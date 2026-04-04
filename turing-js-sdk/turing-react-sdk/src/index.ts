// ── Core ──
export { TuringProvider } from "./core/provider";
export { useTuringContext } from "./core/use-turing-context";
export type { TuringContextValue } from "./core/context";

// ── API ──
export { fetchSearch, fetchChat, fetchAutoComplete, fetchSortOptions, parseHrefToParams } from "./core/api";
export type { SearchParams } from "./core/api";

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
  SearchStatus,
  SearchState,
  ResolvedDocument,
  ResultItemProps,
  FacetGroupProps,
  PaginationItemProps,
} from "./core/types";
