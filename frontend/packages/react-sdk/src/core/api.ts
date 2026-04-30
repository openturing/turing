import axios from "axios";
import type { TurChatConversationMessage, TurChatResponse, TurIntent, TurSearchResponse, TurSortOption } from "./types";

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
 * Conversational RAG: sends the full message history to the site's GenAI chat.
 * The server uses the latest user message for document retrieval and preserves
 * prior turns as conversation context.
 *
 * @since 2026.2.4
 */
export async function postChatConversation(
  site: string,
  messages: TurChatConversationMessage[],
  locale?: string,
): Promise<TurChatResponse> {
  const { data } = await axios.post<TurChatResponse>(
    `/sn/${site}/chat/conversation`,
    { messages, locale },
  );
  return data;
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
