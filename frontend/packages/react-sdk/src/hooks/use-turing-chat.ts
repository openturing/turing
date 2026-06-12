import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  deleteAgentFlowState,
  deleteSiteConversationState,
  fetchChatEnabled,
  postAgentChat,
  postChatConversation,
  postSiteFormSubmit,
  type ChatDisabledReason,
} from "../core/api";
import { useOptionalTuringContext } from "../core/use-turing-context";
import {
  TUR_SESSION_DEFAULT_COOKIE_NAME,
  TUR_SESSION_DEFAULT_TTL_SECONDS,
  getOrCreateTurSession,
} from "../core/session";
import type {
  TurChatConversationMessage,
  TurChatForm,
  TurChatFormSubmitResponse,
  TurChatSource,
} from "../core/types";

export type ChatStatus = "idle" | "loading" | "success" | "error";

/** Query-param name carrying the forced A/B variant (T73). */
const AB_VARIANT_PARAM = "_ab_variant";

/**
 * Reads the forced A/B variant label from the current page URL
 * (`?_ab_variant=<label>`). Returns `undefined` when there is no
 * `window.location` (SSR / non-browser) or the param is absent/blank.
 *
 * @since 2026.3.1
 */
function readAbVariantFromLocation(): string | undefined {
  try {
    const search = globalThis.location?.search;
    if (!search) return undefined;
    const value = new URLSearchParams(search).get(AB_VARIANT_PARAM)?.trim();
    return value || undefined;
  } catch {
    return undefined;
  }
}

export interface ChatMessage extends TurChatConversationMessage {
  /** Stable id for React keys (auto-generated) */
  id: string;
  /** Unix ms when the message was created */
  timestamp: number;
  /**
   * Suggested chip labels for the next user turn (only present on assistant
   * messages, only when the chat-flow engine emitted an `"options"` event
   * for the turn). The host renders them as buttons below the bubble;
   * picking one is equivalent to sending that label as the next user
   * message. Empty array (or missing) means free text only.
   *
   * @since 2026.2.17
   */
  options?: string[];
  /**
   * Native multi-field form attached to this assistant message (T107). The
   * host renders it as a form and calls {@link UseTuringChatReturn#submitForm}
   * with the collected values. Cleared once submitted.
   *
   * @since 2026.3.1
   */
  form?: TurChatForm;
  /**
   * RAG provenance behind this assistant answer (T292), emitted by the chat
   * pipeline as a `"sources"` SSE event. The host renders source chips below
   * the bubble; each entry maps to a retrieved chunk.
   *
   * @since 2026.3.1
   */
  sources?: TurChatSource[];
}

/**
 * Switches the hook from site mode (RAG, anonymous, default) to agent mode
 * (explicit AI agent + LLM). Pass when the consumer knows exactly which
 * agent to invoke — typical for authenticated console UIs.
 *
 * @since 2026.2.16
 */
export interface UseTuringChatAgent {
  readonly id: string;
  readonly llmInstanceId: string;
  /**
   * Hint that the host UI may pass a per-turn {@code llmInstanceId}
   * override to {@link UseTuringChatReturn#send}. Purely documentary on
   * the hook — the override is always honoured when supplied; this flag
   * exists so the host's typed surface can advertise the capability to
   * UI authors. Defaults to {@code false}.
   *
   * @since 2026.3.1
   */
  readonly allowOverride?: boolean;
}

/**
 * Per-call overrides accepted by {@link UseTuringChatReturn#send}. Today
 * only the LLM-instance override matters; structuring it as an options bag
 * keeps the door open for future per-turn flags (forced flow, structured
 * tool overrides) without another signature change.
 *
 * @since 2026.3.1
 */
export interface SendOverrides {
  /**
   * Routes this turn through a different {@code TurLLMInstance} than the
   * one pinned via {@code agent.llmInstanceId}. Ignored when the hook is
   * in site mode (no agent endpoint to point at). The hook does not
   * persist the override — the caller controls the "sticky" semantics.
   */
  readonly llmInstanceId?: string;
}

export interface UseTuringChatOptions {
  /**
   * Persist the conversation in `sessionStorage`, scoped by site name.
   * Pass a string to use a custom key suffix.
   * Default: `false` (no persistence).
   */
  readonly persist?: boolean | string;
  /**
   * Maximum number of message turns kept in memory.
   * Default: `50`.
   */
  readonly maxMessages?: number;
  /**
   * Forces a specific chat flow to govern every turn instead of letting the
   * server-side router auto-pick. Most callers should leave this unset.
   *
   * @since 2026.2.16
   */
  readonly flowId?: string;
  /**
   * Forces a specific A/B experiment arm by `variantLabel` for every turn
   * (T73) — bypasses the engine's deterministic hash / bandit / schedule
   * window. Typically used for QA + sales demos. When omitted, the hook falls
   * back to the `?_ab_variant=<label>` URL query param unless
   * {@link UseTuringChatOptions.readAbVariantFromUrl} is `false`.
   *
   * @since 2026.3.1
   */
  readonly forcedVariant?: string;
  /**
   * Pins this conversation to a single skill (by id or name) so the agent runs
   * that one skill as a distinct "mode/flow" (T325), instead of offering the
   * whole enabled skill set for progressive disclosure. Agent mode only; leave
   * unset for the legacy all-skills behaviour.
   *
   * @since 2026.3.1
   */
  readonly selectedSkillId?: string;
  /**
   * Whether to auto-read the forced A/B variant from the `?_ab_variant` URL
   * query param when {@link UseTuringChatOptions.forcedVariant} is not set
   * explicitly. Defaults to `true`.
   *
   * @since 2026.3.1
   */
  readonly readAbVariantFromUrl?: boolean;
  /**
   * Opt into agent mode: the hook hits `POST /v2/ai-agent/{id}/chat` with
   * the explicit `llmInstanceId` instead of the site's RAG endpoint. When
   * unset (default), behavior is the original site-scoped chat.
   *
   * @since 2026.2.16
   */
  readonly agent?: UseTuringChatAgent;
  /**
   * Overrides {@code TuringConfig.locale} for the chat call only. Useful
   * when the active locale is selected dynamically (e.g. a locale picker on
   * the search page) and the parent provider's config can't be threaded
   * with that runtime value.
   *
   * @since 2026.2.16
   */
  readonly locale?: string;
  /**
   * Controlled conversation id. When set, the hook uses this value verbatim
   * as the backend {@code conversationId} for every turn and skips minting
   * one from the {@code TUR_SESSION} cookie. Lets a host that owns the
   * session lifecycle (e.g. the admin console keying chats by IndexedDB
   * session id) keep the backend conversation and its own persisted
   * transcript on the same id. When unset (default), the cookie-minted id
   * is used exactly as before — existing consumers are unaffected.
   *
   * <p>Treated as the source of truth on each {@code send}; pair with a
   * remount (e.g. {@code key={conversationId}}) when switching sessions so
   * {@link initialMessages} re-seeds cleanly.
   *
   * @since 2026.3.1
   */
  readonly conversationId?: string;
  /**
   * Seeds the message list at mount — typically a transcript restored from
   * the host's own store (IndexedDB session, server snapshot). Read once on
   * first render only; change it together with a remount (keyed on
   * {@link conversationId}) to swap transcripts. Ignored when {@code persist}
   * loads a saved conversation from {@code sessionStorage}.
   *
   * @since 2026.3.1
   */
  readonly initialMessages?: ChatMessage[];
}

export interface UseTuringChatReturn {
  /** Whether the site has GenAI/RAG enabled. `null` while unknown. */
  enabled: boolean | null;
  /**
   * Diagnostic code from the `/chat/enabled` probe explaining why
   * {@link enabled} is `false`. `null` while loading or in agent mode.
   * Use {@code CHAT_DISABLED_HINTS} from the SDK root for a
   * human-readable message.
   *
   * @since 2026.2.17
   */
  disabledReason: ChatDisabledReason | null;
  /** Current conversation messages (oldest first) */
  messages: ChatMessage[];
  /** Loading status of the most recent send */
  status: ChatStatus;
  /** Error message from the last failed send */
  error: string | null;
  /** Whether a turn is in progress */
  isStreaming: boolean;
  /**
   * Stable id sent to the backend so the chat-flow router and any future
   * conversation-scoped features can group turns together. `null` until the
   * first `send` call (the id is minted lazily).
   *
   * @since 2026.2.16
   */
  conversationId: string | null;
  /**
   * Send a user message. Appends it to the conversation, calls the API
   * with full history, and appends the assistant reply on success.
   *
   * <p>In agent mode, an optional {@code overrides.llmInstanceId} swaps the
   * LLM for this turn only — the hook does NOT mutate
   * {@code agent.llmInstanceId}, so the host UI decides whether the change
   * is "sticky" (re-render with a new option) or per-turn (one-shot picker).
   *
   * @since 2026.3.1 (overrides param)
   */
  send: (content: string, overrides?: SendOverrides) => Promise<void>;
  /**
   * The native multi-field form awaiting submission (T107), or `null` when
   * none is pending. Mirrors the `form` on the latest assistant message.
   *
   * @since 2026.3.1
   */
  activeForm: TurChatForm | null;
  /**
   * Submit a native multi-field form (T107): writes every value to its slot
   * via `POST /chat/form-submit`, then streams the next assistant turn. Site
   * mode only — resolves `null` in pure agent mode (the endpoint is
   * site-scoped) or when there is no active conversation.
   *
   * @since 2026.3.1
   */
  submitForm: (
    values: Record<string, string>,
    overrides?: SendOverrides,
  ) => Promise<TurChatFormSubmitResponse | null>;
  /** Abort the in-flight assistant turn (rolls back the pending user message). */
  stop: () => void;
  /** Clear all messages and reset error state. */
  reset: () => void;
  /**
   * Drops server-side flow state for the current conversation so the next
   * turn starts at the START node. In site mode it clears every flow on the
   * site's agent for this conversation; in agent mode it requires `flowId`
   * (no-op when omitted, since the agent endpoint targets one specific flow).
   * Returns `false` when nothing was deleted (no active flow, or the call
   * failed); the local message list is untouched.
   *
   * @since 2026.2.16
   */
  resetFlow: () => Promise<boolean>;
}

const DEFAULT_MAX_MESSAGES = 50;

function newId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * Pure update over an immutable message list: locates the message by id and
 * replaces it with the result of {@code mutator}. Returns the same array
 * reference when no match (so React's setState bails on the update).
 *
 * <p>Extracted out of the {@code send} callback so the streaming patch path
 * doesn't nest functions past Sonar's 4-level threshold — inlining
 * {@code findIndex} + the mutator inside a {@code setMessages} closure inside
 * an {@code async} arrow inside {@code useCallback} crosses the line.
 */
/**
 * `fetch` reports an aborted request as a {@link DOMException} with
 * {@code name === "AbortError"}. Some runtimes (older Node test envs) raise
 * a plain {@code Error}, so we also accept that name as a fallback.
 */
function isAbortError(err: unknown): boolean {
  if (err instanceof DOMException && err.name === "AbortError") return true;
  return err instanceof Error && err.name === "AbortError";
}

function patchMessage(
  messages: ChatMessage[],
  id: string,
  mutator: (msg: ChatMessage) => ChatMessage,
): ChatMessage[] {
  const idx = messages.findIndex((m) => m.id === id);
  if (idx === -1) return messages;
  const updated = [...messages];
  updated[idx] = mutator(updated[idx]);
  return updated;
}

function storageKey(site: string, suffix: string | true): string {
  const tag = typeof suffix === "string" ? suffix : "default";
  return `turing.chat.${site}.${tag}`;
}

interface PersistedChat {
  conversationId: string | null;
  messages: ChatMessage[];
}

function isChatMessage(m: unknown): m is ChatMessage {
  return (
    typeof m === "object" &&
    m !== null &&
    typeof (m as ChatMessage).id === "string" &&
    typeof (m as ChatMessage).content === "string" &&
    ((m as ChatMessage).role === "user" || (m as ChatMessage).role === "assistant")
  );
}

// Backwards compat: pre-2026.2.16 we persisted just the messages array.
// Treat that shape as legacy and migrate forward on first read.
function loadPersisted(key: string): PersistedChat {
  try {
    const raw = globalThis.sessionStorage?.getItem(key);
    if (!raw) return { conversationId: null, messages: [] };
    const parsed: unknown = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return { conversationId: null, messages: parsed.filter(isChatMessage) };
    }
    if (typeof parsed === "object" && parsed !== null) {
      const obj = parsed as Partial<PersistedChat>;
      const messages = Array.isArray(obj.messages) ? obj.messages.filter(isChatMessage) : [];
      const conversationId =
        typeof obj.conversationId === "string" && obj.conversationId
          ? obj.conversationId
          : null;
      return { conversationId, messages };
    }
    return { conversationId: null, messages: [] };
  } catch {
    return { conversationId: null, messages: [] };
  }
}

function savePersisted(key: string, value: PersistedChat): void {
  try {
    globalThis.sessionStorage?.setItem(key, JSON.stringify(value));
  } catch {
    // sessionStorage may be unavailable (SSR, private mode, quota)
  }
}

/**
 * Conversational AI hook backed by the site's RAG endpoint.
 * Manages multi-turn chat state, history, abort, and optional persistence.
 *
 * @example
 * ```tsx
 * const { enabled, messages, send, status, reset } = useTuringChat({ persist: true });
 *
 * if (enabled === false) return null;
 *
 * return (
 *   <>
 *     {messages.map((m) => <Bubble key={m.id} role={m.role}>{m.content}</Bubble>)}
 *     <input onSubmit={(text) => send(text)} disabled={status === "loading"} />
 *     <button onClick={reset}>New conversation</button>
 *   </>
 * );
 * ```
 *
 * @since 2026.2.12
 */
export function useTuringChat(
  options: UseTuringChatOptions = {},
): UseTuringChatReturn {
  // Site mode requires the provider for `config.site` (chat-enabled probe,
  // `postChatConversation`, `deleteSiteConversationState`); agent mode reads
  // nothing from it. Mirror the T228a pattern from `useTuringSlots` so an
  // admin console with no real "site" can mount the agent-mode variant
  // without wrapping the world in a placeholder <TuringProvider>.
  const ctx = useOptionalTuringContext();
  const {
    persist = false,
    maxMessages = DEFAULT_MAX_MESSAGES,
    flowId,
    selectedSkillId,
    agent,
    locale: localeOverride,
    conversationId: controlledConversationId,
    initialMessages,
  } = options;
  // T73: forced A/B variant. Explicit option wins; otherwise auto-detect the
  // `?_ab_variant=<label>` URL param unless the caller opted out.
  const optForcedVariant = options.forcedVariant;
  const optReadAbVariantFromUrl = options.readAbVariantFromUrl;
  const forcedVariant = useMemo(
    () =>
      optForcedVariant ??
      (optReadAbVariantFromUrl === false ? undefined : readAbVariantFromLocation()),
    [optForcedVariant, optReadAbVariantFromUrl],
  );
  const agentId = agent?.id;
  const agentLlmInstanceId = agent?.llmInstanceId;
  const isAgentMode = Boolean(agentId && agentLlmInstanceId);
  if (!ctx && !isAgentMode) {
    throw new Error(
      "useTuringChat in site mode requires a <TuringProvider> ancestor. " +
      "Either mount the provider or pass `options.agent` to use agent mode.",
    );
  }
  const siteName = ctx?.config.site ?? "";
  const effectiveLocale = localeOverride ?? ctx?.config.locale;

  const persistKey = persist && siteName ? storageKey(siteName, persist) : null;

  const initialPersisted = persistKey
    ? loadPersisted(persistKey)
    : { conversationId: controlledConversationId ?? null, messages: initialMessages ?? [] };

  // In agent mode the site's RAG flag doesn't gate access — the consumer
  // already chose an agent — so default to enabled and skip the fetch.
  const [enabled, setEnabled] = useState<boolean | null>(isAgentMode ? true : null);
  const [disabledReason, setDisabledReason] = useState<ChatDisabledReason | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>(initialPersisted.messages);
  const [conversationId, setConversationId] = useState<string | null>(initialPersisted.conversationId);
  const [status, setStatus] = useState<ChatStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  // T107 — the native multi-field form awaiting submission, if any.
  const [activeForm, setActiveForm] = useState<TurChatForm | null>(null);
  // Server-driven cookie config (`turing.chat.session.*`). Default values are
  // applied immediately so a `send` before the enabled-fetch resolves still
  // gets a TUR_SESSION cookie.
  const sessionCookieNameRef = useRef<string>(TUR_SESSION_DEFAULT_COOKIE_NAME);
  const sessionTtlSecondsRef = useRef<number>(TUR_SESSION_DEFAULT_TTL_SECONDS);

  const requestRef = useRef(0);
  const abortedRef = useRef(false);
  // Per-send AbortController so `stop()` actually cancels the in-flight
  // fetch (and stops the backend from generating tokens we throw away)
  // instead of just gating state mutations on a counter. Replaced on every
  // `send`; `stop()`/`reset()` call `.abort()` on the latest.
  const abortControllerRef = useRef<AbortController | null>(null);
  // Keep a synchronous mirror of the latest messages so `send` can read the
  // newest history without depending on `messages` (which would re-create the
  // callback on every turn) and without relying on the setState updater fn
  // (which is queued, not synchronous).
  const messagesRef = useRef<ChatMessage[]>(messages);
  // Same reasoning for `conversationId`: minted lazily inside `send`, the ref
  // lets us read the value back synchronously without a re-render.
  const conversationIdRef = useRef<string | null>(conversationId);

  // Resolve enabled flag once per site (skipped in agent mode). Also captures
  // the server-driven session cookie name + TTL so the SDK can mint
  // TUR_SESSION with the deployment's overrides.
  useEffect(() => {
    if (isAgentMode || !siteName) {
      setEnabled(true);
      return;
    }
    let alive = true;
    fetchChatEnabled(siteName)
      .then((res) => {
        if (!alive) return;
        const isEnabled = Boolean(res?.enabled);
        setEnabled(isEnabled);
        setDisabledReason(isEnabled ? null : (res?.reason ?? null));
        if (res?.sessionCookieName) sessionCookieNameRef.current = res.sessionCookieName;
        if (typeof res?.sessionTtlSeconds === "number" && res.sessionTtlSeconds > 0) {
          sessionTtlSecondsRef.current = res.sessionTtlSeconds;
        }
      })
      .catch(() => {
        if (alive) {
          setEnabled(false);
          setDisabledReason(null);
        }
      });
    return () => {
      alive = false;
    };
  }, [siteName, isAgentMode]);

  // Persist on change + keep the synchronous refs in sync
  useEffect(() => {
    messagesRef.current = messages;
    conversationIdRef.current = conversationId;
    if (persistKey) savePersisted(persistKey, { conversationId, messages });
  }, [persistKey, messages, conversationId]);

  // Controlled-id sync: when the host supplies (or later resolves) a
  // `conversationId` prop, adopt it into state + ref so reads like
  // `resetFlow()` see the right id even before the first `send`. No-op for
  // uncontrolled consumers (prop undefined).
  useEffect(() => {
    if (controlledConversationId && controlledConversationId !== conversationIdRef.current) {
      conversationIdRef.current = controlledConversationId;
      setConversationId(controlledConversationId);
    }
  }, [controlledConversationId]);

  // Extracted out of `send`'s try/catch so the streaming path stays under
  // the cognitive-complexity threshold. Drops the result when a newer send
  // is in flight, swallows AbortError as an intentional stop, and surfaces
  // everything else as an error.
  const handleSendError = useCallback((err: unknown, requestId: number) => {
    if (requestId !== requestRef.current || abortedRef.current) return;
    if (isAbortError(err)) {
      setStatus("idle");
      return;
    }
    setError(err instanceof Error ? err.message : "Chat request failed");
    setStatus("error");
  }, []);

  // Shared streaming body for `send` and `submitForm`: opens an empty
  // assistant bubble, streams tokens, then patches in the final text, chip
  // options, and any native form (T107). Assumes `messagesRef.current`
  // already reflects the turn's input history.
  const runAssistantTurn = useCallback(
    async (
      wire: TurChatConversationMessage[],
      requestId: number,
      controller: AbortController,
      activeConversationId: string,
      effectiveLlmInstanceId: string | undefined,
    ) => {
      try {
        const assistantId = newId();
        const assistantMsgInitial: ChatMessage = {
          id: assistantId,
          role: "assistant",
          content: "",
          timestamp: Date.now(),
        };
        const streamingHistory = [...messagesRef.current, assistantMsgInitial];
        const initialWithReply =
          streamingHistory.length > maxMessages
            ? streamingHistory.slice(-maxMessages)
            : streamingHistory;
        messagesRef.current = initialWithReply;
        setMessages(initialWithReply);

        const onToken = (token: string) => {
          if (requestId !== requestRef.current || abortedRef.current) return;
          setMessages((prev) => patchMessage(prev, assistantId,
              (m) => ({ ...m, content: m.content + token })));
        };

        const res = agentId && effectiveLlmInstanceId
          ? await postAgentChat(agentId, effectiveLlmInstanceId, wire, {
              conversationId: activeConversationId,
              flowId,
              forcedVariant,
              selectedSkillId,
              onToken,
              signal: controller.signal,
            })
          : await postChatConversation(siteName, wire, effectiveLocale, {
              conversationId: activeConversationId,
              flowId,
              forcedVariant,
              onToken,
              signal: controller.signal,
            });

        if (requestId !== requestRef.current || abortedRef.current) return;

        const finalOptions = res?.options && res.options.length > 0 ? res.options : undefined;
        const finalForm = res?.form && res.form.fields?.length > 0 ? res.form : undefined;
        const finalSources = res?.sources && res.sources.length > 0 ? res.sources : undefined;
        setMessages((prev) => patchMessage(prev, assistantId, (m) => ({
          ...m,
          content: res?.content ?? m.content,
          options: finalOptions,
          form: finalForm,
          sources: finalSources,
        })));
        setActiveForm(finalForm ?? null);
        setStatus("success");
      } catch (err) {
        handleSendError(err, requestId);
      }
    },
    [siteName, effectiveLocale, maxMessages, flowId, selectedSkillId, forcedVariant, agentId,
      handleSendError],
  );

  const send = useCallback(
    async (rawContent: string, overrides?: SendOverrides) => {
      const content = rawContent.trim();
      if (!content) return;
      // Per-turn LLM override — only meaningful in agent mode where the
      // endpoint accepts an explicit `llmInstanceId`. In site mode the
      // override is silently ignored (the site picks its agent + LLM
      // server-side). The hook never mutates `agentLlmInstanceId`; the
      // caller owns the "sticky" decision.
      const effectiveLlmInstanceId =
        overrides?.llmInstanceId && agentId
          ? overrides.llmInstanceId
          : agentLlmInstanceId;

      const userMsg: ChatMessage = {
        id: newId(),
        role: "user",
        content,
        timestamp: Date.now(),
      };

      const requestId = ++requestRef.current;
      abortedRef.current = false;
      // Drop any prior in-flight request — `stop()`/`reset()` should have
      // already aborted, but a fast caller can chain `send → send` without
      // touching either. Mint a fresh controller per send.
      abortControllerRef.current?.abort();
      const controller = new AbortController();
      abortControllerRef.current = controller;
      setError(null);
      setStatus("loading");

      // Build next history synchronously from the ref (always up-to-date),
      // commit it to state and the ref, then send the same array on the wire.
      const all = [...messagesRef.current, userMsg];
      const nextMessages =
        all.length > maxMessages ? all.slice(-maxMessages) : all;
      messagesRef.current = nextMessages;
      setMessages(nextMessages);

      // The conversation id is the cross-domain TUR_SESSION cookie minted
      // by the SDK — same value across reloads, ties future personalization
      // lookups to the visitor. Falls back to an in-memory id only when the
      // cookie API is unavailable (SSR, cookies disabled). Without an id the
      // backend skips chat-flow routing for turn 1 entirely (the executor
      // early-returns when `conversationId` is blank).
      // A controlled `conversationId` (host owns the session lifecycle) always
      // wins — we never mint a cookie id over it. Otherwise fall back to the
      // cookie-minted id exactly as before.
      let activeConversationId =
        controlledConversationId ??
        getOrCreateTurSession({
          name: sessionCookieNameRef.current,
          ttlSeconds: sessionTtlSecondsRef.current,
        }) ?? conversationIdRef.current ?? newId();
      if (activeConversationId !== conversationIdRef.current) {
        conversationIdRef.current = activeConversationId;
        setConversationId(activeConversationId);
      }

      // A fresh user turn supersedes any pending native form (T107).
      setActiveForm(null);

      const wire: TurChatConversationMessage[] = nextMessages.map((m) => ({
        role: m.role,
        content: m.content,
      }));
      await runAssistantTurn(
        wire,
        requestId,
        controller,
        activeConversationId,
        effectiveLlmInstanceId,
      );
    },
    [maxMessages, agentId, agentLlmInstanceId, controlledConversationId, runAssistantTurn],
  );

  const submitForm = useCallback(
    async (
      values: Record<string, string>,
      overrides?: SendOverrides,
    ): Promise<TurChatFormSubmitResponse | null> => {
      if (!siteName) {
        setError("submitForm requires site mode (the form-submit endpoint is site-scoped)");
        setStatus("error");
        return null;
      }
      const cid = conversationIdRef.current;
      if (!cid) return null;

      const pendingForm = activeForm;
      const requestId = ++requestRef.current;
      abortedRef.current = false;
      abortControllerRef.current?.abort();
      const controller = new AbortController();
      abortControllerRef.current = controller;
      setError(null);
      setStatus("loading");

      // Consume the pending form: clear it from state and from the assistant
      // message that carried it so the inputs disappear immediately.
      setActiveForm(null);
      setMessages((prev) => prev.map((m) => (m.form ? { ...m, form: undefined } : m)));

      let result: TurChatFormSubmitResponse | null = null;
      try {
        result = await postSiteFormSubmit(siteName, cid, values, pendingForm?.nodeId);
      } catch (err) {
        handleSendError(err, requestId);
        return null;
      }
      if (requestId !== requestRef.current || abortedRef.current) return result;

      const effectiveLlmInstanceId =
        overrides?.llmInstanceId && agentId ? overrides.llmInstanceId : agentLlmInstanceId;
      const wire: TurChatConversationMessage[] = messagesRef.current.map((m) => ({
        role: m.role,
        content: m.content,
      }));
      await runAssistantTurn(wire, requestId, controller, cid, effectiveLlmInstanceId);
      return result;
    },
    [siteName, agentId, agentLlmInstanceId, activeForm, handleSendError, runAssistantTurn],
  );

  const stop = useCallback(() => {
    abortedRef.current = true;
    requestRef.current++;
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    setStatus("idle");
  }, []);

  const reset = useCallback(() => {
    abortedRef.current = true;
    requestRef.current++;
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    messagesRef.current = [];
    setMessages([]);
    setError(null);
    setStatus("idle");
    setActiveForm(null);
    // Reset clears local history but never touches TUR_SESSION — the cookie
    // identifies the visitor across reloads, so the conversationId stays the
    // same. Server-side flow state should be cleared via `resetFlow()`.
  }, []);

  // Drop server-side flow state. Site mode clears every flow attached to
  // the conversation under the site's agent (the auto-router may have
  // picked any of them); agent mode targets the specific `flowId` the
  // caller declared. Returns false when there's nothing to clear or the
  // call fails — never throws so a "New chat" button never breaks the UI.
  const resetFlow = useCallback(async (): Promise<boolean> => {
    const cid = conversationIdRef.current;
    if (!cid) return false;
    try {
      if (agentId && agentLlmInstanceId) {
        if (!flowId) return false;
        return await deleteAgentFlowState(agentId, flowId, cid);
      }
      if (!siteName) return false;
      const deleted = await deleteSiteConversationState(siteName, cid);
      return deleted > 0;
    } catch {
      return false;
    }
  }, [agentId, agentLlmInstanceId, flowId, siteName]);

  return {
    enabled,
    disabledReason,
    messages,
    status,
    error,
    isStreaming: status === "loading",
    conversationId,
    send,
    activeForm,
    submitForm,
    stop,
    reset,
    resetFlow,
  };
}
