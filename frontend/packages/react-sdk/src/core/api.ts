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
  readonly sessionCookieName?: string;
  readonly sessionTtlSeconds?: number;
}

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
 * Drains a Spring `Flux<ChatResponse>` SSE stream into a single concatenated
 * string. Each `data:` line is parsed as {@link TurChatStreamEvent}; the
 * chunks are aggregated and forwarded one-by-one to {@code onToken} when
 * provided. Shared between the site RAG chat and the agent chat — both
 * endpoints emit the same shape.
 */
async function consumeAssistantStream(
  response: Response,
  onToken?: (token: string) => void,
): Promise<string> {
  if (!response.body) {
    throw new Error("Chat response has no body");
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let aggregated = "";

  function consumeLine(line: string) {
    if (!line.startsWith("data:")) return;
    const jsonStr = line.slice(5).trim();
    if (!jsonStr) return;
    try {
      const parsed = JSON.parse(jsonStr) as TurChatStreamEvent;
      if (parsed.content) {
        aggregated += parsed.content;
        onToken?.(parsed.content);
      }
    } catch {
      // Skip malformed JSON chunks (e.g. heartbeat comments)
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
  return aggregated;
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

  const aggregated = await consumeAssistantStream(response, options?.onToken);
  return { role: "assistant", content: aggregated };
}

/**
 * Options for {@link postAgentChat}. Mirrors {@link PostChatConversationOptions}
 * — the agent endpoint speaks the same SSE shape as the SN site RAG endpoint.
 *
 * @since 2026.2.16
 */
export interface PostAgentChatOptions {
  readonly onToken?: (token: string) => void;
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

  const aggregated = await consumeAssistantStream(response, options?.onToken);
  return { role: "assistant", content: aggregated };
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
  };
}
