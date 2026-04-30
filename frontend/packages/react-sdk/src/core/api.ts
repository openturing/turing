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

export function fetchChat(site: string, params: Pick<SearchParams, "q" | "_setlocale">): Promise<TurChatResponse> {
  const qs = new URLSearchParams();
  qs.append("q", params.q);
  if (params._setlocale) qs.append("_setlocale", params._setlocale);
  return get<TurChatResponse>(`/sn/${site}/chat?${qs}`);
}

/**
 * Lightweight check whether the site has RAG (Generative AI) enabled.
 * Used by the search UI to conditionally expose the "AI Mode" toggle.
 *
 * @since 2026.2.4
 */
export function fetchChatEnabled(site: string): Promise<{ enabled: boolean }> {
  return get<{ enabled: boolean }>(`/sn/${site}/chat/enabled`);
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
    body: JSON.stringify({ messages, locale }),
    signal: options?.signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }
  if (!response.body) {
    throw new Error("Chat conversation response has no body");
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
        options?.onToken?.(parsed.content);
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

  return { role: "assistant", content: aggregated };
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
