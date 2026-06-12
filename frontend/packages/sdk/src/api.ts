import type { TuringClient } from "./client";
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
  return { text: aggregated, options: optionLabels, form, sources };
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

export interface PostAgentChatOptions {
  readonly onToken?: (token: string) => void;
  readonly onOptions?: (options: string[]) => void;
  /** Fired when the engine emits a native multi-field form schema (T107). */
  readonly onForm?: (form: TurChatForm) => void;
  /** Fired when the engine emits RAG provenance for the answer (T292). */
  readonly onSources?: (sources: TurChatSource[]) => void;
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
  /** Set when the server short-circuited (site/agent missing). */
  readonly error?: string | null;
}

/**
 * Uploads a document (PDF / DOCX / TXT / RTF / HTML) and asks the server to
 * extract values for the requested slots using the agent's configured LLM.
 *
 * Server endpoint: {@code POST /api/sn/{site}/chat/slot-extract}.
 */
export async function postSiteSlotExtract(
  client: TuringClient,
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
  const data = await client.post<TurChatSlotExtractResponse>(
    `/sn/${site}/chat/slot-extract`,
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
): Promise<TurChatFlowSelectResponse> {
  const data = await client.post<TurChatFlowSelectResponse>(
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
    postLlmChat: (
      llmInstanceId: string,
      messages: TurChatConversationMessage[],
      options?: PostLlmChatOptions,
    ) => postLlmChat(client, llmInstanceId, messages, options),
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
    postSiteFlowSelect: (site: string, conversationId: string, flow: string) =>
      postSiteFlowSelect(client, site, conversationId, flow),
    fetchLlmInstances: () => fetchLlmInstances(client),
    fetchLlmContextInfo: (llmInstanceId: string) => fetchLlmContextInfo(client, llmInstanceId),
    fetchAgentContextInfo: (agentId: string, llmInstanceId: string) =>
      fetchAgentContextInfo(client, agentId, llmInstanceId),
    fetchAutoComplete: (site: string, params: SearchParams) =>
      fetchAutoComplete(client, site, params),
    fetchSortOptions: (site: string) => fetchSortOptions(client, site),
  };
}

export type TuringApi = ReturnType<typeof createTuringApi>;
