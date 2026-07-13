/**
 * `@viglet/turing-sdk` — vanilla JS SDK for Viglet Turing ES.
 *
 * Framework-agnostic, zero-dependency. Provides an explicit HTTP client, the
 * full Turing REST/SSE API surface, and observable controllers (search, chat,
 * autocomplete, slots) for the common front-end flows. Works in Adobe Edge
 * Delivery Services blocks, plain `<script>`, or any bundler.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

// ── Client ──
export { createTuringClient } from "./client";
export type {
  TuringClient,
  TuringClientConfig,
  TuringRequestOptions,
} from "./client";

// ── API (functions + bound helper) ──
export {
  fetchSearch,
  postClick,
  fetchChat,
  fetchChatEnabled,
  postChatConversation,
  postAgentChat,
  postPersonaChat,
  postClientToolResult,
  postLlmChat,
  postSemanticChat,
  deleteSiteConversationState,
  deleteAgentFlowState,
  fetchIntents,
  fetchSiteChatSlots,
  fetchAgentChatSlots,
  postSiteChatSlot,
  postSiteSlotExtract,
  postSiteSlotExtractMulti,
  fetchSiteChatState,
  postSiteHandoff,
  postSiteFlowSelect,
  fetchPersonaContentFit,
  postSiteFormSubmit,
  fetchLlmInstances,
  fetchLlmContextInfo,
  fetchAgentContextInfo,
  fetchAutoComplete,
  fetchSortOptions,
  fetchSimilar,
  postSiteSlotUpload,
  postSiteChatResume,
  fetchSpellCheck,
  fetchRelatedTerms,
  dslSearch,
  dslQuery,
  fetchDiscovery,
  fetchFeatures,
  fetchSystemLocales,
  fetchLlmVendors,
  postSummary,
  parseHrefToParams,
  createTuringApi,
  CHAT_DISABLED_HINTS,
  LOW_CONFIDENCE_THRESHOLD,
} from "./api";
export type {
  SearchParams,
  ClickTrackParams,
  ChatEnabledResponse,
  ChatDisabledReason,
  PostChatConversationOptions,
  PostAgentChatOptions,
  PostPersonaChatOptions,
  PostClientToolResultOptions,
  ClientToolResultBody,
  PostLlmChatOptions,
  PostSemanticChatOptions,
  TurChatSessionSlots,
  TurChatSessionSlotsDelta,
  TurWorkspaceArtifact,
  TurWorkspaceEvent,
  TurWorkspaceArtifacts,
  TurChatSlotWriteResponse,
  TurChatSlotExtractResponse,
  TurChatConversationState,
  TurChatHandoffChannel,
  TurChatHandoffResponse,
  TurChatFlowSelectResponse,
  TurSimilarMode,
  TurSimilarResult,
  FetchSimilarParams,
  TurChatSlotUploadResponse,
  PostSlotUploadOptions,
  SlotExtractOptions,
  TurChatResumeResponse,
  TurDslSearchRequest,
  TurDslSearchHit,
  TurDslSearchResponse,
  TurDiscoveryInfo,
  TurFeaturesInfo,
  TurSystemLocale,
  TurSummaryResult,
  TuringApi,
} from "./api";

// ── Session ──
export {
  getOrCreateTurSession,
  readTurSession,
  clearTurSession,
  TUR_SESSION_DEFAULT_COOKIE_NAME,
  TUR_SESSION_DEFAULT_TTL_SECONDS,
} from "./session";
export type { TurSessionOptions } from "./session";

// ── Resolve utilities ──
export { resolveDocuments, resolveGroups } from "./resolve";

// ── Sandbox artifact URLs (code-interpreter / workspace) ──
export { resolveSandboxUrl, isSandboxUrl, absolutizeArtifactUrls } from "./sandbox";

// ── Rich-content segmentation (```html live preview / games / charts) ──
export { splitRichContent, hasHtmlBlock } from "./rich-content";
export type { TurRichSegment } from "./rich-content";

// ── Slots SSE multiplexer (advanced) ──
export { subscribeSlotsSse } from "./slots-sse";
export type {
  SlotsSseSubscriber,
  SlotsSseSubscription,
  SlotsSseOptions,
} from "./slots-sse";

// ── Workspace SSE multiplexer (T113) ──
export { subscribeWorkspaceSse, _workspaceSseOpenChannelCount } from "./workspace-sse";
export type {
  WorkspaceSseSubscriber,
  WorkspaceSseSubscription,
  WorkspaceSseScope,
} from "./workspace-sse";

// ── Store ──
export { createStore } from "./store";
export type { Store, Listener } from "./store";

// ── Analytics event bus (T458, Block Z) ──
export { createTuringAnalytics, TURING_ANALYTICS_EVENTS } from "./analytics";
export type {
  TuringAnalytics,
  TuringAnalyticsOptions,
  TuringAnalyticsContext,
  TuringAnalyticsEvent,
  TuringAnalyticsEventName,
  TuringAnalyticsSink,
  TuringAnalyticsValue,
} from "./analytics";
// ── Analytics sinks: GA4/GTM, generic, debug (T459) ──
export { googleAnalyticsSink, onEventSink, debugSink } from "./analytics-sinks";
export type {
  GoogleAnalyticsSinkOptions,
  DebugSinkOptions,
} from "./analytics-sinks";
// ── Analytics lifecycle: abandonment watcher (T460) ──
export { createAbandonmentWatcher } from "./analytics-lifecycle";
export type {
  AbandonmentOptions,
  AbandonmentReason,
  AbandonmentWatcher,
} from "./analytics-lifecycle";

// ── Controllers ──
export { createSearchController } from "./controllers/search";
export type {
  SearchController,
  SearchControllerState,
  SearchControllerOptions,
} from "./controllers/search";
export { createChatController } from "./controllers/chat";
export type {
  ChatController,
  ChatControllerState,
  ChatControllerOptions,
  ChatControllerAgent,
  ChatControllerPersona,
  ChatMessage,
  ChatStatus,
  SendOverrides,
  ClientToolHandler,
  ClientToolRegistration,
} from "./controllers/chat";
// T450 — embeddable action widget: default host-action client tools.
export { createHostActions } from "./host-actions";
export type { HostActions, HostActionsOptions } from "./host-actions";
export { createAutoComplete } from "./controllers/autocomplete";
export type {
  AutoCompleteController,
  AutoCompleteState,
} from "./controllers/autocomplete";
export { createSlotsController } from "./controllers/slots";
export type {
  SlotsController,
  SlotsControllerState,
  SlotsControllerOptions,
  SlotsControllerScope,
  SlotsStatus,
} from "./controllers/slots";
export { createSimilarController } from "./controllers/similar";
export type {
  SimilarController,
  SimilarControllerState,
  SimilarControllerOptions,
  SimilarStatus,
} from "./controllers/similar";

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
  TurDuplicateCluster,
  TurDuplicateClusterMember,
  TurPaginationItem,
  TurWidget,
  TurFacetGroup,
  TurFacetItem,
  TurSpellCheck,
  TurRelatedTermSuggestion,
  TurLocaleItem,
  TurSortOption,
  TurChatResponse,
  TurChatConversationMessage,
  TurChatConversationResponse,
  TurChatStreamEvent,
  TurChatSource,
  TurChatCitation,
  TurChatGrounding,
  TurChatSecondOpinion,
  TurPersonaOption,
  TurPersonaPersonality,
  TurContentFit,
  TurContentFitMisfit,
  TurSearchSuggestions,
  TurChatToolCall,
  TurClientToolCall,
  TurChatForm,
  TurChatFormField,
  TurChatFormSubmitResponse,
  TurIntent,
  TurIntentAction,
  TurLlmInstance,
  TurLlmVendor,
  TurLlmContextInfo,
  TurGroupBean,
  SearchStatus,
  SearchState,
  ResolvedDocument,
  ResolvedGroup,
} from "./types";
