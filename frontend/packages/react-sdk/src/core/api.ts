import axios from "axios";
import type {
  TurChatConversationMessage,
  TurChatConversationResponse,
  TurChatResponse,
  TurChatStreamEvent,
  TurIntent,
  TurSearchResponse,
  TurSortOption,
} from "./types";

/**
 * Low-level Turing API client. All hooks delegate to this module.
 * Uses the globally configured axios instance from the host app,
 * inheriting baseURL, CSRF, auth headers, and interceptors.
 *
 * @since 2026.2.0
 */

export interface SearchParams {
  q: string;
  p?: string;
  _setlocale?: string;
  sort?: string;
  fq?: string[];
  tr?: string[];
  nfpr?: string;
  /** Group results by a field value (e.g. "templateName") */
  group?: string;
  /** Number of results per page */
  rows?: string;
  /** Facet ordering profile name (falls back to the site's default profile when unset/unknown) */
  profile?: string;
}

function buildQueryString(params: SearchParams): string {
  const qs = new URLSearchParams();

  qs.append("q", params.q || "*");
  if (params.p) qs.append("p", params.p);
  if (params._setlocale) qs.append("_setlocale", params._setlocale);
  if (params.sort) qs.append("sort", params.sort);

  for (const v of params.fq ?? []) qs.append("fq[]", v);
  for (const v of params.tr ?? []) qs.append("tr[]", v);

  if (params.nfpr) qs.append("nfpr", params.nfpr);
  if (params.group) qs.append("group", params.group);
  if (params.rows) qs.append("rows", params.rows);
  if (params.profile) qs.append("profile", params.profile);

  return qs.toString().replaceAll("%5B%5D", "[]");
}

async function get<T>(url: string): Promise<T> {
  const { data } = await axios.get<T>(url);
  return data;
}

export function fetchSearch(site: string, params: SearchParams): Promise<TurSearchResponse> {
  return get<TurSearchResponse>(`/sn/${site}/search?${buildQueryString(params)}`);
}

/**
 * Payload for {@link postClick}. The minimum needed to reconstruct CTR is the
 * query that produced the result (`term`) and the document the user opened
 * (`documentId`). `position` is the 1-based rank within the page (used to
 * study scroll fatigue). `userId` and `locale` are optional but, when
 * provided, let dashboards split CTR by user cohort or locale.
 *
 * @since 2026.2.7
 */
export interface ClickTrackParams {
  /** Query that produced the result the user clicked. */
  readonly term: string;
  /** Identifier of the clicked document (URL/UUID/business id, free-form). */
  readonly documentId: string;
  /** 1-based rank of the clicked result within the page. */
  readonly position: number;
  /** Optional opaque user identifier. Defaults to anonymous on the server. */
  readonly userId?: string;
  /** BCP-47 or POSIX locale string ("en-US" / "en_US"). */
  readonly locale?: string;
}

/**
 * Records a click-through on a search result. Fire-and-forget by design — the
 * SDK never blocks UX waiting for this to land. Errors are swallowed and
 * surfaced via {@code console.warn}.
 *
 * Server endpoint: {@code POST /api/sn/{site}/click}. Toggled by
 * {@code turing.search.metrics.enabled} on the backend.
 *
 * @since 2026.2.7
 */
export async function postClick(site: string, params: ClickTrackParams): Promise<void> {
  try {
    await axios.post(`/sn/${site}/click`, params);
  } catch (error) {
    if (typeof console !== "undefined") {
      // eslint-disable-next-line no-console
      console.warn("[Turing] click tracking failed", error);
    }
  }
}

export function fetchChat(site: string, params: Pick<SearchParams, "q" | "_setlocale">): Promise<TurChatResponse> {
  const qs = new URLSearchParams();
  qs.append("q", params.q);
  if (params._setlocale) qs.append("_setlocale", params._setlocale);
  return get<TurChatResponse>(`/sn/${site}/chat?${qs}`);
}

/**
 * Response of {@link fetchChatEnabled}. {@code sessionCookieName} and
 * {@code sessionTtlSeconds} drive the {@code TUR_SESSION} cookie minted by
 * the SDK — both come from {@code turing.chat.session.*} server config so
 * a deployment can override them without rebuilding the frontend.
 *
 * @since 2026.2.4 (extended in 2026.2.17 with session config)
 */
export interface ChatEnabledResponse {
  readonly enabled: boolean;
  /**
   * Diagnostic code explaining why chat is unavailable. `"NONE"` when
   * `enabled` is true; otherwise one of: `"NO_GENAI"`, `"NO_AGENT"`,
   * `"AGENT_DISABLED"`, `"RAG_DISABLED"`, `"MISSING_LLM"`,
   * `"MISSING_EMBEDDING"`, `"MISSING_STORE"`. May be absent on older
   * Turing backends — treat absence as opaque "disabled".
   *
   * @since 2026.2.17
   */
  readonly reason?: ChatDisabledReason;
  readonly sessionCookieName?: string;
  readonly sessionTtlSeconds?: number;
}

/**
 * Why the chat endpoint is disabled for a site. Mirror of the
 * server-side enum in {@code TurSNSiteGenAiAPI.ChatDisabledReason}.
 *
 * @since 2026.2.17
 */
export type ChatDisabledReason =
  | "NONE"
  | "NO_GENAI"
  | "NO_AGENT"
  | "AGENT_DISABLED"
  | "RAG_DISABLED"
  | "MISSING_LLM"
  | "MISSING_EMBEDDING"
  | "MISSING_STORE";

/**
 * Human-readable hint for each {@link ChatDisabledReason} value.
 * The frontend can use it directly as a tooltip on the AI Mode toggle
 * when the chat is unavailable. Localized to PT-BR by default since
 * the diagnostic is admin-facing in the only locale the SDK currently ships.
 *
 * @since 2026.2.17
 */
export const CHAT_DISABLED_HINTS: Readonly<Record<ChatDisabledReason, string>> = {
  NONE: "Chat com IA está disponível",
  NO_GENAI: "Site não tem GenAI configurado no admin do Turing",
  NO_AGENT: "Nenhum AI Agent vinculado a este site no admin do Turing",
  AGENT_DISABLED: "O AI Agent vinculado está desabilitado",
  RAG_DISABLED: "RAG está desabilitado no AI Agent (habilite no admin)",
  MISSING_LLM: "O AI Agent não tem nenhuma instância de LLM configurada",
  MISSING_EMBEDDING: "O AI Agent não tem um modelo de embedding selecionado",
  MISSING_STORE: "O AI Agent não tem um vector store selecionado",
};

/**
 * Lightweight check whether the site has RAG (Generative AI) enabled.
 * Used by the search UI to conditionally expose the "AI Mode" toggle.
 *
 * @since 2026.2.4
 */
export function fetchChatEnabled(site: string): Promise<ChatEnabledResponse> {
  return get<ChatEnabledResponse>(`/sn/${site}/chat/enabled`);
}

/**
 * Reads the XSRF token cookie set by the host application's axios config,
 * so the SSE fetch call below stays in parity with axios' built-in CSRF
 * handling. Returns `undefined` when the cookie is absent (anonymous SPAs).
 */
function readXsrfCookie(): string | undefined {
  if (typeof document === "undefined") return undefined;
  const match = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(document.cookie);
  return match ? decodeURIComponent(match[1]) : undefined;
}

export interface PostChatConversationOptions {
  /**
   * Called once per SSE chunk with the chunk's `content` field. Use it to
   * render a typing/streaming effect. The same content is also accumulated
   * and returned in the final {@link TurChatConversationResponse}.
   */
  readonly onToken?: (token: string) => void;
  /**
   * Called once if the chat-flow engine emits an `"options"` SSE event for
   * the turn — with the parsed `string[]` of chip labels to render below the
   * assistant message. Picking a chip is equivalent to sending that label as
   * the next user message.
   *
   * @since 2026.2.17
   */
  readonly onOptions?: (options: string[]) => void;
  /** Aborts the in-flight SSE request when triggered. */
  readonly signal?: AbortSignal;
  /**
   * Stable id that groups all turns of the same conversation. Required for
   * the chat-flow router to fire from turn 1 — without it, the backend
   * skips flow selection entirely and the agent answers from its base
   * system prompt only. Generate once per conversation on the client and
   * reuse for every send until the user resets.
   *
   * @since 2026.2.16
   */
  readonly conversationId?: string;
  /**
   * Forces a specific chat flow to govern the turn instead of letting the
   * router auto-pick. Used by admin/preview UIs that need to simulate a
   * particular path. Most callers should leave this unset.
   *
   * @since 2026.2.16
   */
  readonly flowId?: string;
}

/**
 * Drains a Spring `Flux<ChatResponse>` SSE stream and returns both the
 * concatenated assistant text and any chip labels emitted by the chat-flow
 * engine via a `type === "options"` event. Each `data:` line is parsed as
 * {@link TurChatStreamEvent} and routed by {@code type}:
 *
 * - `"token"` (or omitted, for older servers): {@code content} is text — append
 *   to {@code aggregated} and forward through {@code onToken}.
 * - `"options"`: {@code content} is a JSON-encoded `string[]` — parsed once
 *   and surfaced via {@code onOptions} and on the returned value. Empty/
 *   malformed payloads are silently dropped.
 *
 * Shared between the site RAG chat and the agent chat — both endpoints emit
 * the same shape.
 */
async function consumeAssistantStream(
  response: Response,
  onToken?: (token: string) => void,
  onOptions?: (options: string[]) => void,
): Promise<{ text: string; options: string[] }> {
  if (!response.body) {
    throw new Error("Chat response has no body");
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let aggregated = "";
  let optionLabels: string[] = [];

  function consumeLine(line: string) {
    if (!line.startsWith("data:")) return;
    const jsonStr = line.slice(5).trim();
    if (!jsonStr) return;
    let parsed: TurChatStreamEvent;
    try {
      parsed = JSON.parse(jsonStr) as TurChatStreamEvent;
    } catch {
      // Skip malformed JSON chunks (e.g. heartbeat comments)
      return;
    }
    if (parsed.type === "options") {
      // `content` is itself a JSON-encoded string[] — parse it once. Silently
      // drop malformed/empty payloads rather than spamming the console; the
      // UI just won't render chips for this turn.
      try {
        const parsedOptions = JSON.parse(parsed.content) as unknown;
        if (Array.isArray(parsedOptions)) {
          const labels = parsedOptions.filter((s): s is string => typeof s === "string");
          if (labels.length > 0) {
            optionLabels = labels;
            onOptions?.(labels);
          }
        }
      } catch {
        // ignore
      }
      return;
    }
    // Default to text. Treat any unknown `type` as a token event so old
    // servers (no `type` field) and forward-compat servers both work.
    if (parsed.content) {
      aggregated += parsed.content;
      onToken?.(parsed.content);
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() ?? "";
      for (const line of lines) consumeLine(line);
    }
    if (buffer) consumeLine(buffer);
  } finally {
    reader.releaseLock();
  }
  return { text: aggregated, options: optionLabels };
}

/**
 * Conversational RAG: sends the full message history to the site's GenAI
 * chat (`POST /sn/{site}/chat/conversation`) and consumes the Server-Sent
 * Events stream returned by the backend. Each `data:` line is parsed as a
 * {@link TurChatStreamEvent}; the chunks are aggregated and returned as a
 * single {@link TurChatConversationResponse} after the stream closes.
 *
 * The server uses the latest user message for document retrieval and
 * preserves prior turns as conversation context.
 *
 * @since 2026.2.4 (rewritten for SSE in 2026.2.15)
 */
export async function postChatConversation(
  site: string,
  messages: TurChatConversationMessage[],
  locale?: string,
  options?: PostChatConversationOptions,
): Promise<TurChatConversationResponse> {
  const baseURL = axios.defaults.baseURL ?? "";
  const url = `${baseURL}/sn/${site}/chat/conversation`;

  const headers: Record<string, string> = {
    Accept: "text/event-stream",
    "Content-Type": "application/json",
  };
  const xsrf = readXsrfCookie();
  if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;

  const response = await fetch(url, {
    method: "POST",
    headers,
    credentials: "include",
    body: JSON.stringify({
      messages,
      locale,
      conversationId: options?.conversationId,
      flowId: options?.flowId,
    }),
    signal: options?.signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const { text, options: chipOptions } = await consumeAssistantStream(
    response,
    options?.onToken,
    options?.onOptions,
  );
  return { role: "assistant", content: text, options: chipOptions };
}

/**
 * Options for {@link postAgentChat}. Mirrors {@link PostChatConversationOptions}
 * — the agent endpoint speaks the same SSE shape as the SN site RAG endpoint.
 *
 * @since 2026.2.16
 */
export interface PostAgentChatOptions {
  readonly onToken?: (token: string) => void;
  /**
   * Called once if the chat-flow engine emits an `"options"` SSE event for
   * the turn. See {@link PostChatConversationOptions.onOptions}.
   *
   * @since 2026.2.17
   */
  readonly onOptions?: (options: string[]) => void;
  readonly signal?: AbortSignal;
  readonly conversationId?: string;
  readonly flowId?: string;
}

/**
 * Streams a turn through an explicit AI Agent (`POST /v2/ai-agent/{agentId}/chat`).
 * Use this when the consumer knows the exact agent + LLM to invoke (e.g. an
 * authenticated console UI). For anonymous site search, use
 * {@link postChatConversation} instead — it resolves the agent from the site
 * configuration server-side.
 *
 * @since 2026.2.16
 */
export async function postAgentChat(
  agentId: string,
  llmInstanceId: string,
  messages: TurChatConversationMessage[],
  options?: PostAgentChatOptions,
): Promise<TurChatConversationResponse> {
  const baseURL = axios.defaults.baseURL ?? "";
  const url = `${baseURL}/v2/ai-agent/${agentId}/chat`;

  const headers: Record<string, string> = {
    Accept: "text/event-stream",
    "Content-Type": "application/json",
  };
  const xsrf = readXsrfCookie();
  if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;

  const response = await fetch(url, {
    method: "POST",
    headers,
    credentials: "include",
    body: JSON.stringify({
      llmInstanceId,
      messages,
      conversationId: options?.conversationId,
      flowId: options?.flowId,
    }),
    signal: options?.signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const { text, options: chipOptions } = await consumeAssistantStream(
    response,
    options?.onToken,
    options?.onOptions,
  );
  return { role: "assistant", content: text, options: chipOptions };
}

/**
 * Drops every chat-flow runtime state for {@code conversationId} on the
 * site's agent. Use to wire a "New chat" button on public search UIs —
 * client-side `reset()` only clears local state; this clears the server-side
 * mirror so a forced flow doesn't resume on the next conversation that
 * reuses an old id.
 *
 * @returns number of state rows the server deleted (0 when nothing matched).
 * @since 2026.2.16
 */
export async function deleteSiteConversationState(
  site: string,
  conversationId: string,
): Promise<number> {
  const params = new URLSearchParams({ conversationId });
  const { data } = await axios.delete<number>(
    `/sn/${site}/chat/conversation-state?${params}`,
  );
  return typeof data === "number" ? data : 0;
}

/**
 * Drops the runtime state of {@code (conversationId, flowId)} on the agent
 * endpoint. Mirror of {@link deleteSiteConversationState} for the explicit
 * agent path.
 *
 * @since 2026.2.16
 */
export async function deleteAgentFlowState(
  agentId: string,
  flowId: string,
  conversationId: string,
): Promise<boolean> {
  const params = new URLSearchParams({ flowId, conversationId });
  const { data } = await axios.delete<boolean>(
    `/v2/ai-agent/${agentId}/chat-flow-state?${params}`,
  );
  return data === true;
}

/**
 * Fetches the curated list of enabled Intents for the site's AI agent.
 * Each Intent groups quick-prompt suggestions ("actions") that authors set
 * up in the Turing admin to surface as chips below the chat composer.
 * Returns an empty array when the site has no agent or no enabled intents.
 *
 * @since 2026.2.12
 */
export function fetchIntents(site: string): Promise<TurIntent[]> {
  return get<TurIntent[]>(`/sn/${site}/chat/intents`);
}

/**
 * Slots captured during a chat session, returned as a single flat map keyed
 * by slot name. The backend merges values across every flow (active or
 * completed) of the AI agent that the conversation touched, so consumers
 * never need to walk a per-flow tree.
 *
 * @since 2026.2.7
 */
export interface TurChatSessionSlots {
  readonly conversationId: string;
  readonly slots: Readonly<Record<string, string>>;
}

/**
 * Reads the slots captured during the conversation from the site's RAG
 * endpoint. Use when the SDK is in site mode (the same default as
 * {@link postChatConversation}). Pair with {@link useTuringSlots} to wire
 * a polled refresh.
 *
 * Server endpoint: {@code GET /api/sn/{site}/chat/slots?conversationId=...}.
 *
 * @since 2026.2.7
 */
export function fetchSiteChatSlots(
  site: string,
  conversationId: string,
): Promise<TurChatSessionSlots> {
  const qs = new URLSearchParams({ conversationId });
  return get<TurChatSessionSlots>(`/sn/${site}/chat/slots?${qs}`);
}

/**
 * Reads the slots captured during the conversation from the agent endpoint.
 * Use when the SDK is in agent mode (mirrors {@link postAgentChat}).
 *
 * Server endpoint: {@code GET /api/v2/ai-agent/{agentId}/chat-slots?conversationId=...}.
 *
 * @since 2026.2.7
 */
export function fetchAgentChatSlots(
  agentId: string,
  conversationId: string,
): Promise<TurChatSessionSlots> {
  const qs = new URLSearchParams({ conversationId });
  return get<TurChatSessionSlots>(`/v2/ai-agent/${agentId}/chat-slots?${qs}`);
}

/**
 * Server response for the slot-write endpoint. {@code updatedStates} is the
 * number of chat-flow state rows that absorbed the write — {@code 0} when
 * the conversation has no active flow yet (caller must send the first chat
 * message before writing slots).
 *
 * @since 2026.2.7
 */
export interface TurChatSlotWriteResponse {
  readonly updatedStates: number;
}

/**
 * Writes a single slot on the current conversation through the site's GenAI
 * endpoint. Companion to {@link fetchSiteChatSlots} — lets a React component
 * force a slot value (e.g. user picks an option via button) without sending
 * a chat message. The backend writes to every active flow state for
 * {@code conversationId}; if none exists yet, the call is a no-op and the
 * response reports {@code updatedStates: 0}.
 *
 * Server endpoint: {@code POST /api/sn/{site}/chat/slots}.
 *
 * @since 2026.2.7
 */
export async function postSiteChatSlot(
  site: string,
  conversationId: string,
  name: string,
  value: string,
): Promise<TurChatSlotWriteResponse> {
  const { data } = await axios.post<TurChatSlotWriteResponse>(
    `/sn/${site}/chat/slots`,
    { conversationId, name, value },
  );
  return data ?? { updatedStates: 0 };
}

/**
 * Server response for the slot-extract endpoint. {@code extracted} maps
 * each requested slot name to the value the LLM produced (or {@code null}
 * when the document didn't contain enough info to fill that slot).
 * {@code slotsWritten} counts how many were persisted by the engine —
 * may be less than {@code Object.keys(extracted).length} if some values
 * were null or the conversation had no live flow state yet.
 *
 * @since 2026.2.7
 */
export interface TurChatSlotExtractResponse {
  readonly extracted: Readonly<Record<string, string | null>>;
  readonly slotsWritten: number;
  readonly extractedTextChars: number;
  /** Set when the server short-circuited (site/agent missing). */
  readonly error?: string | null;
}

/**
 * Uploads a document (PDF / DOCX / TXT / RTF / HTML — anything Apache Tika
 * auto-detects) and asks the server to extract values for the requested
 * slots using the agent's configured LLM. Each extracted field is written
 * to the conversation's chat-flow state via the same path as the
 * {@code POST /chat/slots} writer — so {@link useTuringSlots} subscribers
 * receive the new values over SSE in &lt;100 ms.
 *
 * <p>Generic by design: caller supplies the {@code slotNames} array
 * (e.g. {@code ["name", "cargo_atual", "objetivo"]}) — if omitted, the
 * server targets the entire agent slot catalog.
 *
 * Server endpoint: {@code POST /api/sn/{site}/chat/slot-extract}.
 *
 * @since 2026.2.7
 */
export async function postSiteSlotExtract(
  site: string,
  file: File,
  conversationId: string,
  slotNames?: ReadonlyArray<string>,
): Promise<TurChatSlotExtractResponse> {
  const form = new FormData();
  form.append("file", file);
  form.append("conversationId", conversationId);
  if (slotNames && slotNames.length > 0) {
    form.append("slotNames", slotNames.join(","));
  }
  const { data } = await axios.post<TurChatSlotExtractResponse>(
    `/sn/${site}/chat/slot-extract`,
    form,
  );
  return data ?? { extracted: {}, slotsWritten: 0, extractedTextChars: 0 };
}

/**
 * Snapshot of a conversation's active chat-flow state. Returned by the
 * lightweight {@code GET /chat/state} endpoint — useful for debug
 * panels and for app-level logic that needs to know which A/B variant
 * the visitor was routed to. All fields are nullable when the
 * conversation hasn't started yet.
 *
 * @since 2026.2.7
 */
export interface TurChatConversationState {
  readonly conversationId: string;
  readonly flowId: string | null;
  readonly flowName: string | null;
  readonly currentNodeId: string | null;
  readonly guardrailMethod: string | null;
  readonly experimentKey: string | null;
  readonly variantLabel: string | null;
}

/**
 * Lightweight read of the conversation's flow state. No LLM call, no
 * slot polling — just one row from the chat-flow state table. Used by
 * {@link useTuringFlowState} and {@link useTuringExperiment}.
 *
 * Server endpoint: {@code GET /api/sn/{site}/chat/state?conversationId=...}.
 *
 * @since 2026.2.7
 */
export function fetchSiteChatState(
  site: string,
  conversationId: string,
): Promise<TurChatConversationState> {
  const qs = new URLSearchParams({ conversationId });
  return get<TurChatConversationState>(`/sn/${site}/chat/state?${qs}`);
}

/**
 * Channels accepted by the handoff endpoint. {@code whatsapp} produces a
 * {@code wa.me} universal link; {@code email} a {@code mailto:};
 * {@code sms} an {@code sms:} link. New channels plug in on the server
 * side without touching this SDK.
 *
 * @since 2026.2.7
 */
export type TurChatHandoffChannel =
  | "whatsapp"
  | "email"
  | "sms"
  | "telegram"
  | "slack";

/**
 * Server response for the handoff endpoint. {@code url} is the absolute
 * deep link the client should open (e.g. via {@code window.open(url, "_blank")});
 * {@code transcript} is the rendered slot context embedded inside the
 * URL's body, returned verbatim so the UI can preview to the visitor
 * what the consultant will see.
 *
 * @since 2026.2.7
 */
export interface TurChatHandoffResponse {
  readonly url: string | null;
  readonly transcript: string | null;
  readonly slotsIncluded: number;
  readonly error?: string | null;
}

/**
 * Builds a channel-specific handoff deep link carrying the conversation
 * context. The visitor's captured slots (name, cargo, area, objetivo,
 * etc.) are embedded in the link's message body so the human consultant
 * picks up the conversation without re-asking. Side effect on the
 * server: writes {@code handoff_status = "{channel}_requested"} to the
 * chat-flow state for analytics tracking.
 *
 * Server endpoint: {@code POST /api/sn/{site}/chat/handoff}.
 *
 * @example
 * ```ts
 * const result = await postSiteHandoff(site, {
 *   conversationId: "abc-123",
 *   channel: "whatsapp",
 *   destination: "5511999999999",
 *   intro: "Olá! Vim do site de Executive Education.",
 *   slotsToInclude: ["name", "cargo_atual", "area_label", "objetivo"],
 * });
 * if (result.url) window.open(result.url, "_blank", "noopener,noreferrer");
 * ```
 *
 * @since 2026.2.7
 */
export async function postSiteHandoff(
  site: string,
  params: {
    readonly conversationId: string;
    readonly channel: TurChatHandoffChannel;
    readonly destination: string;
    readonly intro?: string;
    readonly slotsToInclude?: ReadonlyArray<string>;
  },
): Promise<TurChatHandoffResponse> {
  const { data } = await axios.post<TurChatHandoffResponse>(
    `/sn/${site}/chat/handoff`,
    {
      conversationId: params.conversationId,
      channel: params.channel,
      destination: params.destination,
      intro: params.intro,
      slotsToInclude: params.slotsToInclude,
    },
  );
  return data ?? { url: null, transcript: null, slotsIncluded: 0 };
}

export function fetchAutoComplete(site: string, params: SearchParams): Promise<string[]> {
  return get<string[]>(`/sn/${site}/ac?${buildQueryString(params)}`);
}

export function fetchSortOptions(site: string): Promise<TurSortOption[]> {
  return get<TurSortOption[]>(`/sn/${site}/search/sort-options`);
}

/**
 * Parses an href link from the Turing API (e.g. facet link, pagination link)
 * into a SearchParams object that can be used to re-execute a search.
 */
export function parseHrefToParams(href: string): SearchParams {
  const qs = href.includes("?") ? href.split("?")[1] : href;
  const usp = new URLSearchParams(qs);

  const fq: string[] = [];
  const tr: string[] = [];

  usp.getAll("fq").forEach((v) => fq.push(v));
  usp.getAll("fq[]").forEach((v) => fq.push(v));
  usp.getAll("tr").forEach((v) => tr.push(v));
  usp.getAll("tr[]").forEach((v) => tr.push(v));

  return {
    q: usp.get("q") || "*",
    p: usp.get("p") || "1",
    _setlocale: usp.get("_setlocale") || undefined,
    sort: usp.get("sort") || undefined,
    fq: fq.length ? fq : undefined,
    tr: tr.length ? tr : undefined,
    nfpr: usp.get("nfpr") || undefined,
    group: usp.get("group") || undefined,
    rows: usp.get("rows") || undefined,
    profile: usp.get("profile") || undefined,
  };
}
