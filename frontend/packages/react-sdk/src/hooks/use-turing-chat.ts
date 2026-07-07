import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  deleteAgentFlowState,
  deleteSiteConversationState,
  fetchChatEnabled,
  postAgentChat,
  postPersonaChat,
  postChatConversation,
  postClientToolResult,
  postSiteFormSubmit,
  type ChatDisabledReason,
} from "../core/api";
import { useOptionalTuringContext } from "../core/use-turing-context";
import {
  TUR_SESSION_DEFAULT_COOKIE_NAME,
  TUR_SESSION_DEFAULT_TTL_SECONDS,
  getOrCreateTurSession,
} from "../core/session";
import { TURING_ANALYTICS_EVENTS, type TuringAnalytics } from "../core/analytics";
import {
  createAbandonmentWatcher,
  type AbandonmentOptions,
  type AbandonmentWatcher,
} from "../core/analytics-lifecycle";
import type {
  ClientToolHandler,
  ClientToolRegistration,
  TurChatConversationMessage,
  TurChatConversationResponse,
  TurChatForm,
  TurChatFormSubmitResponse,
  TurChatToolCall,
  TurClientToolCall,
} from "../core/types";
import {
  newId,
  useStreamingChatCore,
  type ChatMessage,
  type ChatStatus,
} from "./streaming-chat-core";

// Re-exported for backwards compatibility — `ChatMessage` and `ChatStatus`
// moved to `streaming-chat-core.ts` in T243 (shared with `useTuringLlmChat`)
// but were, and stay, part of this hook's public surface.
export type { ChatMessage, ChatStatus } from "./streaming-chat-core";

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
 * Block AI / §XXXII.2 (T579) — switches the hook into persona mode: talk
 * directly to a {@link TurPersona} (`POST /v2/persona/{id}/chat`) instead of an
 * agent or a site. A peer of {@link UseTuringChatAgent}; pass exactly one of
 * `agent` / `persona`. Persona turns are stateless (no flow / skill / A-B knobs).
 *
 * @since 2026.3.4
 */
export interface UseTuringChatPersona {
  readonly id: string;
  readonly llmInstanceId: string;
  /**
   * Advertises that the host may pass a per-turn {@code llmInstanceId} override
   * to {@link UseTuringChatReturn#send} (personas have no per-entity LLM
   * allow-list, so any enabled instance is valid). Documentary only — the
   * override is always honoured when supplied. Defaults to {@code false}.
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
   * Block AI / §XXXII.2 (T579) — opt into persona mode: the hook hits
   * `POST /v2/persona/{id}/chat` with the explicit `llmInstanceId` instead of
   * the agent or site endpoint. Mutually exclusive with {@link agent}; when both
   * are set, `persona` wins. Persona turns are stateless (flow / skill / A-B
   * options are ignored).
   *
   * @since 2026.3.4
   */
  readonly persona?: UseTuringChatPersona;
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
  /**
   * T439 — frontend ("client") tool handlers, keyed by tool name, as either a
   * bare handler or `{ schema?, handler }`. When the agent (which must declare
   * the tool, `clientToolsEnabled`) calls one, the hook runs the handler and
   * POSTs its result so the turn continues. Mirrors `useCopilotAction`. Read on
   * every send (live), so a changing object is honoured without a remount.
   *
   * @since 2026.3.4
   */
  readonly clientTools?: Record<string, ClientToolRegistration>;
  /**
   * T463 (Block Z) — canonical analytics bus (from {@link useTuringAnalytics}).
   * When set, the hook emits `turing_chat_start` (first user message),
   * `turing_chat_message_sent` (every send), and — unless disabled via
   * {@link abandonment} — `turing_chat_abandoned` on tab hide / unload / idle.
   * A native form submit emits `turing_chat_lead_captured`. No-op when absent.
   *
   * @since 2026.3.6
   */
  readonly analytics?: TuringAnalytics;
  /**
   * T463 — tune (or disable, with `false`) the abandonment watcher. Defaults on
   * when {@link analytics} is set. See {@link AbandonmentOptions}.
   *
   * @since 2026.3.6
   */
  readonly abandonment?: AbandonmentOptions | boolean;
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
  /**
   * T439 — register a frontend ("client") tool handler the agent can invoke.
   * Replaces any prior handler for {@code name}; returns an unregister function.
   * Equivalent to an entry in the {@code clientTools} option.
   *
   * @since 2026.3.4
   */
  registerClientTool: (name: string, handler: ClientToolHandler) => () => void;
  /** Remove a previously-registered client-tool handler. */
  unregisterClientTool: (name: string) => void;
}

const DEFAULT_MAX_MESSAGES = 50;

/** Cap on chained client-tool round-trips in a single turn (runaway guard). */
const MAX_CLIENT_TOOL_HOPS = 10;

function asClientToolHandler(registration: ClientToolRegistration): ClientToolHandler {
  return typeof registration === "function" ? registration : registration.handler;
}

/**
 * Runs the registered handler for a {@link TurClientToolCall} and normalizes the
 * outcome to `{result}` or `{error}` — a missing/throwing handler is reported to
 * the agent as a tool error rather than surfacing in the UI (T439).
 */
async function runClientToolHandler(
  handlers: Map<string, ClientToolHandler>,
  call: TurClientToolCall,
): Promise<{ result?: unknown; error?: string }> {
  const handler = handlers.get(call.name);
  if (!handler) {
    return { error: `No client tool handler registered for '${call.name}'` };
  }
  let parsedArgs: unknown;
  try {
    parsedArgs = call.args ? JSON.parse(call.args) : {};
  } catch {
    parsedArgs = call.args;
  }
  try {
    return { result: await handler(parsedArgs) };
  } catch (err) {
    return { error: err instanceof Error ? err.message : String(err) };
  }
}

/** Merges two tool-call lists by `callId` (later entries win). */
function mergeToolCallLists(a: TurChatToolCall[], b: TurChatToolCall[]): TurChatToolCall[] {
  const byId = new Map<string, TurChatToolCall>();
  for (const c of a) byId.set(c.callId, c);
  for (const c of b) byId.set(c.callId, c);
  return [...byId.values()];
}

interface ClientToolRoundTripCtx {
  handlers: Map<string, ClientToolHandler>;
  conversationId: string;
  signal: AbortSignal;
  onToken: (token: string) => void;
  onToolCall: (call: TurChatToolCall, all: TurChatToolCall[]) => void;
  shouldStop: () => boolean;
}

/**
 * T438 / T439 — drives the client-tool round-trips for a parked turn: runs the
 * registered handler, POSTs its result, and consumes the continuation, looping
 * until the agent answers (no `clientToolCall`), the hop cap trips, or the turn
 * goes stale. Returns the final response plus the content + tool calls
 * accumulated across all legs (extracted to keep `runAssistantTurn` simple).
 */
async function runClientToolRoundTrips(
  initial: TurChatConversationResponse,
  ctx: ClientToolRoundTripCtx,
): Promise<{ res: TurChatConversationResponse; content: string; toolCalls: TurChatToolCall[] }> {
  let res = initial;
  let content = res?.content ?? "";
  let toolCalls: TurChatToolCall[] = res?.toolCalls ?? [];
  let hops = 0;
  while (res?.clientToolCall && hops < MAX_CLIENT_TOOL_HOPS && !ctx.shouldStop()) {
    hops += 1;
    const call = res.clientToolCall;
    const { result, error } = await runClientToolHandler(ctx.handlers, call);
    res = await postClientToolResult(
      { conversationId: ctx.conversationId, callId: call.callId, result, error },
      { onToken: ctx.onToken, onToolCall: ctx.onToolCall, signal: ctx.signal },
    );
    content += res?.content ?? "";
    if (res?.toolCalls?.length) {
      toolCalls = mergeToolCallLists(toolCalls, res.toolCalls);
    }
  }
  return { res, content, toolCalls };
}

interface SubjectTurnCtx {
  readonly personaId?: string;
  readonly agentId?: string;
  readonly llmInstanceId?: string;
  readonly siteName: string;
  readonly effectiveLocale?: string;
  readonly conversationId: string;
  readonly flowId?: string;
  readonly forcedVariant?: string;
  readonly selectedSkillId?: string;
  readonly wire: TurChatConversationMessage[];
  readonly onToken: (token: string) => void;
  readonly onToolCall: (call: TurChatToolCall, all: TurChatToolCall[]) => void;
  readonly signal: AbortSignal;
}

/**
 * Picks the transport for one turn from the resolved chat subject: persona
 * (T579, stateless — no flow/skill/variant), else agent, else the site RAG
 * endpoint. Extracted so {@link useTuringChat}'s `runAssistantTurn` stays a
 * single straight-line flow regardless of subject.
 */
function dispatchSubjectTurn(c: SubjectTurnCtx): Promise<TurChatConversationResponse> {
  if (c.personaId && c.llmInstanceId) {
    return postPersonaChat(c.personaId, c.llmInstanceId, c.wire, {
      onToken: c.onToken,
      onToolCall: c.onToolCall,
      signal: c.signal,
    });
  }
  if (c.agentId && c.llmInstanceId) {
    return postAgentChat(c.agentId, c.llmInstanceId, c.wire, {
      conversationId: c.conversationId,
      flowId: c.flowId,
      forcedVariant: c.forcedVariant,
      selectedSkillId: c.selectedSkillId,
      onToken: c.onToken,
      onToolCall: c.onToolCall,
      signal: c.signal,
    });
  }
  return postChatConversation(c.siteName, c.wire, c.effectiveLocale, {
    conversationId: c.conversationId,
    flowId: c.flowId,
    forcedVariant: c.forcedVariant,
    onToken: c.onToken,
    onToolCall: c.onToolCall,
    signal: c.signal,
  });
}

/**
 * Normalizes a finished turn's response + the client-tool-aggregated content /
 * tool calls into the "empty → undefined" shape the assistant bubble patch
 * expects. Extracted so {@link useTuringChat}'s `runAssistantTurn` stays under
 * the cognitive-complexity budget.
 */
function buildAssistantFinals(
  res: TurChatConversationResponse,
  aggregatedContent: string,
  aggregatedToolCalls: TurChatToolCall[],
) {
  const searchSuggestions =
    res?.searchSuggestions &&
    (res.searchSuggestions.renderedContent ||
      (res.searchSuggestions.queries?.length ?? 0) > 0)
      ? res.searchSuggestions
      : undefined;
  return {
    content: aggregatedContent,
    options: res?.options && res.options.length > 0 ? res.options : undefined,
    form: res?.form && res.form.fields?.length > 0 ? res.form : undefined,
    sources: res?.sources && res.sources.length > 0 ? res.sources : undefined,
    citations: res?.citations && res.citations.length > 0 ? res.citations : undefined,
    // T490 — Gemini google_search Search Suggestion chips, when present.
    searchSuggestions,
    // T178 — the OpenAI reasoning summary ("Why this answer"), when present.
    reasoning: res?.reasoning && res.reasoning.trim().length > 0 ? res.reasoning : undefined,
    toolCalls: aggregatedToolCalls.length > 0 ? aggregatedToolCalls : undefined,
    // T516 grounding verdict / T522 second-opinion verdict (undefined when clean).
    grounding: res?.grounding ?? undefined,
    secondOpinion: res?.secondOpinion ?? undefined,
  };
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
    persona,
    locale: localeOverride,
    conversationId: controlledConversationId,
    initialMessages,
    analytics,
    abandonment,
  } = options;

  // T463 — analytics lifecycle bookkeeping, ref-based so the stable `send`
  // callback never rebuilds. `chatStarted` gates the once-per-conversation
  // start event; `userTurns` rides on every event; the watcher + guards drive
  // abandonment (T460).
  const chatStartedRef = useRef(false);
  const userTurnsRef = useRef(0);
  const convertedRef = useRef(false);
  const abandonmentFiredRef = useRef(false);
  const convStartedAtRef = useRef(0);
  const watcherRef = useRef<AbandonmentWatcher | null>(null);
  const analyticsRef = useRef<TuringAnalytics | undefined>(analytics);
  analyticsRef.current = analytics;
  const abandonmentEnabledRef = useRef(false);
  abandonmentEnabledRef.current = Boolean(analytics) && abandonment !== false;
  const abandonmentOptionsRef = useRef<AbandonmentOptions>({});
  abandonmentOptionsRef.current = typeof abandonment === "object" ? abandonment : {};

  const fireAbandonment = useCallback((reason: string) => {
    const bus = analyticsRef.current;
    if (!bus || abandonmentFiredRef.current || convertedRef.current || userTurnsRef.current < 1) {
      return;
    }
    abandonmentFiredRef.current = true;
    bus.emit(TURING_ANALYTICS_EVENTS.chatAbandoned, {
      reason,
      turns: userTurnsRef.current,
      last_step: userTurnsRef.current,
      elapsed_ms: convStartedAtRef.current ? Date.now() - convStartedAtRef.current : undefined,
    });
  }, []);

  // Release the watcher on unmount.
  useEffect(() => () => watcherRef.current?.stop(), []);

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
  // Persona mode wins over agent mode when both subjects are (mis)configured.
  const personaId = persona?.id;
  const personaLlmInstanceId = persona?.llmInstanceId;
  const isPersonaMode = Boolean(personaId && personaLlmInstanceId);
  const agentId = isPersonaMode ? undefined : agent?.id;
  const agentLlmInstanceId = isPersonaMode ? undefined : agent?.llmInstanceId;
  const isAgentMode = Boolean(agentId && agentLlmInstanceId);
  // Both persona and agent mode are self-contained subjects (explicit LLM), so
  // neither needs the site provider or the RAG-enabled probe.
  const isSubjectMode = isAgentMode || isPersonaMode;
  if (!ctx && !isSubjectMode) {
    throw new Error(
      "useTuringChat in site mode requires a <TuringProvider> ancestor. " +
      "Either mount the provider or pass `options.agent` / `options.persona`.",
    );
  }
  const siteName = ctx?.config.site ?? "";
  const effectiveLocale = localeOverride ?? ctx?.config.locale;

  const persistKey = persist && siteName ? storageKey(siteName, persist) : null;

  const initialPersisted = persistKey
    ? loadPersisted(persistKey)
    : { conversationId: controlledConversationId ?? null, messages: initialMessages ?? [] };

  // Shared streaming/bubble/abort state machine (T243) — owns `messages`,
  // `status`, `error`, the request counter, the aborted flag, the per-send
  // AbortController, and the synchronous `messagesRef`.
  const core = useStreamingChatCore({
    maxMessages,
    initialMessages: initialPersisted.messages,
  });
  const {
    messages,
    status,
    error,
    setMessages,
    setStatus,
    setError,
    messagesRef,
    isStale,
    handleSendError,
    beginSend,
    commitMessages,
    openAssistantBubble,
    appendToken,
    patchAssistant,
  } = core;

  // In agent mode the site's RAG flag doesn't gate access — the consumer
  // already chose an agent — so default to enabled and skip the fetch.
  const [enabled, setEnabled] = useState<boolean | null>(isSubjectMode ? true : null);
  const [disabledReason, setDisabledReason] = useState<ChatDisabledReason | null>(null);
  const [conversationId, setConversationId] = useState<string | null>(initialPersisted.conversationId);
  // T107 — the native multi-field form awaiting submission, if any.
  const [activeForm, setActiveForm] = useState<TurChatForm | null>(null);
  // Server-driven cookie config (`turing.chat.session.*`). Default values are
  // applied immediately so a `send` before the enabled-fetch resolves still
  // gets a TUR_SESSION cookie.
  const sessionCookieNameRef = useRef<string>(TUR_SESSION_DEFAULT_COOKIE_NAME);
  const sessionTtlSecondsRef = useRef<number>(TUR_SESSION_DEFAULT_TTL_SECONDS);

  // Same reasoning as the core's `messagesRef`, for `conversationId`: minted
  // lazily inside `send`, the ref lets us read the value back synchronously
  // without a re-render.
  const conversationIdRef = useRef<string | null>(conversationId);

  // T439 — imperatively-registered client-tool handlers. Option-provided
  // handlers (`options.clientTools`) are merged on top of these at send time so
  // a changing option object is honoured live without a remount.
  const clientToolHandlersRef = useRef<Map<string, ClientToolHandler>>(new Map());
  const optionClientTools = options.clientTools;
  const resolveClientToolHandlers = useCallback((): Map<string, ClientToolHandler> => {
    const merged = new Map(clientToolHandlersRef.current);
    if (optionClientTools) {
      for (const [name, registration] of Object.entries(optionClientTools)) {
        merged.set(name, asClientToolHandler(registration));
      }
    }
    return merged;
  }, [optionClientTools]);

  // Resolve enabled flag once per site (skipped in agent mode). Also captures
  // the server-driven session cookie name + TTL so the SDK can mint
  // TUR_SESSION with the deployment's overrides.
  useEffect(() => {
    if (isSubjectMode || !siteName) {
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
  }, [siteName, isSubjectMode]);

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

  // Shared streaming body for `send` and `submitForm`: opens an empty
  // assistant bubble, streams tokens, then patches in the final text, chip
  // options, and any native form (T107). Assumes `messagesRef.current`
  // already reflects the turn's input history. The bubble/token/abort
  // mechanics live in `useStreamingChatCore` (T243); this only adds the
  // site/agent transport split and the form/options/sources final patch.
  const runAssistantTurn = useCallback(
    async (
      wire: TurChatConversationMessage[],
      requestId: number,
      controller: AbortController,
      activeConversationId: string,
      effectiveLlmInstanceId: string | undefined,
    ) => {
      try {
        const assistantId = openAssistantBubble();
        const onToken = (token: string) => appendToken(assistantId, requestId, token);
        // T436 — patch live tool-call activity onto the in-flight bubble as
        // `tool_call` events arrive (`all` is merged by callId), so the host
        // can render running tool activity before the answer text streams in.
        const onToolCall = (_call: TurChatToolCall, all: TurChatToolCall[]) => {
          if (isStale(requestId)) return;
          patchAssistant(assistantId, (m) => ({ ...m, toolCalls: all }));
        };

        let res = await dispatchSubjectTurn({
          personaId,
          agentId,
          llmInstanceId: effectiveLlmInstanceId,
          siteName,
          effectiveLocale,
          conversationId: activeConversationId,
          flowId,
          forcedVariant,
          selectedSkillId,
          wire,
          onToken,
          onToolCall,
          signal: controller.signal,
        });

        if (isStale(requestId)) return;

        // T438 / T439 — resolve any client-tool round-trips before finalizing:
        // run the registered handler, POST the result, and consume the
        // continuation into the same bubble (tokens append live), accumulating
        // content + tool calls across legs.
        const roundTrip = await runClientToolRoundTrips(res, {
          handlers: resolveClientToolHandlers(),
          conversationId: activeConversationId,
          signal: controller.signal,
          onToken,
          onToolCall,
          shouldStop: () => isStale(requestId),
        });
        if (isStale(requestId)) return;
        res = roundTrip.res;
        const aggregatedContent = roundTrip.content;
        const aggregatedToolCalls = roundTrip.toolCalls;

        const finals = buildAssistantFinals(res, aggregatedContent, aggregatedToolCalls);
        patchAssistant(assistantId, (m) => ({
          ...m,
          content: finals.content || m.content,
          options: finals.options,
          form: finals.form,
          sources: finals.sources,
          citations: finals.citations,
          searchSuggestions: finals.searchSuggestions,
          reasoning: finals.reasoning,
          toolCalls: finals.toolCalls ?? m.toolCalls,
          grounding: finals.grounding,
          secondOpinion: finals.secondOpinion,
        }));
        setActiveForm(finals.form ?? null);
        setStatus("success");
      } catch (err) {
        handleSendError(err, requestId);
      }
    },
    [siteName, effectiveLocale, flowId, selectedSkillId, forcedVariant, agentId, personaId,
      openAssistantBubble, appendToken, patchAssistant, isStale, handleSendError, setStatus,
      resolveClientToolHandlers],
  );

  const send = useCallback(
    async (rawContent: string, overrides?: SendOverrides) => {
      const content = rawContent.trim();
      if (!content) return;
      // Per-turn LLM override — only meaningful in a subject mode (agent /
      // persona) where the endpoint accepts an explicit `llmInstanceId`. In
      // site mode the override is silently ignored (the site picks its agent +
      // LLM server-side). The hook never mutates the pinned instance; the
      // caller owns the "sticky" decision.
      const baseLlmInstanceId = personaLlmInstanceId ?? agentLlmInstanceId;
      const effectiveLlmInstanceId =
        overrides?.llmInstanceId && (agentId || personaId)
          ? overrides.llmInstanceId
          : baseLlmInstanceId;

      const userMsg: ChatMessage = {
        id: newId(),
        role: "user",
        content,
        timestamp: Date.now(),
      };

      const { requestId, controller } = beginSend();

      // Build next history synchronously from the ref (always up-to-date),
      // commit it to state and the ref, then send the same array on the wire.
      const nextMessages = commitMessages([...messagesRef.current, userMsg]);

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

      // T463 — emit chat lifecycle events + keep the bus context in sync.
      const bus = analyticsRef.current;
      if (bus) {
        bus.setContext({ conversationId: activeConversationId, sessionId: activeConversationId });
        userTurnsRef.current += 1;
        if (!chatStartedRef.current) {
          chatStartedRef.current = true;
          convStartedAtRef.current = Date.now();
          bus.emit(TURING_ANALYTICS_EVENTS.chatStart, {
            mode: personaId ? "persona" : agentId ? "agent" : "site",
          });
          if (abandonmentEnabledRef.current && !watcherRef.current) {
            watcherRef.current = createAbandonmentWatcher(fireAbandonment, abandonmentOptionsRef.current);
          }
        }
        bus.emit(TURING_ANALYTICS_EVENTS.chatMessageSent, {
          turn: userTurnsRef.current,
          length: content.length,
        });
        watcherRef.current?.ping();
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
    [agentId, agentLlmInstanceId, personaId, personaLlmInstanceId, controlledConversationId,
      beginSend, commitMessages, messagesRef, runAssistantTurn, fireAbandonment],
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
      const { requestId, controller } = beginSend();

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
      if (isStale(requestId)) return result;

      // T463 — a native form capture (T107) is a conversion. Emit once and mark
      // converted so abandonment never fires afterwards.
      const bus = analyticsRef.current;
      if (bus && !convertedRef.current) {
        convertedRef.current = true;
        bus.emit(TURING_ANALYTICS_EVENTS.chatLeadCaptured, {
          reason: "form",
          turns: userTurnsRef.current,
        });
      }

      const effectiveLlmInstanceId =
        overrides?.llmInstanceId && agentId ? overrides.llmInstanceId : agentLlmInstanceId;
      const wire: TurChatConversationMessage[] = messagesRef.current.map((m) => ({
        role: m.role,
        content: m.content,
      }));
      await runAssistantTurn(wire, requestId, controller, cid, effectiveLlmInstanceId);
      return result;
    },
    [siteName, agentId, agentLlmInstanceId, activeForm, beginSend, setMessages, isStale,
      handleSendError, runAssistantTurn],
  );

  const stop = core.stop;

  // Reset clears local history but never touches TUR_SESSION — the cookie
  // identifies the visitor across reloads, so the conversationId stays the
  // same. Server-side flow state should be cleared via `resetFlow()`. The
  // extra cleanup drops any pending native form (T107).
  const reset = useCallback(() => {
    core.reset(() => setActiveForm(null));
    // T463 — a reset starts a fresh conversation: re-arm the lifecycle so the
    // start/abandonment events track the new session from scratch.
    watcherRef.current?.stop();
    watcherRef.current = null;
    chatStartedRef.current = false;
    userTurnsRef.current = 0;
    convertedRef.current = false;
    abandonmentFiredRef.current = false;
    convStartedAtRef.current = 0;
  }, [core]);

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

  // T439 — imperative client-tool registration (stable identities; mutate the ref).
  const registerClientTool = useCallback(
    (name: string, handler: ClientToolHandler): (() => void) => {
      clientToolHandlersRef.current.set(name, handler);
      return () => clientToolHandlersRef.current.delete(name);
    },
    [],
  );
  const unregisterClientTool = useCallback((name: string) => {
    clientToolHandlersRef.current.delete(name);
  }, []);

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
    registerClientTool,
    unregisterClientTool,
  };
}
