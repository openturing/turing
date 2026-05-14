// ── Provider Configuration ──

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

// ── Chat ──

export interface TurChatResponse {
  text: string;
  enabled: boolean;
}

export interface TurChatConversationMessage {
  role: "user" | "assistant";
  content: string;
}

/**
 * One Server-Sent Event emitted by `POST /sn/{site}/chat/conversation`.
 * The backend currently flushes a single text event with the full content
 * after the LLM tool-calling loop completes (future server changes may
 * stream incremental tokens — aggregators should append, not replace), and
 * optionally a second event with {@code type === "options"} carrying the
 * suggested chip labels for the next user turn.
 *
 * @since 2026.2.15 (extended in 2026.2.17 with {@code type})
 */
export interface TurChatStreamEvent {
  role: "assistant";
  content: string;
  /**
   * Payload discriminator:
   * - `"token"` (or omitted, for older servers): {@link content} is the
   *   assistant text. Append to the running buffer.
   * - `"options"`: {@link content} is a JSON-encoded `string[]` — the chip
   *   labels to render below the just-finished assistant bubble. Picking
   *   one sends that label as the next user message.
   */
  type?: "token" | "options";
}

/**
 * Aggregated response returned by `postChatConversation` once the SSE
 * stream is fully consumed.
 *
 * @since 2026.2.15
 */
export interface TurChatConversationResponse {
  role: "assistant";
  /** The assistant's text (all `"token"` events concatenated). */
  content: string;
  /**
   * Suggested chip labels emitted by the chat-flow engine for the next turn
   * — empty when the current node has no `switchOptions` or `inlineOptions`.
   * Picking one is equivalent to typing that label as the user message.
   *
   * @since 2026.2.17
   */
  options?: string[];
}

// ── Intents (curated quick prompts) ──

export interface TurIntentAction {
  /** Stable id from the backend */
  id?: string;
  /** Short visible label rendered on the chip */
  label: string;
  /** Full prompt text sent to the LLM when the chip is clicked */
  prompt: string;
  /** Sort order assigned by the author */
  sortOrder?: number;
}

export interface TurIntent {
  id?: string;
  /** Category title (e.g. "Cursos", "Inscrições") */
  title: string;
  /** Optional admin-facing description */
  description?: string;
  /** Iconify identifier in the format `<prefix>:<name>` (e.g. `tabler:bulb`) */
  icon?: string;
  /** 1 = enabled, 0 = disabled */
  enabled: number;
  /** Sort order assigned by the author */
  sortOrder?: number;
  /** Quick-prompt actions for this intent */
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

// ── UI Component Props ──

export interface ResultItemProps {
  document: ResolvedDocument;
  raw: TurDocument;
  index: number;
}

export interface FacetGroupProps {
  group: TurFacetGroup;
  isFacetItemTypeOr: boolean;
  onNavigate: (href: string) => void;
}

export interface PaginationItemProps {
  item: TurPaginationItem;
  isActive: boolean;
  onClick: () => void;
}
