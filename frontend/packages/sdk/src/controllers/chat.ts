import type { TuringClient } from "../client";
import {
  deleteAgentFlowState,
  deleteSiteConversationState,
  fetchChatEnabled,
  fetchSiteChatSlots,
  fetchSiteChatState,
  postAgentChat,
  postPersonaChat,
  postChatConversation,
  postClientToolResult,
  type ChatDisabledReason,
} from "../api";
import {
  TUR_SESSION_DEFAULT_COOKIE_NAME,
  TUR_SESSION_DEFAULT_TTL_SECONDS,
  getOrCreateTurSession,
} from "../session";
import { postSiteFormSubmit } from "../api";
import { TURING_ANALYTICS_EVENTS, type TuringAnalytics } from "../analytics";
import {
  createAbandonmentWatcher,
  type AbandonmentOptions,
  type AbandonmentWatcher,
} from "../analytics-lifecycle";
import { createStore, type Store } from "../store";
import type {
  TurChatCitation,
  TurChatConversationMessage,
  TurChatForm,
  TurChatFormSubmitResponse,
  TurChatGrounding,
  TurChatSecondOpinion,
  TurChatSource,
  TurChatToolCall,
  TurClientToolCall,
} from "../types";

/**
 * T439 — a frontend ("client") tool handler: runs in the browser when the agent
 * calls the tool, receiving the parsed arguments and returning the result fed
 * back to the agent. May be async; a thrown error is reported to the agent as a
 * tool error (it decides whether to retry/abandon) rather than surfacing in the UI.
 */
export type ClientToolHandler = (args: unknown) => unknown | Promise<unknown>;

/** Client-tool registration accepting either a bare handler or a `{schema, handler}` object. */
export type ClientToolRegistration =
  | ClientToolHandler
  | { handler: ClientToolHandler; schema?: unknown };

function asClientToolHandler(registration: ClientToolRegistration): ClientToolHandler {
  return typeof registration === "function" ? registration : registration.handler;
}

/** Cap on chained client-tool round-trips in a single turn (runaway guard). */
const MAX_CLIENT_TOOL_HOPS = 10;

/**
 * Runs the registered handler for a {@link TurClientToolCall} and normalizes the
 * outcome to a `{result}` or `{error}`. A missing handler or a thrown handler is
 * reported as an `error` so the agent (not the UI) decides how to recover.
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
    parsedArgs = call.args; // hand the raw string through when args isn't JSON
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
  /**
   * Per-sentence Anthropic citations behind this assistant answer (T152).
   * The citation-aware chat UI (T154) underlines the cited span and links to
   * the source.
   */
  citations?: TurChatCitation[];
  /**
   * Live tool-call activity behind this assistant turn (T436), merged by
   * `callId`. Populated incrementally as `tool_call` events stream in (so a UI
   * can show "calling search_knowledge_base…" while the answer is prepared) and
   * finalized when the turn completes. Present only when the agent has
   * `toolCallEventsEnabled`.
   */
  toolCalls?: TurChatToolCall[];
  /**
   * T516 / §XXVIII.12 — the answer-grounding guardrail verdict for this turn,
   * present only when the guardrail is enabled and flagged the answer. The UI
   * renders a confidence badge beside the bubble.
   */
  grounding?: TurChatGrounding;
  /**
   * T522 / §XXVIII.18 — the cross-vendor "second opinion" verdict for this turn,
   * present only when the check is enabled and produced a signal.
   */
  secondOpinion?: TurChatSecondOpinion;
}

export interface ChatControllerAgent {
  readonly id: string;
  readonly llmInstanceId: string;
  readonly allowOverride?: boolean;
}

/**
 * Block AI / §XXXII.2 (T579) — persona subject: talk directly to a persona
 * (`POST /v2/persona/{id}/chat`). A peer of {@link ChatControllerAgent}; pass
 * exactly one of `agent` / `persona`. Persona turns are stateless.
 */
export interface ChatControllerPersona {
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
   * T635 / §XXVII.4 — site-mode RAG persona ("same question, different eyes").
   * Forwarded as `personaId` on every `/chat/conversation` turn and validated
   * against the site agent's catalog server-side (unknown → default). Distinct
   * from {@link persona} (the stateless persona-mode transport, T579). Ignored
   * in agent/persona mode.
   */
  readonly personaId?: string;
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
  /**
   * Block AI / §XXXII.2 (T579) — opt into persona mode (`POST /v2/persona/{id}/chat`).
   * Mutually exclusive with {@link agent}; when both are set, `persona` wins.
   * Persona turns are stateless (flow / variant options are ignored).
   */
  readonly persona?: ChatControllerPersona;
  /** Controlled conversation id (host owns the session lifecycle). */
  readonly conversationId?: string;
  /** Seed the message list at construction. */
  readonly initialMessages?: ChatMessage[];
  /**
   * T439 — frontend ("client") tool handlers, keyed by tool name. When the agent
   * (which must declare the tool, `clientToolsEnabled`) calls one, the controller
   * runs the handler and POSTs its result so the turn continues. Equivalent to
   * calling {@link ChatController.registerClientTool} for each entry; mirrors
   * CopilotKit's `useCopilotAction` ergonomics.
   */
  readonly clientTools?: Record<string, ClientToolRegistration>;
  /**
   * T458 (Block Z) — canonical analytics bus. When provided, the controller
   * emits `turing_chat_start` (first user message), `turing_chat_message_sent`
   * (every send, with the running turn count) and keeps the bus's context
   * envelope (`conversationId`/`sessionId`) in sync. No-op when absent, so
   * existing embeds are unchanged. Create it with `createTuringAnalytics()` and
   * attach a sink (e.g. `googleAnalyticsSink()`).
   */
  readonly analytics?: TuringAnalytics;
  /**
   * T460 (Block Z) — client-side abandonment detection. When `analytics` is set
   * this defaults **on**: a started conversation (≥1 user message) that ends
   * without a conversion emits a single `turing_chat_abandoned` on tab hide,
   * page unload, or idle timeout — the drop-off signal the server can't see.
   * Pass `false` to disable, or an object to tune `idleMs` / `onHidden` /
   * `onUnload`. No effect without `analytics` or outside a browser.
   */
  readonly abandonment?: AbandonmentOptions | boolean;
  /**
   * T461 (Block Z) — slot names that count as a conversion (e.g. `["email",
   * "phone"]`). When any is written during the conversation, the controller
   * emits a single `turing_chat_lead_captured`. A native form submit (T107) is
   * always treated as a lead when this list is empty, or when it writes one of
   * these slots. No effect without `analytics`.
   */
  readonly goalSlots?: ReadonlyArray<string>;
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
  /**
   * T439 — register a frontend ("client") tool handler the agent can invoke.
   * Replaces any prior handler for {@code name}. Returns an unregister function.
   */
  registerClientTool(name: string, handler: ClientToolHandler): () => void;
  /** Remove a previously-registered client-tool handler. */
  unregisterClientTool(name: string): void;
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
    personaId: ragPersonaId,
    agent,
    persona,
    conversationId: controlledConversationId,
    initialMessages,
    clientTools,
    analytics,
    abandonment,
    goalSlots = [],
  } = options;

  // T439 — registered client-tool handlers, seeded from `options.clientTools`
  // and mutable via registerClientTool/unregisterClientTool.
  const clientToolHandlers = new Map<string, ClientToolHandler>();
  if (clientTools) {
    for (const [name, registration] of Object.entries(clientTools)) {
      clientToolHandlers.set(name, asClientToolHandler(registration));
    }
  }

  // T73: forced A/B variant. Explicit option wins; otherwise auto-detect the
  // `?_ab_variant=<label>` URL param unless the host opted out. Resolved once
  // at construction so the demo link drives every turn of the session.
  const forcedVariant =
    options.forcedVariant ??
    (options.readAbVariantFromUrl === false ? undefined : readAbVariantFromLocation());

  // Persona mode wins over agent mode when both subjects are (mis)configured.
  const personaId = persona?.id;
  const personaLlmInstanceId = persona?.llmInstanceId;
  const isPersonaMode = Boolean(personaId && personaLlmInstanceId);
  const agentId = isPersonaMode ? undefined : agent?.id;
  const agentLlmInstanceId = isPersonaMode ? undefined : agent?.llmInstanceId;
  const isAgentMode = Boolean(agentId && agentLlmInstanceId);
  // Both persona and agent mode carry an explicit LLM, so neither needs the
  // site name or the RAG-enabled probe.
  const isSubjectMode = isAgentMode || isPersonaMode;

  if (!site && !isSubjectMode) {
    throw new Error(
      "createChatController in site mode requires `options.site`. " +
        "Either pass it or pass `options.agent` / `options.persona`.",
    );
  }

  const persistKey = persist && site ? storageKey(site, persist) : null;
  const initialPersisted = persistKey
    ? loadPersisted(persistKey)
    : { conversationId: controlledConversationId ?? null, messages: initialMessages ?? [] };

  const store = createStore<ChatControllerState>({
    enabled: isSubjectMode ? true : null,
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

  // T458 — analytics lifecycle bookkeeping. `chatStarted` gates the once-per-
  // conversation `turing_chat_start`; `userTurns` rides along on every event.
  let chatStarted = false;
  let userTurns = 0;
  // Seed the bus envelope with the identity known at construction. The
  // conversation/session id is stamped lazily in `send` once resolved.
  analytics?.setContext({ site: site || undefined, agentId });

  // T460 — abandonment / engagement detection. `converted` (set by T461 on a
  // lead capture / handoff) and `abandonmentFired` (once-only) guard the fire;
  // `convStartedAt` measures elapsed time; `lastNodeId` is the last flow step
  // reached (populated by T461's step tracking) reported in the event.
  const abandonmentEnabled = Boolean(analytics) && abandonment !== false;
  const abandonmentOptions: AbandonmentOptions =
    typeof abandonment === "object" ? abandonment : {};
  let abandonmentWatcher: AbandonmentWatcher | null = null;
  let abandonmentFired = false;
  let converted = false;
  let convStartedAt = 0;
  let lastNodeId: string | null = null;

  function fireAbandonment(reason: string): void {
    if (!analytics || abandonmentFired || converted || userTurns < 1) return;
    abandonmentFired = true;
    analytics.emit(TURING_ANALYTICS_EVENTS.chatAbandoned, {
      reason,
      turns: userTurns,
      last_step: userTurns,
      last_node_id: lastNodeId ?? undefined,
      elapsed_ms: convStartedAt ? Date.now() - convStartedAt : undefined,
    });
  }

  // T461 — A/B attribution fires once per conversation, then every event
  // carries the stamped experiment/variant/persona via the bus context.
  let abAssigned = false;

  /**
   * Emits a single `turing_chat_lead_captured` and marks the conversation
   * converted (so abandonment never fires afterwards). Idempotent.
   */
  function emitLeadCaptured(reason: string, extra?: Record<string, string | number>): void {
    if (!analytics || converted) return;
    converted = true;
    analytics.emit(TURING_ANALYTICS_EVENTS.chatLeadCaptured, {
      reason,
      turns: userTurns,
      ...extra,
    });
  }

  /**
   * T461 — best-effort, fire-and-forget post-turn analytics (site mode only,
   * where `/chat/state` + `/chat/slots` are scoped). Resolves A/B attribution
   * (stamps the bus context + emits `turing_ab_variant_assigned` once), emits a
   * `turing_chat_step` when the flow cursor advances, and emits
   * `turing_chat_lead_captured` when a configured goal slot is written. Never
   * throws into the UI — analytics is additive.
   */
  async function runAnalyticsPostTurn(cid: string): Promise<void> {
    if (!analytics || !site) return;
    try {
      const state = await fetchSiteChatState(client, site, cid);
      const hasAb = Boolean(state.experimentKey || state.variantLabel || state.personaId);
      if (!abAssigned && hasAb) {
        abAssigned = true;
        analytics.setContext({
          experimentKey: state.experimentKey ?? undefined,
          variantLabel: state.variantLabel ?? undefined,
          personaId: state.personaId ?? undefined,
        });
        analytics.emit(TURING_ANALYTICS_EVENTS.abVariantAssigned);
      } else if (state.personaId && analytics.getContext().personaId !== state.personaId) {
        // Persona can switch mid-conversation — keep the stamp current.
        analytics.setContext({ personaId: state.personaId });
      }
      if (state.currentNodeId && state.currentNodeId !== lastNodeId) {
        lastNodeId = state.currentNodeId;
        analytics.emit(TURING_ANALYTICS_EVENTS.chatStep, {
          node_id: state.currentNodeId,
          flow_id: state.flowId ?? undefined,
          flow_name: state.flowName ?? undefined,
          step: userTurns,
        });
      }
      if (goalSlots.length > 0 && !converted) {
        const slotsRes = await fetchSiteChatSlots(client, site, cid);
        const slots = slotsRes?.slots ?? {};
        const hit = goalSlots.find((name) => {
          const value = slots[name];
          return typeof value === "string" && value.trim() !== "";
        });
        if (hit) emitLeadCaptured("goal_slot", { goal_slot: hit });
      }
    } catch {
      // best-effort — a failed state/slots read must not break analytics
    }
  }

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

  // Resolve enabled flag once (skipped in agent/persona mode). Fire-and-forget.
  if (isSubjectMode || !site) {
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

    // T458 — keep the analytics envelope in sync, then emit the chat lifecycle
    // events. `turing_chat_start` fires once per conversation; every send emits
    // `turing_chat_message_sent` with the running user-turn count.
    if (analytics) {
      analytics.setContext({
        conversationId: activeConversationId,
        sessionId: activeConversationId,
      });
      userTurns += 1;
      if (!chatStarted) {
        chatStarted = true;
        convStartedAt = Date.now();
        analytics.emit(TURING_ANALYTICS_EVENTS.chatStart, {
          mode: isPersonaMode ? "persona" : isAgentMode ? "agent" : "site",
        });
        // T460 — arm the abandonment watcher once the conversation is real.
        if (abandonmentEnabled && !abandonmentWatcher) {
          abandonmentWatcher = createAbandonmentWatcher(fireAbandonment, abandonmentOptions);
        }
      }
      analytics.emit(TURING_ANALYTICS_EVENTS.chatMessageSent, {
        turn: userTurns,
        length: content.length,
      });
      // Each message is engagement — reset the idle clock.
      abandonmentWatcher?.ping();
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

      // T436 — patch the live tool-call list onto the in-flight assistant bubble
      // as `tool_call` events arrive, so a UI can show running tool activity
      // before the answer text streams in. `all` is already merged by callId.
      const onToolCall = (_call: TurChatToolCall, all: TurChatToolCall[]) => {
        if (requestId !== latestRequestId || aborted) return;
        commitMessages(
          patchMessage(messages, assistantId, (m) => ({ ...m, toolCalls: all })),
        );
      };

      // Picks the transport for this turn: persona (T579, stateless), else
      // agent, else the site RAG endpoint.
      const dispatchSubjectTurn = () => {
        if (personaId && effectiveLlmInstanceId) {
          return postPersonaChat(client, personaId, effectiveLlmInstanceId, wire, {
            onToken,
            onToolCall,
            signal: controller.signal,
          });
        }
        if (agentId && effectiveLlmInstanceId) {
          return postAgentChat(client, agentId, effectiveLlmInstanceId, wire, {
            conversationId: activeConversationId,
            flowId,
            forcedVariant,
            onToken,
            onToolCall,
            signal: controller.signal,
          });
        }
        return postChatConversation(client, site, wire, locale, {
          conversationId: activeConversationId,
          flowId,
          forcedVariant,
          personaId: ragPersonaId,
          onToken,
          onToolCall,
          signal: controller.signal,
        });
      };
      let res = await dispatchSubjectTurn();

      // T516 — carry the last leg's guardrail verdict (set just below).
      let aggregatedGrounding: TurChatGrounding | undefined = res?.grounding;
      // T522 — carry the last leg's second-opinion verdict.
      let aggregatedSecondOpinion: TurChatSecondOpinion | undefined = res?.secondOpinion;

      if (requestId !== latestRequestId || aborted) return;

      // T438 / T439 — drive any client-tool round-trips. The turn parked with a
      // `client_tool_call` instead of an answer: run the registered handler,
      // POST its result, and consume the continuation into the same bubble
      // (tokens append live via `onToken`). Repeat until the agent answers or a
      // hop cap trips. `content` is accumulated across legs.
      let aggregatedContent = res?.content ?? "";
      let aggregatedToolCalls: TurChatToolCall[] = res?.toolCalls ?? [];
      let hops = 0;
      while (res?.clientToolCall && hops < MAX_CLIENT_TOOL_HOPS) {
        hops += 1;
        const call = res.clientToolCall;
        const { result, error } = await runClientToolHandler(clientToolHandlers, call);
        res = await postClientToolResult(
          client,
          { conversationId: activeConversationId, callId: call.callId, result, error },
          { onToken, onToolCall, signal: controller.signal },
        );
        if (requestId !== latestRequestId || aborted) return;
        aggregatedContent += res?.content ?? "";
        if (res?.toolCalls?.length) {
          aggregatedToolCalls = mergeToolCallLists(aggregatedToolCalls, res.toolCalls);
        }
        if (res?.grounding) {
          aggregatedGrounding = res.grounding;
        }
        if (res?.secondOpinion) {
          aggregatedSecondOpinion = res.secondOpinion;
        }
      }

      const finalOptions = res?.options && res.options.length > 0 ? res.options : undefined;
      const finalForm = res?.form && res.form.fields?.length > 0 ? res.form : undefined;
      const finalSources = res?.sources && res.sources.length > 0 ? res.sources : undefined;
      const finalCitations =
        res?.citations && res.citations.length > 0 ? res.citations : undefined;
      const finalToolCalls = aggregatedToolCalls.length > 0 ? aggregatedToolCalls : undefined;
      commitMessages(
        patchMessage(messages, assistantId, (m) => ({
          ...m,
          content: aggregatedContent || m.content,
          options: finalOptions,
          form: finalForm,
          sources: finalSources,
          citations: finalCitations,
          // keep the live-accumulated toolCalls if the final response omitted them
          toolCalls: finalToolCalls ?? m.toolCalls,
          grounding: aggregatedGrounding,
          secondOpinion: aggregatedSecondOpinion,
        })),
        "success",
      );
      store.setState({ activeForm: finalForm ?? null });
      // T461 — fire-and-forget funnel-step / A/B attribution / goal-slot
      // conversion off the freshly-updated server state. Site mode only.
      if (analytics && site) void runAnalyticsPostTurn(activeConversationId);
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

    // T461 — a native form capture (T107) is a conversion: when no goal slots
    // are configured, any submit counts; otherwise only one that writes a goal
    // slot does.
    if (analytics) {
      const isLead = goalSlots.length === 0 || goalSlots.some((name) => name in values);
      if (isLead) emitLeadCaptured("form");
    }

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
    // T458 — a reset starts a fresh conversation; re-arm the start event.
    chatStarted = false;
    userTurns = 0;
    // T460 — tear down the old watcher and clear the abandonment/conversion
    // guards so the next conversation is tracked from scratch.
    abandonmentWatcher?.stop();
    abandonmentWatcher = null;
    abandonmentFired = false;
    converted = false;
    abAssigned = false;
    convStartedAt = 0;
    lastNodeId = null;
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
    // T460 — release lifecycle listeners so we don't leak across mounts.
    abandonmentWatcher?.stop();
    abandonmentWatcher = null;
  }

  function registerClientTool(name: string, handler: ClientToolHandler): () => void {
    clientToolHandlers.set(name, handler);
    return () => unregisterClientTool(name);
  }

  function unregisterClientTool(name: string): void {
    clientToolHandlers.delete(name);
  }

  return {
    getState: store.getState,
    setState: store.setState,
    subscribe: store.subscribe,
    send,
    submitForm,
    registerClientTool,
    unregisterClientTool,
    stop,
    reset,
    resetFlow,
    destroy,
  };
}
