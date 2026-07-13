/**
 * Raw API + resolved domain types for the Turing ES vanilla SDK.
 *
 * <p>Ported verbatim from the React SDK's `core/types.ts` (the React SDK keeps
 * its own copy of these types too — copying here keeps `@viglet/turing-sdk`
 * self-contained and free of any cross-package runtime dependency). UI
 * component prop types from the React copy are intentionally omitted — this
 * package ships no components.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

// ── Client / Provider Configuration ──

export interface TuringConfig {
  /** Semantic Navigation site name */
  site: string;
  /** Default locale for searches (e.g. "en_US") */
  locale?: string;
  /** Default sort criteria (e.g. "relevance") */
  sort?: string;
}

// ── Raw API Response Types (mirror Turing backend) ──

export interface TurSearchResponse {
  pagination?: TurPaginationItem[];
  queryContext: TurQueryContext;
  results?: TurSearchResults;
  widget: TurWidget;
  /** Present when the `group` parameter is used */
  groups?: TurGroupBean[];
}

/** A single group in a grouped search response */
export interface TurGroupBean {
  name: string;
  count: number;
  page: number;
  pageCount: number;
  pageStart: number;
  pageEnd: number;
  limit: number;
  results: TurSearchResults;
  pagination: TurPaginationItem[];
}

export interface TurQueryContext {
  count: number;
  defaultFields: TurDefaultFields;
  index: string;
  limit: number;
  offset: number;
  page: number;
  pageCount: number;
  pageEnd: number;
  pageStart: number;
  query: TurQueryMeta;
  responseTime: number;
  facetType: string;
  facetItemType: string;
}

export interface TurDefaultFields {
  date: string;
  description: string;
  image: string;
  text: string;
  title: string;
  url: string;
}

export interface TurQueryMeta {
  queryString: string;
  sort: string;
  locale: string;
}

export interface TurSearchResults {
  document: TurDocument[];
}

export interface TurDocument {
  elevate: boolean;
  fields: Record<string, any>;
  metadata: TurDocumentMetadata[];
  /**
   * T390 — cluster of near-identical documents (the same real-world entity from
   * many sources). Present only on hybrid sites with MoreLikeThis enabled and
   * when this result actually has a duplicate; absent otherwise.
   */
  duplicateCluster?: TurDuplicateCluster;
}

/** T390 — one member of a {@link TurDuplicateCluster}, kept with its provenance. */
export interface TurDuplicateClusterMember {
  id: string;
  title?: string | null;
  type?: string | null;
  url?: string | null;
  /** Originating source/app (the `source_apps` field), or null when not indexed. */
  source?: string | null;
  /** Similarity to the seed document; null for the seed itself. */
  similarity?: number | null;
}

/**
 * T390 — near-identical documents collapsed into one cluster. `canonicalId` is the
 * deterministic representative (lexicographically smallest member id), so any
 * member resolves to the same canonical result.
 */
export interface TurDuplicateCluster {
  canonicalId: string;
  size: number;
  duplicate: boolean;
  members: TurDuplicateClusterMember[];
}

export interface TurDocumentMetadata {
  href: string;
  text: string;
}

export interface TurPaginationItem {
  href: string;
  page: number;
  text: string;
  type: string;
}

// ── Widget (Facets, SpellCheck, Locales) ──

export interface TurWidget {
  icon?: string | null;
  facet: TurFacetGroup[];
  facetToRemove: TurFacetGroup;
  similar: string;
  spellCheck: TurSpellCheck;
  locales: TurLocaleItem[];
  cleanUpFacets: string;
}

export interface TurFacetGroup {
  facets: TurFacetItem[];
  label: { lang: string; text: string };
  name: string;
  description: string;
  type: string;
  multivalued: boolean;
  cleanUpLink: string;
}

export interface TurFacetItem {
  count: number;
  label: string;
  link: string;
  selected: boolean;
}

export interface TurSpellCheck {
  correctedText: boolean;
  usingCorrectedText: boolean;
  original: { link: string; text: string };
  corrected: { link: string; text: string };
}

/**
 * T678 — one query-time "related concepts" suggestion: a controlled-vocabulary
 * `term` recognised in the query and the labels of the terms it is associatively
 * linked to through the microthesaurus `RELATED` (RT) edges.
 */
export interface TurRelatedTermSuggestion {
  term: string;
  related: string[];
}

export interface TurLocaleItem {
  locale: string;
  link: string;
}

// ── Sort Options ──

export interface TurSortOption {
  value: string;
  label: string;
}

// ── LLM Instances ──

export interface TurLlmVendor {
  readonly id: string;
  readonly title: string;
  readonly description?: string;
  /** Provider plugin type (e.g. `openai`); present on the `/llm/vendor` list. */
  readonly plugin?: string;
  /** Vendor website; present on the `/llm/vendor` list. */
  readonly website?: string;
}

export interface TurLlmInstance {
  readonly id: string;
  readonly title: string;
  readonly description?: string;
  /** 1 = available to use; 0 = hidden from selection. */
  readonly enabled: number;
  /** Configured maximum context window in tokens (may be 0 = unknown). */
  readonly contextWindow?: number;
  /** Provider-side model identifier (e.g. `gpt-4o-mini`). */
  readonly modelName?: string;
  /** Iconify identifier (e.g. `simple-icons:openai`) when set by an admin. */
  readonly icon?: string | null;
  readonly turLLMVendor?: TurLlmVendor;
}

export interface TurLlmContextInfo {
  readonly contextWindow: number;
  readonly source: string;
}

// ── Chat ──

export interface TurChatResponse {
  text: string;
  enabled: boolean;
}

export interface TurChatConversationMessage {
  /**
   * Conversation role. {@code "user"} and {@code "assistant"} cover the
   * normal turn-taking; {@code "system"} is accepted by the plain-LLM chat
   * endpoint for callers that want to inject a per-request directive.
   */
  role: "user" | "assistant" | "system";
  content: string;
}

/**
 * One Server-Sent Event emitted by the chat endpoints.
 * - `type === "options"` carries a JSON-encoded `string[]` of chip labels;
 * - `type === "form"` (T107) carries a JSON-encoded {@link TurChatForm} schema
 *   so the SDK can render a native multi-field form;
 * - `type === "sources"` (T292) carries a JSON-encoded {@link TurChatSource}[]
 *   array of the RAG provenance behind the answer;
 * - `type === "citations"` (T152) carries a JSON-encoded {@link TurChatCitation}[]
 *   of the per-sentence Anthropic citations grounding the answer;
 * - `type === "tool_call"` (T436) carries a JSON-encoded {@link TurChatToolCall}
 *   lifecycle event (a tool starting or finishing) so the UI can show live tool
 *   activity;
 * - otherwise `content` is assistant text to append.
 */
export interface TurChatStreamEvent {
  role: "assistant";
  content: string;
  type?:
    | "token"
    | "options"
    | "form"
    | "sources"
    | "citations"
    | "searchSuggestions"
    | "reasoning"
    | "tool_call"
    | "client_tool_call"
    | "grounding"
    | "secondOpinion";
}

/**
 * T522 / §XXVIII.18 — the multi-provider "second opinion" verdict carried by the
 * `"secondOpinion"` SSE event, emitted only when the cross-check is enabled. A
 * cheap model from a DIFFERENT vendor critiqued the answer; the chat UI renders
 * an "agrees / disagrees" confidence badge beside the bubble. Mirrors the backend
 * `TurSecondOpinion`.
 */
export interface TurChatSecondOpinion {
  /** `true` when the cross-vendor critic judged the answer accurate + supported. */
  agree: boolean;
  /** Optional calibrated confidence in `[0,1]`, when available. */
  confidence?: number | null;
  /** The critic's one-line reason (for the hover/tooltip). */
  rationale?: string | null;
  /** The critic model name. */
  criticModel?: string | null;
  /** The critic's vendor/plugin type (always different from the answerer's). */
  criticVendor?: string | null;
}

/**
 * T516 / §XXVIII.12 — the answer-grounding guardrail verdict carried by the
 * `"grounding"` SSE event, emitted only when the guardrail is enabled and
 * flagged something. Mirrors the backend `TurAnswerGuardrailVerdict`. The chat
 * UI renders a confidence badge beside the answer (e.g. "⚠ not grounded" or a
 * flagged-category chip); a clean answer emits no event at all.
 */
export interface TurChatGrounding {
  /** `false` when the answer is not supported by the retrieved context. */
  grounded: boolean;
  /** Contextual-grounding confidence in `[0,1]`, when assessed. */
  groundingScore?: number | null;
  /** Answer↔query relevance confidence in `[0,1]`, when assessed. */
  relevanceScore?: number | null;
  /** Recommended action: `"PASS"`, `"FLAG"`, or `"BLOCK"`. */
  action: "PASS" | "FLAG" | "BLOCK";
  /** Flagged content/PII categories (e.g. `"ungrounded"`, `"pii:EMAIL"`). */
  categories: string[];
  /** Masked answer variant when the guardrail redacted PII, else null. */
  redactedAnswer?: string | null;
  /** Which guardrail backend produced this verdict. */
  strategy: "NONE" | "BEDROCK" | "OPENAI_MODERATION" | "MISTRAL_MODERATION";
}

/**
 * T490 / §X.19 — Google Search Suggestion chips carried by the
 * `"searchSuggestions"` SSE event, emitted when a Gemini turn used the
 * `google_search` grounding tool. Google's terms of service **require**
 * rendering these chips whenever a grounded answer is shown; the headless UI
 * renders `renderedContent` (a self-contained HTML/CSS fragment) verbatim.
 * Mirrors the backend `TurGeminiGroundingDecoder.SearchSuggestions` record.
 */
export interface TurSearchSuggestions {
  /** Self-contained HTML/CSS fragment for the Google-mandated chips (render as-is). */
  renderedContent?: string | null;
  /** The web-search queries the model ran (a textual fallback / debug aid). */
  queries?: string[];
}

/**
 * T438 — payload of the `"client_tool_call"` SSE event: the agent asked the
 * <em>browser</em> to run a frontend tool and the turn parked. The SDK runs the
 * registered handler for {@link #name}, POSTs the result to
 * `/v2/chat/client-tool-result`, and the continuation streams back. Distinct
 * from {@link TurChatToolCall} (which is read-only tool <em>activity</em>).
 */
export interface TurClientToolCall {
  /** Correlates the call with its result POST; keyed park on the server. */
  callId: string;
  /** The frontend tool the agent invoked (must be a registered handler). */
  name: string;
  /** Raw JSON arguments string the model passed (parse before calling the handler). */
  args: string;
}

/**
 * T436 — one tool-call lifecycle event carried by the `"tool_call"` SSE event,
 * and (merged by `callId`) the shape surfaced on
 * {@link TurChatConversationResponse.toolCalls}. Mirrors the backend
 * `TurChatToolCall` record. The headless `TuringToolActivity` atom renders the
 * running/finished tool list from these. Emitted only when the agent opts into
 * `toolCallEventsEnabled`; arguments are redacted/truncated server-side.
 */
export interface TurChatToolCall {
  /** Unique id of this invocation within the turn (correlates start/end). */
  callId: string;
  /** Tool name (matches the server `ToolDefinition.name()`). */
  name: string;
  /** `"start"` when the tool begins, `"end"` when it completes or fails. */
  phase: "start" | "end";
  /** Redacted/truncated argument digest — present on `start`; never raw secrets. */
  argsSummary?: string | null;
  /** `"ok"` | `"error"` — present on `end`. */
  status?: string | null;
  /** End-to-end call duration in ms — present on `end`. */
  durationMs?: number | null;
}

/**
 * T292 — provider-agnostic provenance for one retrieved RAG chunk, carried by
 * the `"sources"` SSE event. Mirrors the backend `TurRagSource` record. Powers
 * the source-chip UI (T293 / T332): chips group by `sourceId`/`url` and expand
 * to the cited chunks, with `score` driving a confidence cue.
 */
export interface TurChatSource {
  /** Stable identifier of the source document. */
  sourceId: string;
  /** Human-friendly chip label (document title, file name, or id). */
  title: string;
  /** Deep link to the source when available. */
  url?: string | null;
  /** Zero-based chunk index within the source document, when recorded. */
  chunkIndex?: number | null;
  /** Retrieval score (cosine similarity / BM25-RRF); drives the confidence cue. */
  score?: number | null;
  /** `true` when this chunk reached the answer via the keyword (BM25) fallback only. */
  keywordOnly?: boolean;
}

/**
 * T634/T635 — a persona the anonymous demo may select ("same question,
 * different eyes"). Mirrors an entry of the site agent's persona catalog.
 */
export interface TurPersonaOption {
  readonly id: string;
  readonly name: string;
  /**
   * T717 / §XLVI.1 — optional Big Five (OCEAN) personality traits (0–100 each).
   * Present only when the persona has opted in; a consumer may surface them (e.g.
   * a trait badge). The traits already shape the persona's answers server-side, so
   * this metadata is display-only — omitting it changes nothing in the chat.
   */
  readonly personality?: TurPersonaPersonality;
}

/** T717 — opt-in Big Five (OCEAN) trait scores, 0–100 each. */
export interface TurPersonaPersonality {
  readonly openness?: number | null;
  readonly conscientiousness?: number | null;
  readonly extraversion?: number | null;
  readonly agreeableness?: number | null;
  readonly neuroticism?: number | null;
}

/**
 * T634/T635 — one flagged span from the content-fit verdict (<em>não condiz</em>):
 * the offending `span`, why it doesn't fit the audience (`reason`), and a
 * suggested rewrite. Mirrors the backend `TurContentFitMisfit`.
 */
export interface TurContentFitMisfit {
  readonly span: string;
  readonly reason: string;
  readonly suggestion: string;
}

/**
 * T634/T635 — the "validate this content as persona X" verdict returned by
 * `fetchPersonaContentFit`. Mirrors the backend public response: overall fit %
 * (`fitScore`), a `summary`, what does fit (`fits` / <em>condiz</em>), the
 * flagged `misfits` (<em>não condiz</em>), and whether the LLM contributed
 * (`llmUsed` false = deterministic readability-only fallback).
 */
export interface TurContentFit {
  readonly personaId: string;
  readonly personaName: string;
  /** Overall fit on a 0–100 scale (already a percentage). */
  readonly fitScore: number;
  readonly summary: string;
  readonly fits: string[];
  readonly misfits: TurContentFitMisfit[];
  readonly llmUsed: boolean;
}

/**
 * T152 / §X.7.a — one per-sentence citation returned by Anthropic Citations,
 * carried by the `"citations"` SSE event. Mirrors the backend `TurChatCitation`
 * record. The citation-aware chat UI (T154) underlines `citedText` in the
 * answer and deep-links to `url`.
 */
export interface TurChatCitation {
  /** Zero-based index into the document blocks sent for the turn. */
  documentIndex: number;
  /** Stable id of the cited source (resolved from the retrieved passage). */
  sourceId?: string | null;
  /** Title Claude echoed back, or the source title as a fallback. */
  documentTitle?: string | null;
  /** Deep link to the cited source, when available. */
  url?: string | null;
  /** The exact span of the source the claim was grounded on. */
  citedText: string;
  /** Start char offset (`char`) or start page (`page`), when present. */
  startIndex?: number | null;
  /** End char offset (`char`) or end page (`page`), when present. */
  endIndex?: number | null;
  /** `"char"`, `"page"` (PDF), or `"search_result"` location type. */
  locationType?: string;
  /**
   * T154 — start char offset of the cited claim in the assistant answer text.
   * The citation-aware UI underlines `answer.slice(answerStart, answerEnd)`.
   */
  answerStart?: number | null;
  /** T154 — end char offset of the cited claim in the assistant answer text. */
  answerEnd?: number | null;
}

/**
 * A single field of a native multi-field form (T107), mirrored from the
 * backend `ChatFlowNode.FormField` record.
 */
export interface TurChatFormField {
  /** Slot the captured value is written to. */
  name: string;
  /** Human-readable label rendered above the input. */
  label?: string;
  /**
   * Input widget hint: `text` (default), `email`, `tel`, `number`, `date`,
   * `textarea`, or `select`. Unknown values degrade to a plain text input.
   */
  type?: string;
  /** When `false`, the field is optional and does not block submission. */
  required?: boolean;
  /** Optional input placeholder text. */
  placeholder?: string;
  /** Validation-rule hint (`email`, `phone`, `cpf`, …) for client-side checks. */
  validationRule?: string;
  /** Choice labels for a `select` field; ignored for other types. */
  options?: string[];
}

/**
 * Structured form schema carried by a `"form"` SSE event (T107). The portal
 * renders these fields as a native form and submits every value at once via
 * {@link createChatController}'s `submitForm`.
 */
export interface TurChatForm {
  /** The form node's id (audit/debug metadata). */
  nodeId?: string;
  /** Prompt/heading shown above the form (the node's instruction or label). */
  title?: string;
  /** The fields to render, in order. */
  fields: TurChatFormField[];
}

/** Aggregated response returned once an SSE chat stream is fully consumed. */
export interface TurChatConversationResponse {
  role: "assistant";
  /** The assistant's text (all `"token"` events concatenated). */
  content: string;
  /** Suggested chip labels emitted by the chat-flow engine for the next turn. */
  options?: string[];
  /** Native multi-field form to render for the next turn (T107), if any. */
  form?: TurChatForm;
  /** RAG provenance behind the answer (T292), if any. */
  sources?: TurChatSource[];
  /** Per-sentence Anthropic citations grounding the answer (T152), if any. */
  citations?: TurChatCitation[];
  /**
   * T490 / §X.19 — Google Search Suggestion chips for a Gemini `google_search`
   * grounded answer, if any. Google's ToS require rendering these whenever the
   * grounded answer is shown.
   */
  searchSuggestions?: TurSearchSuggestions;
  /**
   * T178 / §X.13.a — the OpenAI reasoning model's summary of how it reached the
   * answer (the `"reasoning"` SSE event), if the agent opted into the
   * `reasoning-summary` request option. The UI renders it as the collapsible
   * "Why this answer" panel.
   */
  reasoning?: string;
  /**
   * Tool calls the agent made during the turn (T436), merged by `callId` so
   * each entry carries its latest phase/status/duration. Present only when the
   * agent has `toolCallEventsEnabled`.
   */
  toolCalls?: TurChatToolCall[];
  /**
   * T438 — set when the turn parked on a frontend ("client") tool: the stream
   * ended with a `client_tool_call` event instead of a final answer. The SDK
   * runs the registered handler and resumes via `/v2/chat/client-tool-result`.
   */
  clientToolCall?: TurClientToolCall;
  /**
   * T516 / §XXVIII.12 — the answer-grounding guardrail verdict, present only
   * when the guardrail is enabled and flagged the answer (ungrounded / unsafe /
   * PII). The UI renders a confidence badge beside the answer; a clean answer
   * leaves this undefined.
   */
  grounding?: TurChatGrounding;
  /**
   * T522 / §XXVIII.18 — the cross-vendor "second opinion" verdict, present only
   * when the check is enabled and produced a signal. The UI renders an
   * agrees/disagrees confidence badge beside the answer.
   */
  secondOpinion?: TurChatSecondOpinion;
}

/** Result of {@link createChatController}'s `submitForm` round-trip (T107). */
export interface TurChatFormSubmitResponse {
  success: boolean;
  fieldsWritten: number;
  advancedStates: number;
  currentNodeId?: string | null;
  error?: string | null;
}

// ── Intents (curated quick prompts) ──

export interface TurIntentAction {
  id?: string;
  label: string;
  prompt: string;
  sortOrder?: number;
}

export interface TurIntent {
  id?: string;
  title: string;
  description?: string;
  /** Iconify identifier in the format `<prefix>:<name>` (e.g. `tabler:bulb`) */
  icon?: string;
  /** 1 = enabled, 0 = disabled */
  enabled: number;
  sortOrder?: number;
  actions: TurIntentAction[];
}

// ── Search State ──

export type SearchStatus = "idle" | "loading" | "success" | "error";

export interface SearchState {
  status: SearchStatus;
  data: TurSearchResponse | null;
  error: string | null;
}

// ── Document Field Helpers ──

export interface ResolvedDocument {
  url: string;
  title: string;
  description: string;
  date: string;
  image: string;
  text: string;
  raw: TurDocument;
}

/** A resolved group with documents mapped to default fields */
export interface ResolvedGroup {
  name: string;
  count: number;
  page: number;
  pageCount: number;
  limit: number;
  documents: ResolvedDocument[];
  pagination: TurPaginationItem[];
}
