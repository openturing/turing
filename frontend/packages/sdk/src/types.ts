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
 * One Server-Sent Event emitted by the chat endpoints. `type === "options"`
 * carries a JSON-encoded `string[]` of chip labels; otherwise `content` is
 * assistant text to append.
 */
export interface TurChatStreamEvent {
  role: "assistant";
  content: string;
  type?: "token" | "options";
}

/** Aggregated response returned once an SSE chat stream is fully consumed. */
export interface TurChatConversationResponse {
  role: "assistant";
  /** The assistant's text (all `"token"` events concatenated). */
  content: string;
  /** Suggested chip labels emitted by the chat-flow engine for the next turn. */
  options?: string[];
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
