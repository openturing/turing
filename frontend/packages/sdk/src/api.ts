import type { TuringClient } from "./client";
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
  TurContentFit,
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
 * Low-level Turing API client functions for the vanilla SDK. Every function
 * takes a {@link TuringClient} as its first argument (the React SDK instead
 * delegated to a global axios instance). Controllers and consumers can also
 * use {@link createTuringApi} to get a `client`-bound object of these methods.
 *
 * <p>Ported from the React SDK's `core/api.ts`: axios `get/post/delete` calls
 * become `client.request`, and the fetch-based SSE helpers (already fetch in
 * the React SDK) read {@code client.baseURL} and {@code client.ensureCsrfToken()}
 * instead of `axios.defaults`.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

/**
 * T74 — best-effort browser timezone header. The chat endpoints read
 * {@code X-Timezone} into the analytics cohort (alongside locale from
 * {@code Accept-Language} and device class from {@code User-Agent}) so the
 * scorecard can slice variants by sub-population. Returns an empty object
 * outside a browser / when {@code Intl} can't resolve a zone, so the request
 * is unchanged and the server simply records a {@code null} timezone.
 */
function browserTimezoneHeaders(): Record<string, string> {
  try {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
    return tz ? { "X-Timezone": tz } : {};
  } catch {
    return {};
  }
}

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

export function fetchSearch(
  client: TuringClient,
  site: string,
  params: SearchParams,
): Promise<TurSearchResponse> {
  return client.get<TurSearchResponse>(`/sn/${site}/search?${buildQueryString(params)}`);
}

/**
 * Payload for {@link postClick}. The minimum needed to reconstruct CTR is the
 * query that produced the result (`term`) and the document the user opened
 * (`documentId`). `position` is the 1-based rank within the page.
 */
export interface ClickTrackParams {
  readonly term: string;
  readonly documentId: string;
  readonly position: number;
  readonly userId?: string;
  readonly locale?: string;
}

/**
 * Records a click-through on a search result. Fire-and-forget by design —
 * errors are swallowed and surfaced via {@code console.warn}.
 *
 * Server endpoint: {@code POST /api/sn/{site}/click}.
 */
export async function postClick(
  client: TuringClient,
  site: string,
  params: ClickTrackParams,
): Promise<void> {
  try {
    await client.post(`/sn/${site}/click`, params);
  } catch (error) {
    if (typeof console !== "undefined") {
      console.warn("[Turing] click tracking failed", error);
    }
  }
}

export function fetchChat(
  client: TuringClient,
  site: string,
  params: Pick<SearchParams, "q" | "_setlocale">,
): Promise<TurChatResponse> {
  const qs = new URLSearchParams();
  qs.append("q", params.q);
  if (params._setlocale) qs.append("_setlocale", params._setlocale);
  return client.get<TurChatResponse>(`/sn/${site}/chat?${qs}`);
}

/**
 * Why the chat endpoint is disabled for a site. Mirror of the server-side
 * enum in {@code TurSNSiteGenAiAPI.ChatDisabledReason}.
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

export interface ChatEnabledResponse {
  readonly enabled: boolean;
  readonly reason?: ChatDisabledReason;
  readonly sessionCookieName?: string;
  readonly sessionTtlSeconds?: number;
}

/**
 * Human-readable hint for each {@link ChatDisabledReason} value. Localized to
 * PT-BR by default (the diagnostic is admin-facing).
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
 *
 * Server endpoint: {@code GET /api/sn/{site}/chat/enabled}.
 */
export function fetchChatEnabled(
  client: TuringClient,
  site: string,
): Promise<ChatEnabledResponse> {
  return client.get<ChatEnabledResponse>(`/sn/${site}/chat/enabled`);
}

export interface PostChatConversationOptions {
  readonly onToken?: (token: string) => void;
  readonly onOptions?: (options: string[]) => void;
  /** Fired when the engine emits a native multi-field form schema (T107). */
  readonly onForm?: (form: TurChatForm) => void;
  /** Fired when the engine emits RAG provenance for the answer (T292). */
  readonly onSources?: (sources: TurChatSource[]) => void;
  /** Fired when the engine emits per-sentence Anthropic citations (T152). */
  readonly onCitations?: (citations: TurChatCitation[]) => void;
  /** Fired when the answer-grounding guardrail flags the answer (T516). */
  readonly onGrounding?: (grounding: TurChatGrounding) => void;
  /** Fired when the cross-vendor second-opinion check returns a verdict (T522). */
  readonly onSecondOpinion?: (secondOpinion: TurChatSecondOpinion) => void;
  /**
   * Fired on each live tool-call lifecycle event (T436): `call` is the just-
   * received `start`/`end`, `all` is the merged-by-`callId` list so far.
   */
  readonly onToolCall?: (call: TurChatToolCall, all: TurChatToolCall[]) => void;
  /** Fired when an OpenAI reasoning model returns its "Why this answer" summary (T178). */
  readonly onReasoning?: (reasoning: string) => void;
  readonly signal?: AbortSignal;
  readonly conversationId?: string;
  readonly flowId?: string;
  /**
   * Forced A/B variant label (T73) — when set, the engine hard-pins this
   * experiment arm regardless of the deterministic hash / bandit / schedule
   * window. Typically sourced from the `?_ab_variant=<label>` URL param for
   * QA + sales demos. Blank/undefined → normal sticky assignment.
   */
  readonly forcedVariant?: string;
  /**
   * T635 — per-request persona ("same question, different eyes"). Validated
   * against the site agent's catalog server-side (unknown → default), so any
   * value here is safe. Blank/undefined → the agent's default voice.
   */
  readonly personaId?: string;
}

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
 * (T436): a `start` appends a new entry; a matching `end` merges status/duration
 * onto it (keeping the `start`'s `argsSummary`). Returns a new array so callers
 * can treat it immutably.
 */
function mergeToolCall(existing: TurChatToolCall[], event: TurChatToolCall): TurChatToolCall[] {
  const idx = existing.findIndex((c) => c.callId === event.callId);
  if (idx === -1) return [...existing, event];
  const next = [...existing];
  const prev = next[idx];
  next[idx] = {
    ...prev,
    ...event,
    // an `end` event carries no args — keep the digest captured at `start`.
    argsSummary: event.argsSummary ?? prev.argsSummary,
  };
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

/**
 * Drains a Spring `Flux<ChatResponse>` SSE stream and returns the
 * concatenated assistant text, any chip labels (`type === "options"`), any
 * native multi-field form schema (`type === "form"`, T107), and any RAG
 * provenance (`type === "sources"`, T292) emitted by the chat pipeline.
 */
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
    // Default to text — treat unknown/absent `type` as a token event.
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
 * Conversational RAG: sends the full message history to the site's GenAI chat
 * (`POST /sn/{site}/chat/conversation`) and consumes the SSE stream.
 */
export async function postChatConversation(
  client: TuringClient,
  site: string,
  messages: TurChatConversationMessage[],
  locale?: string,
  options?: PostChatConversationOptions,
): Promise<TurChatConversationResponse> {
  const response = await client.fetchRaw(`/sn/${site}/chat/conversation`, {
    method: "POST",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
      ...browserTimezoneHeaders(),
    },
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

export interface PostAgentChatOptions {
  readonly onToken?: (token: string) => void;
  readonly onOptions?: (options: string[]) => void;
  /** Fired when the engine emits a native multi-field form schema (T107). */
  readonly onForm?: (form: TurChatForm) => void;
  /** Fired when the engine emits RAG provenance for the answer (T292). */
  readonly onSources?: (sources: TurChatSource[]) => void;
  /** Fired when the engine emits per-sentence Anthropic citations (T152). */
  readonly onCitations?: (citations: TurChatCitation[]) => void;
  /** Fired when the answer-grounding guardrail flags the answer (T516). */
  readonly onGrounding?: (grounding: TurChatGrounding) => void;
  /** Fired when the cross-vendor second-opinion check returns a verdict (T522). */
  readonly onSecondOpinion?: (secondOpinion: TurChatSecondOpinion) => void;
  /**
   * Fired on each live tool-call lifecycle event (T436): `call` is the just-
   * received `start`/`end`, `all` is the merged-by-`callId` list so far.
   */
  readonly onToolCall?: (call: TurChatToolCall, all: TurChatToolCall[]) => void;
  /** Fired when an OpenAI reasoning model returns its "Why this answer" summary (T178). */
  readonly onReasoning?: (reasoning: string) => void;
  readonly signal?: AbortSignal;
  readonly conversationId?: string;
  readonly flowId?: string;
  /**
   * Forced A/B variant label (T73) — hard-pins this experiment arm for QA +
   * sales demos, bypassing the deterministic hash / bandit / schedule window.
   * Typically sourced from the `?_ab_variant=<label>` URL param.
   */
  readonly forcedVariant?: string;
  /**
   * Skill-mode pin (T325) — selects a single skill (by id or name) for this
   * turn so the agent operates that one skill as a distinct "mode/flow",
   * instead of offering the full enabled skill set for progressive disclosure.
   * Blank/undefined keeps the legacy all-skills behaviour.
   */
  readonly selectedSkillId?: string;
  /** Optional file attachments — switches the request to multipart/form-data. */
  readonly files?: ReadonlyArray<File>;
}

/**
 * Streams a turn through an explicit AI Agent
 * (`POST /v2/ai-agent/{agentId}/chat`).
 */
export async function postAgentChat(
  client: TuringClient,
  agentId: string,
  llmInstanceId: string,
  messages: TurChatConversationMessage[],
  options?: PostAgentChatOptions,
): Promise<TurChatConversationResponse> {
  const requestPayload = {
    llmInstanceId,
    messages,
    conversationId: options?.conversationId,
    flowId: options?.flowId,
    forcedVariant: options?.forcedVariant,
    selectedSkillId: options?.selectedSkillId,
  };

  const headers: Record<string, string> = {
    Accept: "text/event-stream",
    ...browserTimezoneHeaders(),
  };
  let body: BodyInit;
  if (options?.files && options.files.length > 0) {
    const form = new FormData();
    form.append(
      "request",
      new Blob([JSON.stringify(requestPayload)], { type: "application/json" }),
    );
    for (const file of options.files) form.append("files", file);
    body = form;
    // FormData → browser sets multipart Content-Type + boundary.
  } else {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(requestPayload);
  }

  const response = await client.fetchRaw(`/v2/ai-agent/${agentId}/chat`, {
    method: "POST",
    headers,
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
 * Options for {@link postPersonaChat}. A persona chat turn is deliberately
 * stateless (Block AI / §XXXII.1) — there is no conversation/flow/variant/skill
 * knob, only the streaming callbacks, an abort signal and optional attachments.
 */
export interface PostPersonaChatOptions {
  readonly onToken?: (token: string) => void;
  readonly onOptions?: (options: string[]) => void;
  /** Fired when the engine emits a native multi-field form schema (T107). */
  readonly onForm?: (form: TurChatForm) => void;
  /** Fired when the engine emits RAG provenance for the answer (T292). */
  readonly onSources?: (sources: TurChatSource[]) => void;
  /** Fired when the engine emits per-sentence Anthropic citations (T152). */
  readonly onCitations?: (citations: TurChatCitation[]) => void;
  /** Fired when the answer-grounding guardrail flags the answer (T516). */
  readonly onGrounding?: (grounding: TurChatGrounding) => void;
  /** Fired when the cross-vendor second-opinion check returns a verdict (T522). */
  readonly onSecondOpinion?: (secondOpinion: TurChatSecondOpinion) => void;
  /** Fired on each live tool-call lifecycle event (T436). */
  readonly onToolCall?: (call: TurChatToolCall, all: TurChatToolCall[]) => void;
  /** Fired when an OpenAI reasoning model returns its "Why this answer" summary (T178). */
  readonly onReasoning?: (reasoning: string) => void;
  readonly signal?: AbortSignal;
  /** Optional file attachments — switches the request to multipart/form-data. */
  readonly files?: ReadonlyArray<File>;
}

/**
 * Block AI / §XXXII.2 (T579) — talk directly to a persona
 * (`POST /v2/persona/{personaId}/chat`). Mirrors {@link postAgentChat} exactly;
 * only the URL and the (leaner, stateless) request payload differ. The whole
 * SSE-consume path is shared.
 */
export async function postPersonaChat(
  client: TuringClient,
  personaId: string,
  llmInstanceId: string,
  messages: TurChatConversationMessage[],
  options?: PostPersonaChatOptions,
): Promise<TurChatConversationResponse> {
  const requestPayload = { llmInstanceId, messages };

  const headers: Record<string, string> = {
    Accept: "text/event-stream",
    ...browserTimezoneHeaders(),
  };
  let body: BodyInit;
  if (options?.files && options.files.length > 0) {
    const form = new FormData();
    form.append(
      "request",
      new Blob([JSON.stringify(requestPayload)], { type: "application/json" }),
    );
    for (const file of options.files) form.append("files", file);
    body = form;
    // FormData → browser sets multipart Content-Type + boundary.
  } else {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(requestPayload);
  }

  const response = await client.fetchRaw(`/v2/persona/${personaId}/chat`, {
    method: "POST",
    headers,
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

export interface PostClientToolResultOptions {
  readonly onToken?: (token: string) => void;
  readonly onOptions?: (options: string[]) => void;
  readonly onForm?: (form: TurChatForm) => void;
  readonly onSources?: (sources: TurChatSource[]) => void;
  readonly onCitations?: (citations: TurChatCitation[]) => void;
  /** Fired when the answer-grounding guardrail flags the answer (T516). */
  readonly onGrounding?: (grounding: TurChatGrounding) => void;
  /** Fired when the cross-vendor second-opinion check returns a verdict (T522). */
  readonly onSecondOpinion?: (secondOpinion: TurChatSecondOpinion) => void;
  readonly onToolCall?: (call: TurChatToolCall, all: TurChatToolCall[]) => void;
  /** Fired when an OpenAI reasoning model returns its "Why this answer" summary (T178). */
  readonly onReasoning?: (reasoning: string) => void;
  readonly signal?: AbortSignal;
}

export interface ClientToolResultBody {
  readonly conversationId: string;
  readonly callId: string;
  /** The tool's return value (any JSON). Omit when reporting an `error`. */
  readonly result?: unknown;
  /** Client-side failure message fed back to the agent as the tool result. */
  readonly error?: string;
}

/**
 * T438 — resumes a turn parked on a frontend ("client") tool by POSTing the
 * browser's result to `POST /v2/chat/client-tool-result` and consuming the
 * continuation SSE. Returns the same shape as a chat turn, so the continuation
 * may itself park again on another client tool (chained round-trips).
 */
export async function postClientToolResult(
  client: TuringClient,
  body: ClientToolResultBody,
  options?: PostClientToolResultOptions,
): Promise<TurChatConversationResponse> {
  const response = await client.fetchRaw(`/v2/chat/client-tool-result`, {
    method: "POST",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
    },
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
 * T107 — submit a native multi-field {@code formCapture} form. Writes every
 * `values` entry to its named slot and advances the conversation past the
 * satisfied form node. Server endpoint: `POST /sn/{site}/chat/form-submit`.
 */
export function postSiteFormSubmit(
  client: TuringClient,
  site: string,
  conversationId: string,
  values: Record<string, string>,
  nodeId?: string,
): Promise<TurChatFormSubmitResponse> {
  return client.post<TurChatFormSubmitResponse>(`/sn/${site}/chat/form-submit`, {
    conversationId,
    nodeId,
    values,
  });
}

export interface PostLlmChatOptions {
  readonly onToken?: (token: string) => void;
  readonly signal?: AbortSignal;
  readonly files?: ReadonlyArray<File>;
}

/**
 * Streams a turn against an explicit {@code TurLLMInstance} with no agent, no
 * site, and no chat-flow context — {@code POST /v2/llm/{id}/chat}.
 */
export async function postLlmChat(
  client: TuringClient,
  llmInstanceId: string,
  messages: TurChatConversationMessage[],
  options?: PostLlmChatOptions,
): Promise<TurChatConversationResponse> {
  const headers: Record<string, string> = { Accept: "text/event-stream" };
  let body: BodyInit;
  if (options?.files && options.files.length > 0) {
    const form = new FormData();
    form.append(
      "request",
      new Blob([JSON.stringify({ messages })], { type: "application/json" }),
    );
    for (const file of options.files) form.append("files", file);
    body = form;
  } else {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify({ messages });
  }

  const response = await client.fetchRaw(`/v2/llm/${llmInstanceId}/chat`, {
    method: "POST",
    headers,
    body,
    signal: options?.signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const { text } = await consumeAssistantStream(response, options?.onToken);
  return { role: "assistant", content: text, options: [] };
}

export interface PostSemanticChatOptions {
  readonly onToken?: (token: string) => void;
  readonly signal?: AbortSignal;
}

/**
 * T404 — streams a turn against an explicit {@code TurLLMInstance} running the
 * Semantic Navigation tool set (list_sites / get_site_fields / search_site /
 * catalog_search + any MCP tools) — "talk to the index" without configuring a
 * full AI Agent. Server endpoint: {@code POST /v2/llm/{id}/semantic-chat}.
 *
 * <p>Unlike {@link postLlmChat} this endpoint consumes JSON only (no multipart
 * attachments) — the model reaches content through tool calls, not uploads.
 * The SSE stream reuses the same `data:` envelope, so {@link
 * consumeAssistantStream} parses it unchanged.
 */
export async function postSemanticChat(
  client: TuringClient,
  llmInstanceId: string,
  messages: TurChatConversationMessage[],
  options?: PostSemanticChatOptions,
): Promise<TurChatConversationResponse> {
  const response = await client.fetchRaw(`/v2/llm/${llmInstanceId}/semantic-chat`, {
    method: "POST",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
    },
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
 * Drops every chat-flow runtime state for {@code conversationId} on the site's
 * agent. Use to wire a "New chat" button on public search UIs.
 *
 * @returns number of state rows the server deleted (0 when nothing matched).
 */
export async function deleteSiteConversationState(
  client: TuringClient,
  site: string,
  conversationId: string,
): Promise<number> {
  const params = new URLSearchParams({ conversationId });
  const data = await client.del<number>(
    `/sn/${site}/chat/conversation-state?${params}`,
  );
  return typeof data === "number" ? data : 0;
}

/**
 * Drops the runtime state of {@code (conversationId, flowId)} on the agent
 * endpoint.
 */
export async function deleteAgentFlowState(
  client: TuringClient,
  agentId: string,
  flowId: string,
  conversationId: string,
): Promise<boolean> {
  const params = new URLSearchParams({ flowId, conversationId });
  const data = await client.del<boolean>(
    `/v2/ai-agent/${agentId}/chat-flow-state?${params}`,
  );
  return data === true;
}

/**
 * Fetches the curated list of enabled Intents for the site's AI agent.
 * Returns an empty array when the site has no agent or no enabled intents.
 */
export function fetchIntents(client: TuringClient, site: string): Promise<TurIntent[]> {
  return client.get<TurIntent[]>(`/sn/${site}/chat/intents`);
}

/**
 * Slots captured during a chat session, returned as a single flat map keyed
 * by slot name.
 */
export interface TurChatSessionSlots {
  readonly conversationId: string;
  readonly slots: Readonly<Record<string, string>>;
}

/**
 * T63 — a slot-stream delta from {@code GET /chat/slots/stream/delta}. The
 * first event is a {@code snapshot:true} carrying the whole map in
 * {@code added}; subsequent events are incremental ({@code added}/{@code updated}
 * upserts, {@code removed} deletes). The SSE multiplexer applies these to a
 * running map so consumers still receive full {@link TurChatSessionSlots}
 * snapshots.
 */
export interface TurChatSessionSlotsDelta {
  readonly conversationId: string;
  readonly snapshot: boolean;
  readonly added: Readonly<Record<string, string>>;
  readonly updated: Readonly<Record<string, string>>;
  readonly removed: readonly string[];
}

/**
 * T113 — a single artifact in an agent's per-conversation workspace, as
 * advertised on the workspace SSE bus. Carries <b>metadata only</b>: follow
 * {@link signedUrl} to download the bytes (a time-limited, HMAC-signed
 * {@code /api/v2/workspace/file} link). {@code contentType} / {@code signedUrl}
 * are {@code null} only transiently, never for a {@code put}.
 */
export interface TurWorkspaceArtifact {
  readonly key: string;
  readonly contentType: string | null;
  readonly size: number;
  readonly signedUrl: string | null;
}

/**
 * T113 — one workspace mutation from {@code GET /chat/workspace/stream}. The
 * server emits one per blob write/delete; the SSE multiplexer folds these into
 * a running artifact map and surfaces full {@link TurWorkspaceArtifacts}
 * snapshots to consumers (so a sidebar just renders {@code artifacts}).
 */
export interface TurWorkspaceEvent {
  readonly conversationId: string;
  readonly event: "put" | "delete";
  readonly key: string;
  readonly contentType: string | null;
  readonly size: number;
  readonly signedUrl: string | null;
}

/**
 * T113 — the reconstructed full list of artifacts in a conversation's
 * workspace, surfaced by the workspace SSE multiplexer after applying each
 * {@link TurWorkspaceEvent}. Sorted by key for stable rendering.
 */
export interface TurWorkspaceArtifacts {
  readonly conversationId: string;
  readonly artifacts: readonly TurWorkspaceArtifact[];
}

/**
 * Reads the slots captured during the conversation from the site's RAG
 * endpoint.
 *
 * Server endpoint: {@code GET /api/sn/{site}/chat/slots?conversationId=...}.
 */
export function fetchSiteChatSlots(
  client: TuringClient,
  site: string,
  conversationId: string,
): Promise<TurChatSessionSlots> {
  const qs = new URLSearchParams({ conversationId });
  return client.get<TurChatSessionSlots>(`/sn/${site}/chat/slots?${qs}`);
}

/**
 * Reads the slots captured during the conversation from the agent endpoint.
 *
 * Server endpoint: {@code GET /api/v2/ai-agent/{agentId}/chat-slots?conversationId=...}.
 */
export function fetchAgentChatSlots(
  client: TuringClient,
  agentId: string,
  conversationId: string,
): Promise<TurChatSessionSlots> {
  const qs = new URLSearchParams({ conversationId });
  return client.get<TurChatSessionSlots>(`/v2/ai-agent/${agentId}/chat-slots?${qs}`);
}

export interface TurChatSlotWriteResponse {
  readonly updatedStates: number;
}

/**
 * Writes a single slot on the current conversation through the site's GenAI
 * endpoint.
 *
 * Server endpoint: {@code POST /api/sn/{site}/chat/slots}.
 */
export async function postSiteChatSlot(
  client: TuringClient,
  site: string,
  conversationId: string,
  name: string,
  value: string,
): Promise<TurChatSlotWriteResponse> {
  const data = await client.post<TurChatSlotWriteResponse>(
    `/sn/${site}/chat/slots`,
    { conversationId, name, value },
  );
  return data ?? { updatedStates: 0 };
}

export interface TurChatSlotExtractResponse {
  readonly extracted: Readonly<Record<string, string | null>>;
  readonly slotsWritten: number;
  readonly extractedTextChars: number;
  /**
   * T102 — per-slot model confidence in `[0,1]`, present only when the request
   * passed `confidence: true`. Slots below {@link LOW_CONFIDENCE_THRESHOLD}
   * are worth flagging for human review.
   */
  readonly confidences?: Readonly<Record<string, number>>;
  /** Set when the server short-circuited (site/agent missing). */
  readonly error?: string | null;
}

/**
 * T102 — UI convention: a per-field confidence below this is "low" and should
 * be surfaced to the user (e.g. an inline warning to double-check the value).
 */
export const LOW_CONFIDENCE_THRESHOLD = 0.7;

/** Optional toggles for the slot-extract endpoints. */
export interface SlotExtractOptions {
  /** T102 — ask the model for a `{value, confidence}` per slot (populates `confidences`). */
  readonly confidence?: boolean;
  /**
   * T103 — send PDF/DOCX as a raw binary to the agent's LLM (native file
   * understanding, preserves tables/layout) instead of server-side Tika text
   * extraction. Requires the agent's provider to support document input
   * (Anthropic / Gemini / OpenAI); falls back to Tika on failure.
   */
  readonly nativeBinary?: boolean;
}

/**
 * Uploads a document (PDF / DOCX / TXT / RTF / HTML) or an image of a document
 * (PNG / JPEG / GIF / WebP — e.g. a phone-photo of a CV) and asks the server to
 * extract values for the requested slots using the agent's configured LLM.
 * Image uploads are routed to the agent's vision model server-side; no client
 * change is needed beyond passing the image File.
 *
 * Server endpoint: {@code POST /api/sn/{site}/chat/slot-extract}.
 */
export async function postSiteSlotExtract(
  client: TuringClient,
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
  const data = await client.post<TurChatSlotExtractResponse>(
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
 */
export async function postSiteSlotExtractMulti(
  client: TuringClient,
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
  const data = await client.post<TurChatSlotExtractResponse>(
    `/sn/${site}/chat/slot-extract-multi`,
    form,
  );
  return data ?? { extracted: {}, slotsWritten: 0, extractedTextChars: 0 };
}

export interface TurChatConversationState {
  readonly conversationId: string;
  readonly flowId: string | null;
  readonly flowName: string | null;
  readonly currentNodeId: string | null;
  readonly guardrailMethod: string | null;
  readonly experimentKey: string | null;
  readonly variantLabel: string | null;
  /** T121 — parked-reason label when the leaf cursor sits on a suspend node. */
  readonly suspendedReason?: string | null;
  /**
   * T461 — the active persona id resolved for this conversation (the
   * `__activePersonaId` flow variable), or `null` when none is active. Used by
   * the analytics bus to stamp `persona_id` for GA4 A/B attribution.
   */
  readonly personaId?: string | null;
}

/**
 * Lightweight read of the conversation's flow state.
 *
 * Server endpoint: {@code GET /api/sn/{site}/chat/state?conversationId=...}.
 */
export function fetchSiteChatState(
  client: TuringClient,
  site: string,
  conversationId: string,
): Promise<TurChatConversationState> {
  const qs = new URLSearchParams({ conversationId });
  return client.get<TurChatConversationState>(`/sn/${site}/chat/state?${qs}`);
}

export type TurChatHandoffChannel =
  | "whatsapp"
  | "email"
  | "sms"
  | "telegram"
  | "slack";

export interface TurChatHandoffResponse {
  readonly url: string | null;
  readonly transcript: string | null;
  readonly slotsIncluded: number;
  readonly error?: string | null;
}

/**
 * Builds a channel-specific handoff deep link carrying the conversation
 * context.
 *
 * Server endpoint: {@code POST /api/sn/{site}/chat/handoff}.
 */
export async function postSiteHandoff(
  client: TuringClient,
  site: string,
  params: {
    readonly conversationId: string;
    readonly channel: TurChatHandoffChannel;
    readonly destination: string;
    readonly intro?: string;
    readonly slotsToInclude?: ReadonlyArray<string>;
  },
): Promise<TurChatHandoffResponse> {
  const data = await client.post<TurChatHandoffResponse>(
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

export interface TurChatFlowSelectResponse {
  readonly success: boolean;
  readonly pinnedFlowId: string | null;
  readonly pinnedFlowName: string | null;
  readonly reason: string | null;
}

/**
 * Pins a specific chat-flow for the conversation, overriding the LLM trigger
 * router. {@code flow} resolves by UUID first, then by case-insensitive name.
 *
 * Server endpoint: {@code POST /api/sn/{site}/chat/flow-select}.
 */
export async function postSiteFlowSelect(
  client: TuringClient,
  site: string,
  conversationId: string,
  flow: string,
  personaId?: string,
): Promise<TurChatFlowSelectResponse> {
  const data = await client.post<TurChatFlowSelectResponse>(
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
 * text against a persona from the site agent's catalog and returns a structured
 * content-fit verdict (fit %, what does/doesn't fit, flagged spans + rewrites).
 *
 * The persona must be an <em>audience</em>-usable member of the site's effective
 * agent catalog; anything else is a 404 server-side. The text is hard-capped
 * (12 000 chars → 413) — it's an LLM-cost surface — and rate-limited on the demo
 * host, so keep submissions short.
 *
 * Server endpoint: {@code POST /api/sn/{site}/persona/{personaId}/content-fit}.
 */
export async function fetchPersonaContentFit(
  client: TuringClient,
  site: string,
  personaId: string,
  content: string,
  sourceName?: string,
): Promise<TurContentFit> {
  const data = await client.post<TurContentFit>(
    `/sn/${site}/persona/${personaId}/content-fit`,
    { content, sourceName },
  );
  if (!data) {
    throw new Error("Empty content-fit response");
  }
  return data;
}

/**
 * Lists the LLM instances visible to the current session. Secured behind
 * {@code ROLE_ADMIN}/{@code LLM_VIEW} — anonymous callers get 401/403.
 *
 * Server endpoint: {@code GET /api/llm}.
 */
export function fetchLlmInstances(client: TuringClient): Promise<TurLlmInstance[]> {
  return client.get<TurLlmInstance[]>(`/llm`);
}

/**
 * Probes the configured context window for a given LLM instance.
 *
 * Server endpoint: {@code GET /api/v2/llm/{id}/chat/context-info}.
 */
export function fetchLlmContextInfo(
  client: TuringClient,
  llmInstanceId: string,
): Promise<TurLlmContextInfo> {
  return client.get<TurLlmContextInfo>(`/v2/llm/${llmInstanceId}/chat/context-info`);
}

/**
 * Like {@link fetchLlmContextInfo} but scoped to an AI Agent.
 *
 * Server endpoint:
 * {@code GET /api/v2/ai-agent/{agentId}/chat/context-info?llmInstanceId=...}.
 */
export function fetchAgentContextInfo(
  client: TuringClient,
  agentId: string,
  llmInstanceId: string,
): Promise<TurLlmContextInfo> {
  const qs = new URLSearchParams({ llmInstanceId });
  return client.get<TurLlmContextInfo>(
    `/v2/ai-agent/${agentId}/chat/context-info?${qs}`,
  );
}

export function fetchAutoComplete(
  client: TuringClient,
  site: string,
  params: SearchParams,
): Promise<string[]> {
  return client.get<string[]>(`/sn/${site}/ac?${buildQueryString(params)}`);
}

export function fetchSortOptions(
  client: TuringClient,
  site: string,
): Promise<TurSortOption[]> {
  return client.get<TurSortOption[]>(`/sn/${site}/search/sort-options`);
}

// ── T400: Similar documents ────────────────────────────────────────────────

/**
 * Strategy for {@link fetchSimilar}: {@code VECTOR} (semantic / embedding
 * neighbours) or {@code MLT} (lexical "more like this"). Omit to let the server
 * auto-select based on the site's configured engine.
 */
export type TurSimilarMode = "VECTOR" | "MLT";

/**
 * T384 — one related document returned by {@link fetchSimilar} (server
 * {@code TurSESimilarResult}). Lightweight by design: render the link directly
 * or hydrate the full document via a follow-up search if needed.
 */
export interface TurSimilarResult {
  readonly id: string;
  readonly title: string;
  readonly type: string;
  readonly url: string;
}

export interface FetchSimilarParams {
  /** Seed document id to find neighbours for. */
  readonly id: string;
  /** Max results to return (server caps at 50; default 10). */
  readonly rows?: number;
  /** Locale override (e.g. {@code "en_US"}); omit to use the site default. */
  readonly locale?: string;
  /** Force {@code VECTOR} or {@code MLT}; omit for server auto-selection. */
  readonly mode?: TurSimilarMode;
}

/**
 * T400 — fetches documents similar to a seed document (T384's
 * {@code GET /sn/{site}/search/similar}). Powers a "related" / "you may also
 * like" rail next to a result or detail page with one call.
 */
export function fetchSimilar(
  client: TuringClient,
  site: string,
  params: FetchSimilarParams,
): Promise<TurSimilarResult[]> {
  const qs = new URLSearchParams({ id: params.id });
  if (params.rows != null) qs.append("rows", String(params.rows));
  if (params.locale) qs.append("locale", params.locale);
  if (params.mode) qs.append("mode", params.mode);
  return client.get<TurSimilarResult[]>(`/sn/${site}/search/similar?${qs}`);
}

// ── T401: Multimodal slot-upload + flow resume ──────────────────────────────

/**
 * T64 — response of {@link postSiteSlotUpload}. The uploaded artifact is stored
 * via the configured storage backend; {@link url} is a signed link to download
 * it. When {@code vision} was requested, {@link visionExtracted} carries the
 * scalar slots the vision LLM filled.
 */
export interface TurChatSlotUploadResponse {
  readonly slotName: string;
  readonly slotType: string;
  readonly objectName: string;
  readonly url: string;
  readonly contentType: string;
  readonly size: number;
  readonly visionExtracted: Readonly<Record<string, string>>;
  readonly visionSlotsWritten: number;
  readonly error?: string | null;
}

export interface PostSlotUploadOptions {
  /** Run a vision LLM over an image to fill scalar slots (T64). */
  readonly vision?: boolean;
  /** Scalar slot names to fill via vision when {@link vision} is on. */
  readonly visionSlotNames?: ReadonlyArray<string>;
}

/**
 * T401 — uploads an IMAGE / AUDIO / FILE to a multimodal slot (T64's
 * {@code POST /sn/{site}/chat/slot-upload}). The slot holds the storage URL;
 * with {@code vision} the server also runs a vision LLM to extract scalar
 * values into the named slots.
 */
export async function postSiteSlotUpload(
  client: TuringClient,
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
  const data = await client.post<TurChatSlotUploadResponse>(
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
 * T121 — response of {@link postSiteChatResume}. {@link wasParked} is
 * {@code false} when the conversation had no suspended flow to resume.
 */
export interface TurChatResumeResponse {
  readonly resumed: number;
  readonly wasParked: boolean;
  readonly error?: string | null;
}

/**
 * T401 — resumes a conversation parked at a suspend node
 * ({@code POST /sn/{site}/chat/resume}), optionally applying {@code slotUpdates}
 * before advancing past the suspend point.
 */
export async function postSiteChatResume(
  client: TuringClient,
  site: string,
  conversationId: string,
  slotUpdates?: Readonly<Record<string, string>>,
  resumeReason?: string,
): Promise<TurChatResumeResponse> {
  const data = await client.post<TurChatResumeResponse>(`/sn/${site}/chat/resume`, {
    conversationId,
    slotUpdates,
    resumeReason,
  });
  return data ?? { resumed: 0, wasParked: false, error: "empty response" };
}

// ── T402: Spell-check ───────────────────────────────────────────────────────

/**
 * T402 — fetches "did you mean" corrections for a query
 * ({@code GET /sn/{site}/{locale}/spell-check?q=...}). Returns the same
 * {@link TurSpellCheck} shape the search bean embeds, so a search box can render
 * a correction prompt without parsing the full search response.
 */
export function fetchSpellCheck(
  client: TuringClient,
  site: string,
  locale: string,
  q: string,
): Promise<TurSpellCheck> {
  const qs = new URLSearchParams({ q });
  return client.get<TurSpellCheck>(`/sn/${site}/${locale}/spell-check?${qs}`);
}

// ── T678: Related-concept suggestions (microthesaurus RT links) ─────────────

/**
 * T678 — fetches controlled-vocabulary "related concepts" for a query
 * ({@code GET /sn/{site}/{locale}/related-terms?q=...}). For each thesaurus term
 * recognised in the query it returns the labels of its {@code RELATED} (RT)
 * neighbours, so a search box can offer "you may also be interested in" chips
 * alongside the spell-check "did you mean" prompt. Empty for an opted-out site or
 * a query that mentions no thesaurus term.
 */
export function fetchRelatedTerms(
  client: TuringClient,
  site: string,
  locale: string,
  q: string,
): Promise<TurRelatedTermSuggestion[]> {
  const qs = new URLSearchParams({ q });
  return client.get<TurRelatedTermSuggestion[]>(
    `/sn/${site}/${locale}/related-terms?${qs}`,
  );
}

// ── T403: Elasticsearch-compatible DSL search ───────────────────────────────

/**
 * T403 — a structured Elasticsearch-compatible query body for {@link dslSearch}
 * ({@code POST /sn/{site}/_search}). Mirrors the server {@code TurDslQueryRequest}:
 * {@code query} (match / bool / term / range …), pagination ({@code from} /
 * {@code size}), {@code sort}, {@code _source} projection, {@code highlight},
 * {@code aggs}, {@code post_filter}, and {@code min_score}. Extra ES keys pass
 * through unchanged.
 */
export interface TurDslSearchRequest {
  query?: Record<string, unknown>;
  from?: number;
  size?: number;
  sort?: unknown[];
  _source?: unknown;
  highlight?: Record<string, unknown>;
  aggs?: Record<string, unknown>;
  post_filter?: Record<string, unknown>;
  min_score?: number;
  [key: string]: unknown;
}

/** One document hit in a {@link TurDslSearchResponse}. */
export interface TurDslSearchHit {
  readonly _id: string;
  readonly _score?: number | null;
  readonly _source: Record<string, unknown>;
  readonly highlight?: Record<string, string[]>;
  readonly fields?: Record<string, unknown>;
}

/**
 * T403 — Elasticsearch-compatible response of {@link dslSearch} (server
 * {@code TurDslSearchResponse}). {@code aggregations} / {@code suggest} are left
 * loosely typed because their shape depends on the aggregation/suggester used.
 */
export interface TurDslSearchResponse {
  readonly took: number;
  readonly timed_out: boolean;
  readonly hits: {
    readonly total: { value: number; relation: string };
    readonly max_score?: number | null;
    readonly hits: TurDslSearchHit[];
  };
  readonly aggregations?: Record<string, unknown>;
  readonly suggest?: Record<string, unknown>;
}

/**
 * T403 — runs a structured Elasticsearch-compatible query against a site
 * ({@code POST /sn/{site}/_search}). For power users building bool/filter/aggs
 * queries from JS without the facet-param layer.
 */
export function dslSearch(
  client: TuringClient,
  site: string,
  request: TurDslSearchRequest,
  locale = "en",
): Promise<TurDslSearchResponse> {
  const qs = new URLSearchParams({ locale });
  return client.post<TurDslSearchResponse>(`/sn/${site}/_search?${qs}`, request);
}

/**
 * T403 — runs a raw Solr-style DSL query ({@code POST /sn/{site}/query}) and
 * returns the parsed JSON response verbatim. Lower-level than {@link dslSearch};
 * the {@code locale} is required by this endpoint.
 */
export function dslQuery(
  client: TuringClient,
  site: string,
  query: Record<string, unknown> | string,
  locale: string,
): Promise<unknown> {
  const qs = new URLSearchParams({ locale });
  const body = typeof query === "string" ? query : JSON.stringify(query);
  return client.post<unknown>(`/sn/${site}/query?${qs}`, body, {
    headers: { "Content-Type": "application/json" },
  });
}

// ── T405: Platform discovery helpers ────────────────────────────────────────

/**
 * T405 — public platform identity from {@code GET /discovery} (server
 * {@code TurAPIBean}): product name, multi-tenancy / auth topology, and the
 * available OAuth providers. Lets a generic embed discover its environment.
 */
export interface TurDiscoveryInfo {
  readonly product: string;
  readonly multiTenant: boolean;
  readonly keycloak: boolean;
  readonly authThirdparty: boolean;
  readonly selfRegistration: boolean;
  readonly oauth2Providers: string[];
}

/**
 * T405 — which platform capabilities are on, from {@code GET /features} (server
 * {@code FeaturesResponse}). A widget reads this to hide features the backend
 * has disabled (e.g. storage-backed assets, RAG, skills).
 */
export interface TurFeaturesInfo {
  readonly storageEnabled: boolean;
  readonly ragEnabled: boolean;
  readonly gitServerEnabled: boolean;
  readonly marketplaceEnabled: boolean;
  readonly seInstanceReadOnly: boolean;
  readonly skillsEnabled: boolean;
  readonly tenancyEnabled: boolean;
  readonly platformAdmin: boolean;
  readonly loggingEngine: string;
}

/** T405 — a supported system locale from {@code GET /locale} (server {@code TurLocale}). */
export interface TurSystemLocale {
  readonly initials: string;
  readonly en: string;
  readonly pt: string;
}

/**
 * T405 — result of {@link postSummary} (server
 * {@code TurLlmSummaryService.SummaryResult}). The cached AI summary plus
 * whether the caller may force a regenerate.
 */
export interface TurSummaryResult {
  readonly success: boolean;
  readonly error: string | null;
  readonly content: string;
  readonly canRegenerate: boolean;
}

/** T405 — platform identity / auth topology. Server: {@code GET /api/discovery}. */
export function fetchDiscovery(client: TuringClient): Promise<TurDiscoveryInfo> {
  return client.get<TurDiscoveryInfo>(`/discovery`);
}

/** T405 — enabled platform capabilities. Server: {@code GET /api/features}. */
export function fetchFeatures(client: TuringClient): Promise<TurFeaturesInfo> {
  return client.get<TurFeaturesInfo>(`/features`);
}

/** T405 — supported system locales. Server: {@code GET /api/locale}. */
export function fetchSystemLocales(client: TuringClient): Promise<TurSystemLocale[]> {
  return client.get<TurSystemLocale[]>(`/locale`);
}

/** T405 — configured LLM vendors. Server: {@code GET /api/llm/vendor}. */
export function fetchLlmVendors(client: TuringClient): Promise<TurLlmVendor[]> {
  return client.get<TurLlmVendor[]>(`/llm/vendor`);
}

/**
 * T405 — generates (or returns a cached) LLM summary for arbitrary data
 * ({@code POST /api/v2/summary}). {@code regenerate} bypasses the cache.
 */
export async function postSummary(
  client: TuringClient,
  params: {
    readonly cacheKey: string;
    readonly data: string;
    readonly systemPrompt?: string;
    readonly regenerate?: boolean;
  },
): Promise<TurSummaryResult> {
  const path = params.regenerate ? `/v2/summary?regenerate=true` : `/v2/summary`;
  const data = await client.post<TurSummaryResult>(path, {
    cacheKey: params.cacheKey,
    data: params.data,
    systemPrompt: params.systemPrompt,
  });
  return data ?? { success: false, error: "empty response", content: "", canRegenerate: false };
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

/**
 * Binds every API function above to a {@link TuringClient}, returning an
 * object whose methods drop the leading `client` argument. Ergonomic for
 * EDS blocks that prefer an object over passing the client to each call.
 *
 * @example
 * ```js
 * const api = createTuringApi(client);
 * const res = await api.fetchSearch("my-site", { q: "hello" });
 * ```
 */
export function createTuringApi(client: TuringClient) {
  return {
    fetchSearch: (site: string, params: SearchParams) => fetchSearch(client, site, params),
    postClick: (site: string, params: ClickTrackParams) => postClick(client, site, params),
    fetchChat: (site: string, params: Pick<SearchParams, "q" | "_setlocale">) =>
      fetchChat(client, site, params),
    fetchChatEnabled: (site: string) => fetchChatEnabled(client, site),
    postChatConversation: (
      site: string,
      messages: TurChatConversationMessage[],
      locale?: string,
      options?: PostChatConversationOptions,
    ) => postChatConversation(client, site, messages, locale, options),
    postAgentChat: (
      agentId: string,
      llmInstanceId: string,
      messages: TurChatConversationMessage[],
      options?: PostAgentChatOptions,
    ) => postAgentChat(client, agentId, llmInstanceId, messages, options),
    postPersonaChat: (
      personaId: string,
      llmInstanceId: string,
      messages: TurChatConversationMessage[],
      options?: PostPersonaChatOptions,
    ) => postPersonaChat(client, personaId, llmInstanceId, messages, options),
    postLlmChat: (
      llmInstanceId: string,
      messages: TurChatConversationMessage[],
      options?: PostLlmChatOptions,
    ) => postLlmChat(client, llmInstanceId, messages, options),
    postSemanticChat: (
      llmInstanceId: string,
      messages: TurChatConversationMessage[],
      options?: PostSemanticChatOptions,
    ) => postSemanticChat(client, llmInstanceId, messages, options),
    deleteSiteConversationState: (site: string, conversationId: string) =>
      deleteSiteConversationState(client, site, conversationId),
    deleteAgentFlowState: (agentId: string, flowId: string, conversationId: string) =>
      deleteAgentFlowState(client, agentId, flowId, conversationId),
    fetchIntents: (site: string) => fetchIntents(client, site),
    fetchSiteChatSlots: (site: string, conversationId: string) =>
      fetchSiteChatSlots(client, site, conversationId),
    fetchAgentChatSlots: (agentId: string, conversationId: string) =>
      fetchAgentChatSlots(client, agentId, conversationId),
    postSiteChatSlot: (site: string, conversationId: string, name: string, value: string) =>
      postSiteChatSlot(client, site, conversationId, name, value),
    postSiteSlotExtract: (
      site: string,
      file: File,
      conversationId: string,
      slotNames?: ReadonlyArray<string>,
    ) => postSiteSlotExtract(client, site, file, conversationId, slotNames),
    fetchSiteChatState: (site: string, conversationId: string) =>
      fetchSiteChatState(client, site, conversationId),
    postSiteHandoff: (
      site: string,
      params: Parameters<typeof postSiteHandoff>[2],
    ) => postSiteHandoff(client, site, params),
    postSiteFlowSelect: (site: string, conversationId: string, flow: string, personaId?: string) =>
      postSiteFlowSelect(client, site, conversationId, flow, personaId),
    fetchPersonaContentFit: (
      site: string,
      personaId: string,
      content: string,
      sourceName?: string,
    ) => fetchPersonaContentFit(client, site, personaId, content, sourceName),
    fetchLlmInstances: () => fetchLlmInstances(client),
    fetchLlmContextInfo: (llmInstanceId: string) => fetchLlmContextInfo(client, llmInstanceId),
    fetchAgentContextInfo: (agentId: string, llmInstanceId: string) =>
      fetchAgentContextInfo(client, agentId, llmInstanceId),
    fetchAutoComplete: (site: string, params: SearchParams) =>
      fetchAutoComplete(client, site, params),
    fetchSortOptions: (site: string) => fetchSortOptions(client, site),
    fetchSimilar: (site: string, params: FetchSimilarParams) =>
      fetchSimilar(client, site, params),
    postSiteSlotUpload: (
      site: string,
      conversationId: string,
      slotName: string,
      file: File,
      options?: PostSlotUploadOptions,
    ) => postSiteSlotUpload(client, site, conversationId, slotName, file, options),
    postSiteChatResume: (
      site: string,
      conversationId: string,
      slotUpdates?: Readonly<Record<string, string>>,
      resumeReason?: string,
    ) => postSiteChatResume(client, site, conversationId, slotUpdates, resumeReason),
    fetchSpellCheck: (site: string, locale: string, q: string) =>
      fetchSpellCheck(client, site, locale, q),
    fetchRelatedTerms: (site: string, locale: string, q: string) =>
      fetchRelatedTerms(client, site, locale, q),
    dslSearch: (site: string, request: TurDslSearchRequest, locale?: string) =>
      dslSearch(client, site, request, locale),
    dslQuery: (site: string, query: Record<string, unknown> | string, locale: string) =>
      dslQuery(client, site, query, locale),
    fetchDiscovery: () => fetchDiscovery(client),
    fetchFeatures: () => fetchFeatures(client),
    fetchSystemLocales: () => fetchSystemLocales(client),
    fetchLlmVendors: () => fetchLlmVendors(client),
    postSummary: (params: Parameters<typeof postSummary>[1]) =>
      postSummary(client, params),
  };
}

export type TuringApi = ReturnType<typeof createTuringApi>;
