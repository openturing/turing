import axios from "axios";
import type {
  TurChatCitation,
  TurChatConversationMessage,
  TurChatConversationResponse,
  TurChatForm,
  TurChatFormSubmitResponse,
  TurChatGrounding,
  TurChatResponse,
  TurChatSecondOpinion,
  TurChatSource,
  TurChatStreamEvent,
  TurChatToolCall,
  TurClientToolCall,
  TurIntent,
  TurLlmContextInfo,
  TurLlmInstance,
  TurLlmVendor,
  TurSearchResponse,
  TurSearchSuggestions,
  TurSortOption,
  TurSpellCheck,
  TurRelatedTermSuggestion,
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

export { CHAT_DISABLED_HINTS, parseHrefToParams, LOW_CONFIDENCE_THRESHOLD } from "@viglet/turing-sdk";
export type {
  SearchParams,
  ClickTrackParams,
  ChatEnabledResponse,
  ChatDisabledReason,
  PostChatConversationOptions,
  PostAgentChatOptions,
  PostPersonaChatOptions,
  PostClientToolResultOptions,
  ClientToolResultBody,
  PostLlmChatOptions,
  PostSemanticChatOptions,
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
  TurPersonaOption,
  TurPersonaPersonality,
  TurContentFit,
  TurContentFitMisfit,
  TurSimilarMode,
  TurSimilarResult,
  FetchSimilarParams,
  TurChatSlotUploadResponse,
  PostSlotUploadOptions,
  SlotExtractOptions,
  TurChatResumeResponse,
  TurDslSearchRequest,
  TurDslSearchHit,
  TurDslSearchResponse,
  TurDiscoveryInfo,
  TurFeaturesInfo,
  TurSystemLocale,
  TurSummaryResult,
} from "@viglet/turing-sdk";

import type {
  SearchParams,
  ClickTrackParams,
  ChatEnabledResponse,
  PostChatConversationOptions,
  PostAgentChatOptions,
  PostPersonaChatOptions,
  PostClientToolResultOptions,
  ClientToolResultBody,
  PostLlmChatOptions,
  PostSemanticChatOptions,
  TurChatSessionSlots,
  TurChatSlotWriteResponse,
  TurChatSlotExtractResponse,
  TurChatConversationState,
  TurChatHandoffChannel,
  TurChatHandoffResponse,
  TurChatFlowSelectResponse,
  TurContentFit,
  FetchSimilarParams,
  TurSimilarResult,
  TurChatSlotUploadResponse,
  PostSlotUploadOptions,
  SlotExtractOptions,
  TurChatResumeResponse,
  TurDslSearchRequest,
  TurDslSearchResponse,
  TurDiscoveryInfo,
  TurFeaturesInfo,
  TurSystemLocale,
  TurSummaryResult,
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

/** Parses a `"grounding"` SSE event payload (T516) into a guardrail verdict, or `null`. */
function parseGroundingEvent(content: string): TurChatGrounding | null {
  try {
    const parsed = JSON.parse(content) as TurChatGrounding;
    if (parsed && typeof parsed.action === "string") {
      return parsed;
    }
  } catch {
    // ignore malformed payloads
  }
  return null;
}

/** Parses a `"secondOpinion"` SSE event payload (T522) into a verdict, or `null`. */
function parseSecondOpinionEvent(content: string): TurChatSecondOpinion | null {
  try {
    const parsed = JSON.parse(content) as TurChatSecondOpinion;
    if (parsed && typeof parsed.agree === "boolean") {
      return parsed;
    }
  } catch {
    // ignore malformed payloads
  }
  return null;
}

/** Parses a `"citations"` SSE event payload (T152) into per-sentence citations, or `null`. */
function parseCitationsEvent(content: string): TurChatCitation[] | null {
  try {
    const parsed = JSON.parse(content) as unknown;
    if (Array.isArray(parsed) && parsed.length > 0) {
      return parsed as TurChatCitation[];
    }
  } catch {
    // ignore malformed payloads
  }
  return null;
}

/** Parses a `"searchSuggestions"` SSE event payload (T490) into the Gemini chips, or `null`. */
function parseSearchSuggestionsEvent(content: string): TurSearchSuggestions | null {
  try {
    const parsed = JSON.parse(content) as TurSearchSuggestions;
    if (parsed && (parsed.renderedContent || (parsed.queries?.length ?? 0) > 0)) {
      return parsed;
    }
  } catch {
    // ignore malformed payloads
  }
  return null;
}

/** Parses a `"tool_call"` SSE event payload (T436) into a tool-call lifecycle event, or `null`. */
function parseToolCallEvent(content: string): TurChatToolCall | null {
  try {
    const parsed = JSON.parse(content) as TurChatToolCall;
    if (parsed && typeof parsed.callId === "string" && typeof parsed.name === "string") {
      return parsed;
    }
  } catch {
    // ignore malformed payloads
  }
  return null;
}

/**
 * Upserts a tool-call lifecycle event into the accumulated list by `callId`
 * (T436): a `start` appends; a matching `end` merges status/duration onto it
 * (keeping the `start`'s `argsSummary`). Returns a new array.
 */
function mergeToolCall(existing: TurChatToolCall[], event: TurChatToolCall): TurChatToolCall[] {
  const idx = existing.findIndex((c) => c.callId === event.callId);
  if (idx === -1) return [...existing, event];
  const next = [...existing];
  const prev = next[idx];
  next[idx] = { ...prev, ...event, argsSummary: event.argsSummary ?? prev.argsSummary };
  return next;
}

/** Parses a `"client_tool_call"` SSE event payload (T438), or `null`. */
function parseClientToolCallEvent(content: string): TurClientToolCall | null {
  try {
    const parsed = JSON.parse(content) as TurClientToolCall;
    if (parsed && typeof parsed.callId === "string" && typeof parsed.name === "string") {
      return { callId: parsed.callId, name: parsed.name, args: parsed.args ?? "" };
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
  onCitations?: (citations: TurChatCitation[]) => void,
  onToolCall?: (call: TurChatToolCall, all: TurChatToolCall[]) => void,
  onReasoning?: (reasoning: string) => void,
  onGrounding?: (grounding: TurChatGrounding) => void,
  onSecondOpinion?: (secondOpinion: TurChatSecondOpinion) => void,
): Promise<{
  text: string;
  options: string[];
  form?: TurChatForm;
  sources?: TurChatSource[];
  citations?: TurChatCitation[];
  searchSuggestions?: TurSearchSuggestions;
  reasoning?: string;
  toolCalls?: TurChatToolCall[];
  clientToolCall?: TurClientToolCall;
  grounding?: TurChatGrounding;
  secondOpinion?: TurChatSecondOpinion;
}> {
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
  let citations: TurChatCitation[] | undefined;
  let searchSuggestions: TurSearchSuggestions | undefined;
  let reasoning: string | undefined;
  let toolCalls: TurChatToolCall[] = [];
  let clientToolCall: TurClientToolCall | undefined;
  let grounding: TurChatGrounding | undefined;
  let secondOpinion: TurChatSecondOpinion | undefined;

  /** Handle a non-token structured event; returns true when it was one. */
  function handleStructuredEvent(parsed: TurChatStreamEvent): boolean {
    if (parsed.type === "options") {
      const labels = parseOptionsEvent(parsed.content);
      if (labels) {
        optionLabels = labels;
        onOptions?.(labels);
      }
      return true;
    }
    if (parsed.type === "form") {
      const parsedForm = parseFormEvent(parsed.content);
      if (parsedForm) {
        form = parsedForm;
        onForm?.(parsedForm);
      }
      return true;
    }
    if (parsed.type === "sources") {
      const parsedSources = parseSourcesEvent(parsed.content);
      if (parsedSources) {
        sources = parsedSources;
        onSources?.(parsedSources);
      }
      return true;
    }
    if (parsed.type === "citations") {
      const parsedCitations = parseCitationsEvent(parsed.content);
      if (parsedCitations) {
        citations = parsedCitations;
        onCitations?.(parsedCitations);
      }
      return true;
    }
    if (parsed.type === "searchSuggestions") {
      const parsedSuggestions = parseSearchSuggestionsEvent(parsed.content);
      if (parsedSuggestions) {
        searchSuggestions = parsedSuggestions;
      }
      return true;
    }
    if (parsed.type === "reasoning") {
      // T178 — the reasoning summary is plain text (one or more parts joined
      // server-side); accumulate in case the backend emits more than one event.
      if (parsed.content) {
        reasoning = (reasoning ?? "") + parsed.content;
        onReasoning?.(reasoning);
      }
      return true;
    }
    if (parsed.type === "tool_call") {
      const parsedCall = parseToolCallEvent(parsed.content);
      if (parsedCall) {
        toolCalls = mergeToolCall(toolCalls, parsedCall);
        onToolCall?.(parsedCall, toolCalls);
      }
      return true;
    }
    if (parsed.type === "client_tool_call") {
      const parsedClientCall = parseClientToolCallEvent(parsed.content);
      if (parsedClientCall) {
        clientToolCall = parsedClientCall;
      }
      return true;
    }
    if (parsed.type === "grounding") {
      const parsedGrounding = parseGroundingEvent(parsed.content);
      if (parsedGrounding) {
        grounding = parsedGrounding;
        onGrounding?.(parsedGrounding);
      }
      return true;
    }
    if (parsed.type === "secondOpinion") {
      const parsedSecondOpinion = parseSecondOpinionEvent(parsed.content);
      if (parsedSecondOpinion) {
        secondOpinion = parsedSecondOpinion;
        onSecondOpinion?.(parsedSecondOpinion);
      }
      return true;
    }
    return false;
  }

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
    if (handleStructuredEvent(parsed)) return;
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
  return {
    text: aggregated,
    options: optionLabels,
    form,
    sources,
    citations,
    searchSuggestions,
    reasoning,
    toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
    clientToolCall,
    grounding,
    secondOpinion,
  };
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
      personaId: options?.personaId,
    }),
    signal: options?.signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const {
    text,
    options: chipOptions,
    form,
    sources,
    citations,
    searchSuggestions,
    reasoning,
    toolCalls,
    clientToolCall,
    grounding,
    secondOpinion,
  } = await consumeAssistantStream(
    response,
    options?.onToken,
    options?.onOptions,
    options?.onForm,
    options?.onSources,
    options?.onCitations,
    options?.onToolCall,
    options?.onReasoning,
    options?.onGrounding,
    options?.onSecondOpinion,
  );
  return {
    role: "assistant",
    content: text,
    options: chipOptions,
    form,
    sources,
    citations,
    searchSuggestions,
    reasoning,
    toolCalls,
    clientToolCall,
    grounding,
    secondOpinion,
  };
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

  const {
    text,
    options: chipOptions,
    form,
    sources,
    citations,
    searchSuggestions,
    reasoning,
    toolCalls,
    clientToolCall,
    grounding,
    secondOpinion,
  } = await consumeAssistantStream(
    response,
    options?.onToken,
    options?.onOptions,
    options?.onForm,
    options?.onSources,
    options?.onCitations,
    options?.onToolCall,
    options?.onReasoning,
    options?.onGrounding,
    options?.onSecondOpinion,
  );
  return {
    role: "assistant",
    content: text,
    options: chipOptions,
    form,
    sources,
    citations,
    searchSuggestions,
    reasoning,
    toolCalls,
    clientToolCall,
    grounding,
    secondOpinion,
  };
}

/**
 * Block AI / §XXXII.2 (T579) — streams a turn through a persona
 * (`POST /v2/persona/{personaId}/chat`). Mirrors {@link postAgentChat}; only the
 * URL and the (leaner, stateless) request payload differ.
 *
 * @since 2026.3.4
 */
export async function postPersonaChat(
  personaId: string,
  llmInstanceId: string,
  messages: TurChatConversationMessage[],
  options?: PostPersonaChatOptions,
): Promise<TurChatConversationResponse> {
  const baseURL = axios.defaults.baseURL ?? "";
  const url = `${baseURL}/v2/persona/${personaId}/chat`;

  const headers: Record<string, string> = {
    Accept: "text/event-stream",
  };
  const xsrf = await ensureXsrfToken();
  if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;
  const tz = browserTimezone();
  if (tz) headers["X-Timezone"] = tz;

  const requestPayload = { llmInstanceId, messages };

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

  const {
    text,
    options: chipOptions,
    form,
    sources,
    citations,
    searchSuggestions,
    reasoning,
    toolCalls,
    clientToolCall,
    grounding,
    secondOpinion,
  } = await consumeAssistantStream(
    response,
    options?.onToken,
    options?.onOptions,
    options?.onForm,
    options?.onSources,
    options?.onCitations,
    options?.onToolCall,
    options?.onReasoning,
    options?.onGrounding,
    options?.onSecondOpinion,
  );
  return {
    role: "assistant",
    content: text,
    options: chipOptions,
    form,
    sources,
    citations,
    searchSuggestions,
    reasoning,
    toolCalls,
    clientToolCall,
    grounding,
    secondOpinion,
  };
}

/**
 * T438 — resumes a turn parked on a frontend ("client") tool by POSTing the
 * browser's result to `POST /v2/chat/client-tool-result` and consuming the
 * continuation SSE. Same response shape as a chat turn, so the continuation may
 * itself park again on another client tool (chained round-trips).
 *
 * @since 2026.3.4
 */
export async function postClientToolResult(
  body: ClientToolResultBody,
  options?: PostClientToolResultOptions,
): Promise<TurChatConversationResponse> {
  const baseURL = axios.defaults.baseURL ?? "";
  const url = `${baseURL}/v2/chat/client-tool-result`;

  const headers: Record<string, string> = {
    Accept: "text/event-stream",
    "Content-Type": "application/json",
  };
  const xsrf = await ensureXsrfToken();
  if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;

  const response = await fetch(url, {
    method: "POST",
    headers,
    credentials: "include",
    body: JSON.stringify(body),
    signal: options?.signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const {
    text,
    options: chipOptions,
    form,
    sources,
    citations,
    searchSuggestions,
    reasoning,
    toolCalls,
    clientToolCall,
    grounding,
    secondOpinion,
  } = await consumeAssistantStream(
    response,
    options?.onToken,
    options?.onOptions,
    options?.onForm,
    options?.onSources,
    options?.onCitations,
    options?.onToolCall,
    options?.onReasoning,
    options?.onGrounding,
    options?.onSecondOpinion,
  );
  return {
    role: "assistant",
    content: text,
    options: chipOptions,
    form,
    sources,
    citations,
    searchSuggestions,
    reasoning,
    toolCalls,
    clientToolCall,
    grounding,
    secondOpinion,
  };
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
 * T404 — streams a turn against an explicit {@code TurLLMInstance} running the
 * Semantic Navigation tool set (list_sites / get_site_fields / search_site /
 * catalog_search + any MCP tools) — "talk to the index" without configuring a
 * full AI Agent. Server endpoint: {@code POST /v2/llm/{id}/semantic-chat}.
 *
 * <p>JSON-only (no multipart): the model reaches content through tool calls,
 * not file uploads. The SSE envelope matches {@link postLlmChat}, so {@link
 * consumeAssistantStream} parses it unchanged.
 *
 * @since 2026.3.1
 */
export async function postSemanticChat(
  llmInstanceId: string,
  messages: TurChatConversationMessage[],
  options?: PostSemanticChatOptions,
): Promise<TurChatConversationResponse> {
  const baseURL = axios.defaults.baseURL ?? "";
  const url = `${baseURL}/v2/llm/${llmInstanceId}/semantic-chat`;

  const headers: Record<string, string> = {
    Accept: "text/event-stream",
    "Content-Type": "application/json",
  };
  const xsrf = await ensureXsrfToken();
  if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;

  const response = await fetch(url, {
    method: "POST",
    headers,
    credentials: "include",
    body: JSON.stringify({ messages }),
    signal: options?.signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const { text } = await consumeAssistantStream(response, options?.onToken);
  return { role: "assistant", content: text, options: [] };
}

/**
 * T792 / §LIV.3 (Block BF) — one cited catalog result from the Vectorless
 * (Structured-Data) RAG copilot. Wire shape of the backend
 * {@code TurCatalogCitation}: {@code rank} is the 1-based position the answer
 * text cites as {@code [n]}, {@code url} links the source when present.
 */
export interface TurCatalogCitation {
  readonly rank: number;
  readonly id: string;
  readonly title?: string | null;
  readonly url?: string | null;
  readonly score?: number | null;
  readonly rankingExplanation?: Record<string, unknown> | null;
}

/**
 * T792 / §LIV.3 (Block BF) — response of {@code POST /api/sn/{site}/copilot}
 * (the backend {@code TurCatalogCopilotResult}): a grounded {@code answer} plus
 * the {@code citations} it is built from. {@code available} is {@code false}
 * (with {@code error}) when the copilot cannot run (no default LLM). Even when
 * the answer step fails, {@code citations} may be populated so the client can
 * still render the matched results.
 */
export interface TurCatalogCopilotResult {
  readonly available: boolean;
  readonly answer?: string | null;
  readonly citations: TurCatalogCitation[];
  readonly groundedQuerySummary?: string | null;
  readonly totalHits: number;
  readonly error?: string | null;
}

/**
 * T792 / §LIV.3 (Block BF) — answer a (possibly multi-turn) catalog conversation
 * grounded in the site's index via the vectorless copilot. Non-streaming JSON
 * POST; CSRF + baseURL are inherited from the host's axios instance.
 */
export async function postCopilot(
  site: string,
  messages: TurChatConversationMessage[],
  locale?: string,
): Promise<TurCatalogCopilotResult> {
  const { data } = await axios.post<TurCatalogCopilotResult>(
    `/sn/${site}/copilot`,
    { messages, locale },
  );
  return data;
}

/**
 * T792 / §LIV.3 (Block BF) — whether the vectorless copilot can answer for this
 * site (a default LLM is configured). Backs the widget's readiness gate.
 */
export async function fetchCopilotAvailable(site: string): Promise<boolean> {
  const { data } = await axios.get<{ available?: boolean }>(
    `/sn/${site}/copilot/available`,
  );
  return Boolean(data?.available);
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
 * Uploads a document (or an image of a document — PNG / JPEG / GIF / WebP, e.g.
 * a phone-photo of a CV) and asks the server to extract values for the requested
 * slots using the agent's configured LLM. Image uploads are routed to the
 * agent's vision model server-side; pass the image File unchanged.
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
  options?: SlotExtractOptions,
): Promise<TurChatSlotExtractResponse> {
  const form = new FormData();
  form.append("file", file);
  form.append("conversationId", conversationId);
  if (slotNames && slotNames.length > 0) {
    form.append("slotNames", slotNames.join(","));
  }
  if (options?.confidence) {
    form.append("confidence", "true");
  }
  if (options?.nativeBinary) {
    form.append("nativeBinary", "true");
  }
  const { data } = await axios.post<TurChatSlotExtractResponse>(
    `/sn/${site}/chat/slot-extract`,
    form,
  );
  return data ?? { extracted: {}, slotsWritten: 0, extractedTextChars: 0 };
}

/**
 * Uploads several documents at once and asks the server to reason ACROSS them
 * to fill the requested slots — e.g. a CV + a job description producing a
 * synthesised `skill_gap` slot. Text and image documents may be mixed. A single
 * file behaves exactly like {@link postSiteSlotExtract}.
 *
 * Server endpoint: {@code POST /api/sn/{site}/chat/slot-extract-multi}.
 *
 * @since 2026.3.1
 */
export async function postSiteSlotExtractMulti(
  site: string,
  files: ReadonlyArray<File>,
  conversationId: string,
  slotNames?: ReadonlyArray<string>,
  options?: SlotExtractOptions,
): Promise<TurChatSlotExtractResponse> {
  const form = new FormData();
  for (const file of files) {
    form.append("files", file);
  }
  form.append("conversationId", conversationId);
  if (slotNames && slotNames.length > 0) {
    form.append("slotNames", slotNames.join(","));
  }
  if (options?.confidence) {
    form.append("confidence", "true");
  }
  if (options?.nativeBinary) {
    form.append("nativeBinary", "true");
  }
  const { data } = await axios.post<TurChatSlotExtractResponse>(
    `/sn/${site}/chat/slot-extract-multi`,
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
  personaId?: string,
): Promise<TurChatFlowSelectResponse> {
  const { data } = await axios.post<TurChatFlowSelectResponse>(
    `/sn/${site}/chat/flow-select`,
    // T635 — an optional persona is seeded into the pinned flow (validated
    // against the site agent's catalog server-side).
    { conversationId, flow, personaId },
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
 * T635 / §XXVII.4 — "validate this content as persona X". Evaluates arbitrary
 * text against an audience persona from the site agent's catalog and returns a
 * structured content-fit verdict (fit %, what does/doesn't fit, flagged spans +
 * rewrites). The text is hard-capped (12 000 chars → 413) and rate-limited on
 * the demo host — it's an LLM-cost surface.
 *
 * Server endpoint: {@code POST /api/sn/{site}/persona/{personaId}/content-fit}.
 *
 * @since 2026.3.4
 */
export async function fetchPersonaContentFit(
  site: string,
  personaId: string,
  content: string,
  sourceName?: string,
): Promise<TurContentFit> {
  const { data } = await axios.post<TurContentFit>(
    `/sn/${site}/persona/${personaId}/content-fit`,
    { content, sourceName },
  );
  if (!data) {
    throw new Error("Empty content-fit response");
  }
  return data;
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

/**
 * T148 / §X.6.b — whether real-time voice is available for an agent and, when it
 * is, the resolved model/voice. Drives the SDK's decision to render a voice
 * button. Server: {@code GET /api/v2/ai-agent/{agentId}/voice/available}.
 *
 * @since 2026.3.4
 */
export interface TurVoiceAvailability {
  available: boolean;
  model?: string | null;
  voice?: string | null;
  /** When unavailable: a stable reason code (e.g. "capability-disabled", "vendor-unsupported"). */
  reason?: string | null;
}

/**
 * T148 / §X.6.b — a minted ephemeral real-time voice session. Everything the
 * browser needs to open the WebSocket transport to the vendor's realtime
 * endpoint directly. The {@link clientSecret} is short-lived and scoped to one
 * session; the account API key never reaches the browser.
 */
export interface TurVoiceSession {
  provider: string;
  clientSecret: string;
  expiresAt: number;
  model: string;
  voice: string;
  wsUrl: string;
}

/** Optional hints sent when minting a voice session; all may be omitted. */
export interface PostVoiceSessionOptions {
  llmInstanceId?: string;
  model?: string;
  voice?: string;
  locale?: string;
}

/** T148 — probe whether voice is available for an agent. */
export async function fetchVoiceAvailability(
  agentId: string,
  llmInstanceId?: string,
): Promise<TurVoiceAvailability> {
  const { data } = await axios.get<TurVoiceAvailability>(
    `/v2/ai-agent/${agentId}/voice/available`,
    { params: llmInstanceId ? { llmInstanceId } : {} },
  );
  return data;
}

/** T148 — mint an ephemeral real-time voice session for an agent. */
export async function postVoiceSession(
  agentId: string,
  options?: PostVoiceSessionOptions,
): Promise<TurVoiceSession> {
  const baseURL = axios.defaults.baseURL ?? "";
  const url = `${baseURL}/v2/ai-agent/${agentId}/voice/session`;
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const xsrf = await ensureXsrfToken();
  if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;
  const response = await fetch(url, {
    method: "POST",
    headers,
    credentials: "include",
    body: JSON.stringify(options ?? {}),
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }
  return (await response.json()) as TurVoiceSession;
}

/**
 * T150 / §X.6.d — translate one transcript segment into the operator's language
 * for the spectator view. Server: {@code POST /api/v2/ai-agent/{agentId}/voice/translate}.
 *
 * @since 2026.3.4
 */
export async function postVoiceTranslation(
  agentId: string,
  text: string,
  targetLang: string,
  sourceLang?: string,
): Promise<string> {
  const baseURL = axios.defaults.baseURL ?? "";
  const url = `${baseURL}/v2/ai-agent/${agentId}/voice/translate`;
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const xsrf = await ensureXsrfToken();
  if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;
  const response = await fetch(url, {
    method: "POST",
    headers,
    credentials: "include",
    body: JSON.stringify({ text, targetLang, sourceLang }),
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }
  const data = (await response.json()) as { translation?: string };
  return data.translation ?? "";
}

export function fetchAutoComplete(site: string, params: SearchParams): Promise<string[]> {
  return get<string[]>(`/sn/${site}/ac?${buildQueryString(params)}`);
}

export function fetchSortOptions(site: string): Promise<TurSortOption[]> {
  return get<TurSortOption[]>(`/sn/${site}/search/sort-options`);
}

/**
 * T400 — fetches documents similar to a seed document (T384's
 * {@code GET /sn/{site}/search/similar}). Powers a "related" / "you may also
 * like" rail with one call.
 *
 * @since 2026.3.4
 */
export function fetchSimilar(
  site: string,
  params: FetchSimilarParams,
): Promise<TurSimilarResult[]> {
  const qs = new URLSearchParams({ id: params.id });
  if (params.rows != null) qs.append("rows", String(params.rows));
  if (params.locale) qs.append("locale", params.locale);
  if (params.mode) qs.append("mode", params.mode);
  return get<TurSimilarResult[]>(`/sn/${site}/search/similar?${qs}`);
}

/**
 * T401 — uploads an IMAGE / AUDIO / FILE to a multimodal slot (T64's
 * {@code POST /sn/{site}/chat/slot-upload}). With {@code vision} the server also
 * runs a vision LLM to extract scalar values into the named slots.
 *
 * @since 2026.3.4
 */
export async function postSiteSlotUpload(
  site: string,
  conversationId: string,
  slotName: string,
  file: File,
  options?: PostSlotUploadOptions,
): Promise<TurChatSlotUploadResponse> {
  const form = new FormData();
  form.append("file", file);
  form.append("conversationId", conversationId);
  form.append("slotName", slotName);
  if (options?.vision) form.append("vision", "true");
  if (options?.visionSlotNames && options.visionSlotNames.length > 0) {
    form.append("visionSlotNames", options.visionSlotNames.join(","));
  }
  const { data } = await axios.post<TurChatSlotUploadResponse>(
    `/sn/${site}/chat/slot-upload`,
    form,
  );
  return (
    data ?? {
      slotName,
      slotType: "",
      objectName: "",
      url: "",
      contentType: "",
      size: 0,
      visionExtracted: {},
      visionSlotsWritten: 0,
      error: "empty response",
    }
  );
}

/**
 * T401 — resumes a conversation parked at a suspend node
 * ({@code POST /sn/{site}/chat/resume}), optionally applying {@code slotUpdates}
 * before advancing past the suspend point.
 *
 * @since 2026.3.4
 */
export async function postSiteChatResume(
  site: string,
  conversationId: string,
  slotUpdates?: Readonly<Record<string, string>>,
  resumeReason?: string,
): Promise<TurChatResumeResponse> {
  const { data } = await axios.post<TurChatResumeResponse>(
    `/sn/${site}/chat/resume`,
    { conversationId, slotUpdates, resumeReason },
  );
  return data ?? { resumed: 0, wasParked: false, error: "empty response" };
}

/**
 * T402 — fetches "did you mean" corrections for a query
 * ({@code GET /sn/{site}/{locale}/spell-check?q=...}). Returns the same
 * {@link TurSpellCheck} shape the search bean embeds.
 *
 * @since 2026.3.4
 */
export function fetchSpellCheck(
  site: string,
  locale: string,
  q: string,
): Promise<TurSpellCheck> {
  const qs = new URLSearchParams({ q });
  return get<TurSpellCheck>(`/sn/${site}/${locale}/spell-check?${qs}`);
}

/**
 * T678 — fetches controlled-vocabulary "related concepts" for a query
 * ({@code GET /sn/{site}/{locale}/related-terms?q=...}). For each thesaurus term
 * recognised in the query it returns the labels of its {@code RELATED} (RT)
 * neighbours, a sibling suggestion surface to the spell-check "did you mean".
 *
 * @since 2026.3.4
 */
export function fetchRelatedTerms(
  site: string,
  locale: string,
  q: string,
): Promise<TurRelatedTermSuggestion[]> {
  const qs = new URLSearchParams({ q });
  return get<TurRelatedTermSuggestion[]>(`/sn/${site}/${locale}/related-terms?${qs}`);
}

/**
 * T403 — runs a structured Elasticsearch-compatible query against a site
 * ({@code POST /sn/{site}/_search}). For power users building bool/filter/aggs
 * queries from JS without the facet-param layer.
 *
 * @since 2026.3.4
 */
export async function dslSearch(
  site: string,
  request: TurDslSearchRequest,
  locale = "en",
): Promise<TurDslSearchResponse> {
  const { data } = await axios.post<TurDslSearchResponse>(
    `/sn/${site}/_search`,
    request,
    { params: { locale } },
  );
  return data;
}

/**
 * T403 — runs a raw Solr-style DSL query ({@code POST /sn/{site}/query}) and
 * returns the parsed JSON verbatim. Lower-level than {@link dslSearch}.
 *
 * @since 2026.3.4
 */
export async function dslQuery(
  site: string,
  query: Record<string, unknown> | string,
  locale: string,
): Promise<unknown> {
  const body = typeof query === "string" ? query : JSON.stringify(query);
  const { data } = await axios.post<unknown>(`/sn/${site}/query`, body, {
    params: { locale },
    headers: { "Content-Type": "application/json" },
  });
  return data;
}

/**
 * T405 — platform identity / auth topology. Server: {@code GET /api/discovery}.
 *
 * @since 2026.3.4
 */
export function fetchDiscovery(): Promise<TurDiscoveryInfo> {
  return get<TurDiscoveryInfo>(`/discovery`);
}

/**
 * T405 — enabled platform capabilities. Server: {@code GET /api/features}.
 *
 * @since 2026.3.4
 */
export function fetchFeatures(): Promise<TurFeaturesInfo> {
  return get<TurFeaturesInfo>(`/features`);
}

/**
 * T405 — supported system locales. Server: {@code GET /api/locale}.
 *
 * @since 2026.3.4
 */
export function fetchSystemLocales(): Promise<TurSystemLocale[]> {
  return get<TurSystemLocale[]>(`/locale`);
}

/**
 * T405 — configured LLM vendors. Server: {@code GET /api/llm/vendor}.
 *
 * @since 2026.3.4
 */
export function fetchLlmVendors(): Promise<TurLlmVendor[]> {
  return get<TurLlmVendor[]>(`/llm/vendor`);
}

/**
 * T405 — generates (or returns a cached) LLM summary for arbitrary data
 * ({@code POST /api/v2/summary}). {@code regenerate} bypasses the cache.
 *
 * @since 2026.3.4
 */
export async function postSummary(params: {
  readonly cacheKey: string;
  readonly data: string;
  readonly systemPrompt?: string;
  readonly regenerate?: boolean;
}): Promise<TurSummaryResult> {
  const { data } = await axios.post<TurSummaryResult>(
    `/v2/summary`,
    {
      cacheKey: params.cacheKey,
      data: params.data,
      systemPrompt: params.systemPrompt,
    },
    params.regenerate ? { params: { regenerate: true } } : undefined,
  );
  return data ?? { success: false, error: "empty response", content: "", canRegenerate: false };
}
