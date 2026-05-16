// ── Core ──
export { TuringProvider } from "./core/provider";
export { useTuringContext } from "./core/use-turing-context";
export { useSearchStore } from "./core/use-search-store";
export type { TuringContextValue, SearchStoreValue } from "./core/context";

// ── API ──
export {
  fetchSearch,
  fetchChat,
  fetchChatEnabled,
  postChatConversation,
  postAgentChat,
  deleteSiteConversationState,
  deleteAgentFlowState,
  fetchAutoComplete,
  fetchIntents,
  fetchSiteChatSlots,
  fetchAgentChatSlots,
  postSiteChatSlot,
  fetchSortOptions,
  parseHrefToParams,
  postClick,
  CHAT_DISABLED_HINTS,
} from "./core/api";
export type {
  SearchParams,
  PostChatConversationOptions,
  PostAgentChatOptions,
  ChatEnabledResponse,
  ChatDisabledReason,
  ClickTrackParams,
  TurChatSessionSlots,
  TurChatSlotWriteResponse,
} from "./core/api";

// ── Session ──
export {
  getOrCreateTurSession,
  readTurSession,
  clearTurSession,
  TUR_SESSION_DEFAULT_COOKIE_NAME,
  TUR_SESSION_DEFAULT_TTL_SECONDS,
} from "./core/session";
export type { TurSessionOptions } from "./core/session";

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
export type { UseTuringChatReturn, UseTuringChatOptions, UseTuringChatAgent, ChatMessage, ChatStatus } from "./hooks/use-turing-chat";
export { useTuringIntents } from "./hooks/use-turing-intents";
export type { UseTuringIntentsReturn } from "./hooks/use-turing-intents";
export { useTuringSlots } from "./hooks/use-turing-slots";
export type {
  UseTuringSlotsReturn,
  UseTuringSlotsOptions,
  UseTuringSlotsAgent,
  SlotsStatus,
} from "./hooks/use-turing-slots";
export { useTuringSlot } from "./hooks/use-turing-slot";
export type { UseTuringSlotReturn } from "./hooks/use-turing-slot";
export { useTuringSlotWriter } from "./hooks/use-turing-slot-writer";
export type {
  UseTuringSlotWriterReturn,
  UseTuringSlotWriterOptions,
  SlotWriteStatus,
} from "./hooks/use-turing-slot-writer";
export { useTuringClickTracking } from "./hooks/use-turing-click-tracking";
export type { UseTuringClickTrackingReturn } from "./hooks/use-turing-click-tracking";

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
