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
 * - otherwise `content` is assistant text to append.
 */
export interface TurChatStreamEvent {
  role: "assistant";
  content: string;
  type?: "token" | "options" | "form" | "sources";
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
