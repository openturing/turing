import type { TuringClient } from "../client";
import {
  deleteAgentFlowState,
  deleteSiteConversationState,
  fetchChatEnabled,
  postAgentChat,
  postChatConversation,
  type ChatDisabledReason,
} from "../api";
import {
  TUR_SESSION_DEFAULT_COOKIE_NAME,
  TUR_SESSION_DEFAULT_TTL_SECONDS,
  getOrCreateTurSession,
} from "../session";
import { postSiteFormSubmit } from "../api";
import { createStore, type Store } from "../store";
import type {
  TurChatConversationMessage,
  TurChatForm,
  TurChatFormSubmitResponse,
  TurChatSource,
} from "../types";

/**
 * Vanilla chat controller — the framework-agnostic equivalent of the React
 * SDK's `useTuringChat`. Manages multi-turn conversation state, token-by-token
 * streaming, abort, optional `sessionStorage` persistence, and the
 * site-vs-agent endpoint split, all over an observable {@link Store}.
 *
 * <p>Two modes:
 * - <b>site mode</b> (default): hits the site's RAG endpoint; gated by a
 *   `/chat/enabled` probe. Requires {@code options.site}.
 * - <b>agent mode</b>: pass {@code options.agent}; hits
 *   `POST /v2/ai-agent/{id}/chat` with an explicit `llmInstanceId`.
 *
 * @example
 * ```js
 * const chat = createChatController(client, { site: "my-site" });
 * chat.subscribe((s) => render(s.messages));
 * await chat.send("Hello");
 * ```
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

export type ChatStatus = "idle" | "loading" | "success" | "error";

export interface ChatMessage extends TurChatConversationMessage {
  /** Stable id (auto-generated). */
  id: string;
  /** Unix ms when the message was created. */
  timestamp: number;
  /** Suggested chip labels for the next user turn (assistant messages only). */
  options?: string[];
  /**
   * Native multi-field form attached to this assistant message (T107). The UI
   * renders it as a form and calls {@link ChatController.submitForm} with the
   * collected values. Cleared once the form is submitted.
   */
  form?: TurChatForm;
  /**
   * RAG provenance behind this assistant answer (T292). Rendered as source
   * chips below the bubble; each entry maps to a retrieved chunk.
   */
  sources?: TurChatSource[];
}

export interface ChatControllerAgent {
  readonly id: string;
  readonly llmInstanceId: string;
  readonly allowOverride?: boolean;
}

export interface SendOverrides {
  /** Routes this turn through a different LLM instance (agent mode only). */
  readonly llmInstanceId?: string;
}

export interface ChatControllerOptions {
  /** SN site name. Required in site mode; ignored in agent mode. */
  readonly site?: string;
  /** Default locale forwarded to the site RAG endpoint. */
  readonly locale?: string;
  /** Persist conversation in `sessionStorage`. Pass a string for a custom key suffix. */
  readonly persist?: boolean | string;
  /** Max message turns kept in memory. Default 50. */
  readonly maxMessages?: number;
  /** Force a specific chat flow for every turn. */
  readonly flowId?: string;
  /**
   * Force a specific A/B experiment arm by `variantLabel` for every turn
   * (T73) — bypasses the engine's deterministic hash / bandit / schedule
   * window. Useful for QA + sales demos. When omitted, the controller falls
   * back to the `?_ab_variant=<label>` URL query param (set
   * {@link ChatControllerOptions.readAbVariantFromUrl} to `false` to disable
   * that auto-detection).
   */
  readonly forcedVariant?: string;
  /**
   * Whether to auto-read the forced A/B variant from the `?_ab_variant`
   * URL query param when {@link ChatControllerOptions.forcedVariant} is not
   * set explicitly. Defaults to `true`.
   */
  readonly readAbVariantFromUrl?: boolean;
  /** Opt into agent mode. */
  readonly agent?: ChatControllerAgent;
  /** Controlled conversation id (host owns the session lifecycle). */
  readonly conversationId?: string;
  /** Seed the message list at construction. */
  readonly initialMessages?: ChatMessage[];
}

export interface ChatControllerState {
  /** Whether the site has GenAI/RAG enabled. `null` while unknown. */
  enabled: boolean | null;
  /** Diagnostic code from the `/chat/enabled` probe. `null` while loading/agent mode. */
  disabledReason: ChatDisabledReason | null;
  messages: ChatMessage[];
  status: ChatStatus;
  error: string | null;
  isStreaming: boolean;
  conversationId: string | null;
  /**
   * The native multi-field form awaiting submission (T107), or `null` when
   * none is pending. Mirrors the `form` on the most recent assistant message.
   */
  activeForm: TurChatForm | null;
}

export interface ChatController extends Store<ChatControllerState> {
  /** Send a user message (streams the assistant reply). */
  send(content: string, overrides?: SendOverrides): Promise<void>;
  /**
   * Submit a native multi-field form (T107): writes every value to its slot
   * via `POST /chat/form-submit`, then streams the next assistant turn. Site
   * mode only — rejects in pure agent mode (the endpoint is site-scoped).
   */
  submitForm(
    values: Record<string, string>,
    overrides?: SendOverrides,
  ): Promise<TurChatFormSubmitResponse | null>;
  /** Abort the in-flight assistant turn. */
  stop(): void;
  /** Clear all messages and reset error state. */
  reset(): void;
  /** Drop server-side flow state for the current conversation. */
  resetFlow(): Promise<boolean>;
  /** Abort any in-flight request and release resources. */
  destroy(): void;
}

const DEFAULT_MAX_MESSAGES = 50;

function newId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/** Query-param name carrying the forced A/B variant (T73). */
const AB_VARIANT_PARAM = "_ab_variant";

/**
 * Reads the forced A/B variant label from the current page URL
 * (`?_ab_variant=<label>`). Returns `undefined` when there is no
 * `window.location` (SSR / non-browser) or the param is absent/blank.
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

function isAbortError(err: unknown): boolean {
  if (typeof DOMException !== "undefined" && err instanceof DOMException && err.name === "AbortError") {
    return true;
  }
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

export function createChatController(
  client: TuringClient,
  options: ChatControllerOptions = {},
): ChatController {
  const {
    site = "",
    locale,
    persist = false,
    maxMessages = DEFAULT_MAX_MESSAGES,
    flowId,
    agent,
    conversationId: controlledConversationId,
    initialMessages,
  } = options;

  // T73: forced A/B variant. Explicit option wins; otherwise auto-detect the
  // `?_ab_variant=<label>` URL param unless the host opted out. Resolved once
  // at construction so the demo link drives every turn of the session.
  const forcedVariant =
    options.forcedVariant ??
    (options.readAbVariantFromUrl === false ? undefined : readAbVariantFromLocation());

  const agentId = agent?.id;
  const agentLlmInstanceId = agent?.llmInstanceId;
  const isAgentMode = Boolean(agentId && agentLlmInstanceId);

  if (!site && !isAgentMode) {
    throw new Error(
      "createChatController in site mode requires `options.site`. " +
        "Either pass it or pass `options.agent` to use agent mode.",
    );
  }

  const persistKey = persist && site ? storageKey(site, persist) : null;
  const initialPersisted = persistKey
    ? loadPersisted(persistKey)
    : { conversationId: controlledConversationId ?? null, messages: initialMessages ?? [] };

  const store = createStore<ChatControllerState>({
    enabled: isAgentMode ? true : null,
    disabledReason: null,
    messages: initialPersisted.messages,
    status: "idle",
    error: null,
    isStreaming: false,
    conversationId: initialPersisted.conversationId,
    activeForm: null,
  });

  // Server-driven session cookie config (`turing.chat.session.*`).
  let sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME;
  let sessionTtlSeconds = TUR_SESSION_DEFAULT_TTL_SECONDS;

  // Closure mirrors of refs from the React hook.
  let latestRequestId = 0;
  let aborted = false;
  let abortController: AbortController | null = null;
  let messages = initialPersisted.messages;
  let conversationId: string | null = initialPersisted.conversationId;

  function commitMessages(next: ChatMessage[], status?: ChatStatus): void {
    messages = next;
    if (persistKey) savePersisted(persistKey, { conversationId, messages: next });
    store.setState(
      status
        ? { messages: next, status, isStreaming: status === "loading" }
        : { messages: next },
    );
  }

  function setConversationId(id: string | null): void {
    conversationId = id;
    if (persistKey) savePersisted(persistKey, { conversationId: id, messages });
    store.setState({ conversationId: id });
  }

  // Resolve enabled flag once (skipped in agent mode). Fire-and-forget.
  if (isAgentMode || !site) {
    store.setState({ enabled: true });
  } else {
    fetchChatEnabled(client, site)
      .then((res) => {
        const isEnabled = Boolean(res?.enabled);
        if (res?.sessionCookieName) sessionCookieName = res.sessionCookieName;
        if (typeof res?.sessionTtlSeconds === "number" && res.sessionTtlSeconds > 0) {
          sessionTtlSeconds = res.sessionTtlSeconds;
        }
        store.setState({
          enabled: isEnabled,
          disabledReason: isEnabled ? null : (res?.reason ?? null),
        });
      })
      .catch(() => {
        store.setState({ enabled: false, disabledReason: null });
      });
  }

  function handleSendError(err: unknown, requestId: number): void {
    if (requestId !== latestRequestId || aborted) return;
    if (isAbortError(err)) {
      store.setState({ status: "idle", isStreaming: false });
      return;
    }
    store.setState({
      error: err instanceof Error ? err.message : "Chat request failed",
      status: "error",
      isStreaming: false,
    });
  }

  async function send(rawContent: string, overrides?: SendOverrides): Promise<void> {
    const content = rawContent.trim();
    if (!content) return;

    const effectiveLlmInstanceId =
      overrides?.llmInstanceId && agentId ? overrides.llmInstanceId : agentLlmInstanceId;

    const userMsg: ChatMessage = {
      id: newId(),
      role: "user",
      content,
      timestamp: Date.now(),
    };

    const requestId = ++latestRequestId;
    aborted = false;
    abortController?.abort();
    const controller = new AbortController();
    abortController = controller;
    store.setState({ error: null, status: "loading", isStreaming: true });

    const all = [...messages, userMsg];
    const nextMessages = all.length > maxMessages ? all.slice(-maxMessages) : all;
    commitMessages(nextMessages);

    // conversationId is the cross-domain TUR_SESSION cookie (stable across
    // reloads). A controlled id always wins; otherwise mint/read the cookie.
    const activeConversationId =
      controlledConversationId ??
      getOrCreateTurSession({ name: sessionCookieName, ttlSeconds: sessionTtlSeconds }) ??
      conversationId ??
      newId();
    if (activeConversationId !== conversationId) {
      setConversationId(activeConversationId);
    }

    const wire: TurChatConversationMessage[] = nextMessages.map((m) => ({
      role: m.role,
      content: m.content,
    }));
    await streamAssistantTurn(
      wire,
      requestId,
      controller,
      activeConversationId,
      effectiveLlmInstanceId,
    );
  }

  /**
   * Shared streaming body used by {@link send} and {@link submitForm}: opens an
   * empty assistant bubble, streams tokens, and patches in the final text,
   * chip options, and any native form (T107) once the SSE stream completes.
   * Assumes `messages` already reflects the turn's input history.
   */
  async function streamAssistantTurn(
    wire: TurChatConversationMessage[],
    requestId: number,
    controller: AbortController,
    activeConversationId: string,
    effectiveLlmInstanceId: string | undefined,
  ): Promise<void> {
    try {
      // Pre-commit an empty assistant bubble so a UI can render a typing
      // animation immediately, then patch it in place as tokens arrive.
      const assistantId = newId();
      const assistantMsgInitial: ChatMessage = {
        id: assistantId,
        role: "assistant",
        content: "",
        timestamp: Date.now(),
      };
      const streamingHistory = [...messages, assistantMsgInitial];
      const initialWithReply =
        streamingHistory.length > maxMessages
          ? streamingHistory.slice(-maxMessages)
          : streamingHistory;
      commitMessages(initialWithReply);

      const onToken = (token: string) => {
        if (requestId !== latestRequestId || aborted) return;
        commitMessages(
          patchMessage(messages, assistantId, (m) => ({ ...m, content: m.content + token })),
        );
      };

      const res =
        agentId && effectiveLlmInstanceId
          ? await postAgentChat(client, agentId, effectiveLlmInstanceId, wire, {
              conversationId: activeConversationId,
              flowId,
              forcedVariant,
              onToken,
              signal: controller.signal,
            })
          : await postChatConversation(client, site, wire, locale, {
              conversationId: activeConversationId,
              flowId,
              forcedVariant,
              onToken,
              signal: controller.signal,
            });

      if (requestId !== latestRequestId || aborted) return;

      const finalOptions = res?.options && res.options.length > 0 ? res.options : undefined;
      const finalForm = res?.form && res.form.fields?.length > 0 ? res.form : undefined;
      const finalSources = res?.sources && res.sources.length > 0 ? res.sources : undefined;
      commitMessages(
        patchMessage(messages, assistantId, (m) => ({
          ...m,
          content: res?.content ?? m.content,
          options: finalOptions,
          form: finalForm,
          sources: finalSources,
        })),
        "success",
      );
      store.setState({ activeForm: finalForm ?? null });
    } catch (err) {
      handleSendError(err, requestId);
    }
  }

  async function submitForm(
    values: Record<string, string>,
    overrides?: SendOverrides,
  ): Promise<TurChatFormSubmitResponse | null> {
    if (!site) {
      store.setState({
        error: "submitForm requires site mode (the form-submit endpoint is site-scoped)",
        status: "error",
        isStreaming: false,
      });
      return null;
    }
    const cid = conversationId;
    if (!cid) {
      return null;
    }

    const pendingForm = store.getState().activeForm;
    const requestId = ++latestRequestId;
    aborted = false;
    abortController?.abort();
    const controller = new AbortController();
    abortController = controller;
    store.setState({ error: null, status: "loading", isStreaming: true });

    // Consume the pending form: clear it from state and from the assistant
    // message that carried it so the UI stops rendering inputs immediately.
    store.setState({ activeForm: null });
    commitMessages(messages.map((m) => (m.form ? { ...m, form: undefined } : m)));

    let result: TurChatFormSubmitResponse | null = null;
    try {
      result = await postSiteFormSubmit(client, site, cid, values, pendingForm?.nodeId);
    } catch (err) {
      handleSendError(err, requestId);
      return null;
    }
    if (requestId !== latestRequestId || aborted) return result;

    const effectiveLlmInstanceId =
      overrides?.llmInstanceId && agentId ? overrides.llmInstanceId : agentLlmInstanceId;
    const wire: TurChatConversationMessage[] = messages.map((m) => ({
      role: m.role,
      content: m.content,
    }));
    await streamAssistantTurn(wire, requestId, controller, cid, effectiveLlmInstanceId);
    return result;
  }

  function stop(): void {
    aborted = true;
    latestRequestId++;
    abortController?.abort();
    abortController = null;
    store.setState({ status: "idle", isStreaming: false });
  }

  function reset(): void {
    aborted = true;
    latestRequestId++;
    abortController?.abort();
    abortController = null;
    commitMessages([]);
    store.setState({ error: null, status: "idle", isStreaming: false, activeForm: null });
  }

  async function resetFlow(): Promise<boolean> {
    const cid = conversationId;
    if (!cid) return false;
    try {
      if (agentId && agentLlmInstanceId) {
        if (!flowId) return false;
        return await deleteAgentFlowState(client, agentId, flowId, cid);
      }
      if (!site) return false;
      const deleted = await deleteSiteConversationState(client, site, cid);
      return deleted > 0;
    } catch {
      return false;
    }
  }

  function destroy(): void {
    aborted = true;
    latestRequestId++;
    abortController?.abort();
    abortController = null;
  }

  return {
    getState: store.getState,
    setState: store.setState,
    subscribe: store.subscribe,
    send,
    submitForm,
    stop,
    reset,
    resetFlow,
    destroy,
  };
}
