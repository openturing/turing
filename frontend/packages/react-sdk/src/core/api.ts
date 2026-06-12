import axios from "axios";
import type {
  TurChatConversationMessage,
  TurChatConversationResponse,
  TurChatForm,
  TurChatFormSubmitResponse,
  TurChatResponse,
  TurChatSource,
  TurChatStreamEvent,
  TurIntent,
  TurLlmContextInfo,
  TurLlmInstance,
  TurSearchResponse,
  TurSortOption,
} from "./types";

/**
 * Low-level Turing API client for the React SDK.
 *
 * <p>Uses the globally configured axios instance from the host app, inheriting
 * baseURL, CSRF, auth headers, and interceptors (e.g. the console's 401→login
 * redirect and backend-offline reporting). This is the one place the React SDK
 * deliberately diverges from the framework-agnostic {@code @viglet/turing-sdk}:
 * the vanilla SDK ships a `fetch`-based explicit client, while these functions
 * stay bound to axios so the host's interceptors keep applying.
 *
 * <p>All transport-agnostic request/response <b>types</b>, plus the pure
 * {@link parseHrefToParams} helper and the {@link CHAT_DISABLED_HINTS} table,
 * are owned by {@code @viglet/turing-sdk} and re-exported here so existing
 * `../core/api` imports keep working from a single source of truth.
 *
 * @since 2026.2.0 (types deduplicated into @viglet/turing-sdk in 2026.3.1)
 */

export { CHAT_DISABLED_HINTS, parseHrefToParams } from "@viglet/turing-sdk";
export type {
  SearchParams,
  ClickTrackParams,
  ChatEnabledResponse,
  ChatDisabledReason,
  PostChatConversationOptions,
  PostAgentChatOptions,
  PostLlmChatOptions,
  TurChatSessionSlots,
  TurWorkspaceArtifact,
  TurWorkspaceArtifacts,
  TurWorkspaceEvent,
  TurChatSlotWriteResponse,
  TurChatSlotExtractResponse,
  TurChatConversationState,
  TurChatHandoffChannel,
  TurChatHandoffResponse,
  TurChatFlowSelectResponse,
} from "@viglet/turing-sdk";

import type {
  SearchParams,
  ClickTrackParams,
  ChatEnabledResponse,
  PostChatConversationOptions,
  PostAgentChatOptions,
  PostLlmChatOptions,
  TurChatSessionSlots,
  TurChatSlotWriteResponse,
  TurChatSlotExtractResponse,
  TurChatConversationState,
  TurChatHandoffChannel,
  TurChatHandoffResponse,
  TurChatFlowSelectResponse,
} from "@viglet/turing-sdk";

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

/**
 * Resolves the CSRF token for the fetch-based SSE POSTs (which bypass axios'
 * built-in CSRF handling). Returns the {@code XSRF-TOKEN} cookie when present;
 * otherwise primes it with a `GET {baseURL}/csrf` and re-reads.
 *
 * @since 2026.3.1
 */
async function ensureXsrfToken(): Promise<string | undefined> {
  const existing = readXsrfCookie();
  if (existing) return existing;
  try {
    const baseURL = axios.defaults.baseURL ?? "";
    const res = await fetch(`${baseURL}/csrf`, { credentials: "include" });
    if (res.ok) {
      return res.headers.get("X-XSRF-TOKEN") ?? readXsrfCookie();
    }
  } catch {
    // /csrf unavailable (anonymous SPA, CSRF disabled, CORS) — fall through.
  }
  return readXsrfCookie();
}

/**
 * Drains a Spring `Flux<ChatResponse>` SSE stream and returns both the
 * concatenated assistant text and any chip labels emitted by the chat-flow
 * engine via a `type === "options"` event.
 */
/** Parses an `"options"` SSE event payload into chip labels, or `null`. */
function parseOptionsEvent(content: string): string[] | null {
  try {
    const parsed = JSON.parse(content) as unknown;
    if (Array.isArray(parsed)) {
      const labels = parsed.filter((s): s is string => typeof s === "string");
      if (labels.length > 0) return labels;
    }
  } catch {
    // ignore malformed payloads
  }
  return null;
}

/** Parses a `"form"` SSE event payload (T107) into a form schema, or `null`. */
function parseFormEvent(content: string): TurChatForm | null {
  try {
    const parsed = JSON.parse(content) as TurChatForm;
    if (parsed && Array.isArray(parsed.fields) && parsed.fields.length > 0) {
      return parsed;
    }
  } catch {
    // ignore malformed payloads
  }
  return null;
}

/** Parses a `"sources"` SSE event payload (T292) into RAG provenance, or `null`. */
function parseSourcesEvent(content: string): TurChatSource[] | null {
  try {
    const parsed = JSON.parse(content) as unknown;
    if (Array.isArray(parsed) && parsed.length > 0) {
      return parsed as TurChatSource[];
    }
  } catch {
    // ignore malformed payloads
  }
  return null;
}

async function consumeAssistantStream(
  response: Response,
  onToken?: (token: string) => void,
  onOptions?: (options: string[]) => void,
  onForm?: (form: TurChatForm) => void,
  onSources?: (sources: TurChatSource[]) => void,
): Promise<{ text: string; options: string[]; form?: TurChatForm; sources?: TurChatSource[] }> {
  if (!response.body) {
    throw new Error("Chat response has no body");
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let aggregated = "";
  let optionLabels: string[] = [];
  let form: TurChatForm | undefined;
  let sources: TurChatSource[] | undefined;

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
      const labels = parseOptionsEvent(parsed.content);
      if (labels) {
        optionLabels = labels;
        onOptions?.(labels);
      }
      return;
    }
    if (parsed.type === "form") {
      const parsedForm = parseFormEvent(parsed.content);
      if (parsedForm) {
        form = parsedForm;
        onForm?.(parsedForm);
      }
      return;
    }
    if (parsed.type === "sources") {
      const parsedSources = parseSourcesEvent(parsed.content);
      if (parsedSources) {
        sources = parsedSources;
        onSources?.(parsedSources);
      }
      return;
    }
    // Default to text. Treat any unknown `type` as a token event.
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
  return { text: aggregated, options: optionLabels, form, sources };
}

/**
 * T74 — best-effort browser timezone for the {@code X-Timezone} header. The
 * chat endpoints fold it into the analytics cohort (with locale from
 * {@code Accept-Language} and device class from {@code User-Agent}) so the
 * scorecard can slice variants by sub-population. Returns {@code null} outside
 * a browser / when {@code Intl} can't resolve a zone — the server then records
 * a {@code null} timezone, no behaviour change.
 *
 * @since 2026.3.1
 */
function browserTimezone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || null;
  } catch {
    return null;
  }
}

/**
 * Conversational RAG: sends the full message history to the site's GenAI
 * chat (`POST /sn/{site}/chat/conversation`) and consumes the Server-Sent
 * Events stream returned by the backend.
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
  const xsrf = await ensureXsrfToken();
  if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;
  const tz = browserTimezone();
  if (tz) headers["X-Timezone"] = tz;

  const response = await fetch(url, {
    method: "POST",
    headers,
    credentials: "include",
    body: JSON.stringify({
      messages,
      locale,
      conversationId: options?.conversationId,
      flowId: options?.flowId,
      forcedVariant: options?.forcedVariant,
    }),
    signal: options?.signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const { text, options: chipOptions, form, sources } = await consumeAssistantStream(
    response,
    options?.onToken,
    options?.onOptions,
    options?.onForm,
    options?.onSources,
  );
  return { role: "assistant", content: text, options: chipOptions, form, sources };
}

/**
 * Streams a turn through an explicit AI Agent (`POST /v2/ai-agent/{agentId}/chat`).
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
  };
  const xsrf = await ensureXsrfToken();
  if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;
  const tz = browserTimezone();
  if (tz) headers["X-Timezone"] = tz;

  const requestPayload = {
    llmInstanceId,
    messages,
    conversationId: options?.conversationId,
    flowId: options?.flowId,
    forcedVariant: options?.forcedVariant,
    selectedSkillId: options?.selectedSkillId,
  };

  let body: BodyInit;
  if (options?.files && options.files.length > 0) {
    const form = new FormData();
    form.append(
      "request",
      new Blob([JSON.stringify(requestPayload)], { type: "application/json" }),
    );
    for (const file of options.files) {
      form.append("files", file);
    }
    body = form;
    // FormData → browser sets multipart Content-Type + boundary; don't set it manually.
  } else {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(requestPayload);
  }

  const response = await fetch(url, {
    method: "POST",
    headers,
    credentials: "include",
    body,
    signal: options?.signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const { text, options: chipOptions, form, sources } = await consumeAssistantStream(
    response,
    options?.onToken,
    options?.onOptions,
    options?.onForm,
    options?.onSources,
  );
  return { role: "assistant", content: text, options: chipOptions, form, sources };
}

/**
 * Streams a turn against an explicit {@code TurLLMInstance} with no agent,
 * no site, and no chat-flow context — {@code POST /v2/llm/{id}/chat}.
 *
 * @since 2026.3.1
 */
export async function postLlmChat(
  llmInstanceId: string,
  messages: TurChatConversationMessage[],
  options?: PostLlmChatOptions,
): Promise<TurChatConversationResponse> {
  const baseURL = axios.defaults.baseURL ?? "";
  const url = `${baseURL}/v2/llm/${llmInstanceId}/chat`;

  const headers: Record<string, string> = {
    Accept: "text/event-stream",
  };
  const xsrf = await ensureXsrfToken();
  if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;

  let body: BodyInit;
  if (options?.files && options.files.length > 0) {
    const form = new FormData();
    form.append(
      "request",
      new Blob([JSON.stringify({ messages })], { type: "application/json" }),
    );
    for (const file of options.files) {
      form.append("files", file);
    }
    body = form;
    // FormData → browser sets multipart Content-Type + boundary; don't set it manually.
  } else {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify({ messages });
  }

  const response = await fetch(url, {
    method: "POST",
    headers,
    credentials: "include",
    body,
    signal: options?.signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const { text } = await consumeAssistantStream(response, options?.onToken);
  return { role: "assistant", content: text, options: [] };
}

/**
 * Drops every chat-flow runtime state for {@code conversationId} on the
 * site's agent.
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
 * endpoint.
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
 * Returns an empty array when the site has no agent or no enabled intents.
 *
 * @since 2026.2.12
 */
export function fetchIntents(site: string): Promise<TurIntent[]> {
  return get<TurIntent[]>(`/sn/${site}/chat/intents`);
}

/**
 * Reads the slots captured during the conversation from the site's RAG
 * endpoint.
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
 * Writes a single slot on the current conversation through the site's GenAI
 * endpoint.
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
 * Uploads a document and asks the server to extract values for the requested
 * slots using the agent's configured LLM.
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
 * Lightweight read of the conversation's flow state.
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
 * Builds a channel-specific handoff deep link carrying the conversation
 * context.
 *
 * Server endpoint: {@code POST /api/sn/{site}/chat/handoff}.
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

/**
 * Pins a specific chat-flow for the conversation, overriding the LLM
 * trigger router.
 *
 * Server endpoint: {@code POST /api/sn/{site}/chat/flow-select}.
 *
 * @since 2026.3.1
 */
export async function postSiteFlowSelect(
  site: string,
  conversationId: string,
  flow: string,
): Promise<TurChatFlowSelectResponse> {
  const { data } = await axios.post<TurChatFlowSelectResponse>(
    `/sn/${site}/chat/flow-select`,
    { conversationId, flow },
  );
  return (
    data ?? {
      success: false,
      pinnedFlowId: null,
      pinnedFlowName: null,
      reason: "empty response",
    }
  );
}

/**
 * T107 — submits a native multi-field {@code formCapture} form: writes every
 * `values` entry to its named slot and advances the conversation past the
 * satisfied form node.
 *
 * Server endpoint: {@code POST /api/sn/{site}/chat/form-submit}.
 *
 * @since 2026.3.1
 */
export async function postSiteFormSubmit(
  site: string,
  conversationId: string,
  values: Record<string, string>,
  nodeId?: string,
): Promise<TurChatFormSubmitResponse> {
  const { data } = await axios.post<TurChatFormSubmitResponse>(
    `/sn/${site}/chat/form-submit`,
    { conversationId, nodeId, values },
  );
  return (
    data ?? {
      success: false,
      fieldsWritten: 0,
      advancedStates: 0,
      currentNodeId: null,
      error: "empty response",
    }
  );
}

/**
 * Lists the LLM instances visible to the current session.
 *
 * Server endpoint: {@code GET /api/llm}.
 *
 * @since 2026.3.1
 */
export function fetchLlmInstances(): Promise<TurLlmInstance[]> {
  return get<TurLlmInstance[]>(`/llm`);
}

/**
 * Probes the configured context window for a given LLM instance.
 *
 * Server endpoint: {@code GET /api/v2/llm/{id}/chat/context-info}.
 *
 * @since 2026.3.1
 */
export function fetchLlmContextInfo(
  llmInstanceId: string,
): Promise<TurLlmContextInfo> {
  return get<TurLlmContextInfo>(`/v2/llm/${llmInstanceId}/chat/context-info`);
}

/**
 * Like {@link fetchLlmContextInfo} but scoped to an AI Agent.
 *
 * Server endpoint:
 * {@code GET /api/v2/ai-agent/{agentId}/chat/context-info?llmInstanceId=...}.
 *
 * @since 2026.3.1
 */
export async function fetchAgentContextInfo(
  agentId: string,
  llmInstanceId: string,
): Promise<TurLlmContextInfo> {
  const { data } = await axios.get<TurLlmContextInfo>(
    `/v2/ai-agent/${agentId}/chat/context-info`,
    { params: { llmInstanceId } },
  );
  return data;
}

export function fetchAutoComplete(site: string, params: SearchParams): Promise<string[]> {
  return get<string[]>(`/sn/${site}/ac?${buildQueryString(params)}`);
}

export function fetchSortOptions(site: string): Promise<TurSortOption[]> {
  return get<TurSortOption[]>(`/sn/${site}/search/sort-options`);
}
