/**
 * Domain types for the React SDK.
 *
 * <p>The raw API + resolved domain types are now owned by the
 * framework-agnostic {@code @viglet/turing-sdk} package (single source of
 * truth). This module re-exports them so existing `../core/types` imports keep
 * working, and adds the React/UI-component prop types that only matter to this
 * package's bundled components.
 *
 * @since 2026.3.1
 */
export type {
  TuringConfig,
  TurSearchResponse,
  TurGroupBean,
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
  TurLlmVendor,
  TurLlmInstance,
  TurLlmContextInfo,
  TurChatResponse,
  TurChatConversationMessage,
  TurChatStreamEvent,
  TurChatSource,
  TurChatConversationResponse,
  TurChatForm,
  TurChatFormField,
  TurChatFormSubmitResponse,
  TurIntentAction,
  TurIntent,
  SearchStatus,
  SearchState,
  ResolvedDocument,
  ResolvedGroup,
} from "@viglet/turing-sdk";

import type {
  ResolvedDocument,
  TurDocument,
  TurFacetGroup,
  TurPaginationItem,
} from "@viglet/turing-sdk";

// ── UI Component Props (React-specific — kept local) ──

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
