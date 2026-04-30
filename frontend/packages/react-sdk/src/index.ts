// ── Core ──
export { TuringProvider } from "./core/provider";
export { useTuringContext } from "./core/use-turing-context";
export { useSearchStore } from "./core/use-search-store";
export type { TuringContextValue, SearchStoreValue } from "./core/context";

// ── API ──
export { fetchSearch, fetchChat, fetchChatEnabled, postChatConversation, fetchAutoComplete, fetchIntents, fetchSortOptions, parseHrefToParams } from "./core/api";
export type { SearchParams } from "./core/api";

// ── Resolve utilities ──
export { resolveDocuments, resolveGroups } from "./core/resolve";

// ── Hooks ──
export { useTuringSearch } from "./hooks/use-turing-search";
export type { UseTuringSearchReturn } from "./hooks/use-turing-search";
export { useTuringAutoComplete } from "./hooks/use-turing-autocomplete";
export type { UseTuringAutoCompleteReturn } from "./hooks/use-turing-autocomplete";
export { useTuringSortOptions } from "./hooks/use-turing-sort-options";
export type { UseTuringSortOptionsReturn } from "./hooks/use-turing-sort-options";
export { useTuringUrlSearch } from "./hooks/use-turing-url-search";
export type { UseTuringUrlSearchReturn } from "./hooks/use-turing-url-search";
export { useTuringSearchHistory } from "./hooks/use-turing-search-history";
export type { UseTuringSearchHistoryReturn, SearchHistoryEntry } from "./hooks/use-turing-search-history";

// ── New composable hooks (v2026.3) ──
export { useTuringDocument } from "./hooks/use-turing-document";
export type { TuringDocumentHelper } from "./hooks/use-turing-document";
export { useTuringFacets } from "./hooks/use-turing-facets";
export type { UseTuringFacetsReturn, EnrichedFacetGroup, EnrichedFacetItem } from "./hooks/use-turing-facets";
export { useTuringPagination } from "./hooks/use-turing-pagination";
export type { UseTuringPaginationReturn, PaginationPage } from "./hooks/use-turing-pagination";
export { useTuringTabs } from "./hooks/use-turing-tabs";
export type { UseTuringTabsReturn, UseTuringTabsOptions, TabDefinition, EnrichedTab } from "./hooks/use-turing-tabs";
export { useTuringChat } from "./hooks/use-turing-chat";
export type { UseTuringChatReturn, UseTuringChatOptions, ChatMessage, ChatStatus } from "./hooks/use-turing-chat";
export { useTuringIntents } from "./hooks/use-turing-intents";
export type { UseTuringIntentsReturn } from "./hooks/use-turing-intents";

// ── UI Components ──
export { TuringSearchBar } from "./ui/TuringSearchBar";
export { TuringSearchField } from "./ui/TuringSearchField";
export { TuringResultList } from "./ui/TuringResultList";
export { TuringPagination } from "./ui/TuringPagination";

// ── Types ──
export type {
  TuringConfig,
  TurSearchResponse,
  TurQueryContext,
  TurDefaultFields,
  TurQueryMeta,
  TurSearchResults,
  TurDocument,
  TurDocumentMetadata,
  TurPaginationItem,
  TurWidget,
  TurFacetGroup,
  TurFacetItem,
  TurSpellCheck,
  TurLocaleItem,
  TurSortOption,
  TurChatResponse,
  TurChatConversationMessage,
  TurChatConversationResponse,
  TurChatStreamEvent,
  TurIntent,
  TurIntentAction,
  SearchStatus,
  SearchState,
  ResolvedDocument,
  ResolvedGroup,
  TurGroupBean,
  ResultItemProps,
  FacetGroupProps,
  PaginationItemProps,
} from "./core/types";
