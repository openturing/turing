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
  fetchLlmInstances,
  fetchLlmContextInfo,
  fetchAgentContextInfo,
  postChatConversation,
  postAgentChat,
  postPersonaChat,
  postLlmChat,
  postSemanticChat,
  deleteSiteConversationState,
  deleteAgentFlowState,
  fetchAutoComplete,
  fetchIntents,
  fetchSiteChatSlots,
  fetchAgentChatSlots,
  postSiteChatSlot,
  postSiteSlotExtract,
  postSiteSlotExtractMulti,
  postSiteHandoff,
  postSiteFlowSelect,
  fetchPersonaContentFit,
  postSiteFormSubmit,
  fetchSiteChatState,
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
  postClick,
  fetchVoiceAvailability,
  postVoiceSession,
  postVoiceTranslation,
  CHAT_DISABLED_HINTS,
  LOW_CONFIDENCE_THRESHOLD,
} from "./core/api";
export type {
  SearchParams,
  PostChatConversationOptions,
  PostAgentChatOptions,
  PostPersonaChatOptions,
  PostLlmChatOptions,
  PostSemanticChatOptions,
  ChatEnabledResponse,
  ChatDisabledReason,
  ClickTrackParams,
  TurChatSessionSlots,
  TurWorkspaceArtifact,
  TurWorkspaceArtifacts,
  TurWorkspaceEvent,
  TurChatSlotWriteResponse,
  TurChatSlotExtractResponse,
  TurChatHandoffChannel,
  TurChatHandoffResponse,
  TurChatFlowSelectResponse,
  TurPersonaOption,
  TurPersonaPersonality,
  TurContentFit,
  TurContentFitMisfit,
  TurChatConversationState,
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
  TurVoiceAvailability,
  TurVoiceSession,
  PostVoiceSessionOptions,
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
export type { UseTuringSearchReturn, UseTuringSearchOptions } from "./hooks/use-turing-search";
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
export type { UseTuringChatReturn, UseTuringChatOptions, UseTuringChatAgent, ChatMessage, ChatStatus, SendOverrides } from "./hooks/use-turing-chat";
export { useGenerativeUI } from "./hooks/use-turing-generative-ui";
export type { UseGenerativeUIReturn } from "./hooks/use-turing-generative-ui";
export { useAnswerAsApp } from "./hooks/use-turing-answer-as-app";
export type { UseAnswerAsAppReturn } from "./hooks/use-turing-answer-as-app";
export { useCoBrowseSearch } from "./hooks/use-turing-co-browse";
export type { UseCoBrowseReturn } from "./hooks/use-turing-co-browse";
export { useTuringProactiveCopilot } from "./hooks/use-turing-proactive-copilot";
export type {
  TuringProactiveSuggestion,
  UseTuringProactiveCopilotOptions,
  UseTuringProactiveCopilotReturn,
} from "./hooks/use-turing-proactive-copilot";
// T792 / §LIV.3 (Block BF) — Vectorless (Structured-Data) RAG copilot.
export { useTuringCopilot } from "./hooks/use-turing-copilot";
export type {
  UseTuringCopilotOptions,
  UseTuringCopilotReturn,
  CopilotMessage,
  CopilotStatus,
} from "./hooks/use-turing-copilot";
export type { TurCatalogCitation, TurCatalogCopilotResult } from "./core/api";
export { useTuringUserMemory } from "./hooks/use-turing-user-memory";
export type {
  TuringUserMemory,
  UseTuringUserMemoryOptions,
  UseTuringUserMemoryReturn,
} from "./hooks/use-turing-user-memory";
export { useTuringLlmChat } from "./hooks/use-turing-llm-chat";
export type {
  UseTuringLlmChatReturn,
  UseTuringLlmChatOptions,
  LlmSendOverrides,
} from "./hooks/use-turing-llm-chat";

// ── Analytics (Block Z, T463) ──
export { useTuringAnalytics } from "./hooks/use-turing-analytics";
export type {
  UseTuringAnalyticsOptions,
  UseTuringAnalyticsReturn,
} from "./hooks/use-turing-analytics";
export {
  createTuringAnalytics,
  TURING_ANALYTICS_EVENTS,
} from "./core/analytics";
export type {
  TuringAnalytics,
  TuringAnalyticsOptions,
  TuringAnalyticsContext,
  TuringAnalyticsEvent,
  TuringAnalyticsEventName,
  TuringAnalyticsSink,
  TuringAnalyticsValue,
} from "./core/analytics";
export { googleAnalyticsSink, onEventSink, debugSink } from "./core/analytics-sinks";
export type {
  GoogleAnalyticsSinkOptions,
  DebugSinkOptions,
} from "./core/analytics-sinks";
export { createAbandonmentWatcher } from "./core/analytics-lifecycle";
export type {
  AbandonmentOptions,
  AbandonmentReason,
  AbandonmentWatcher,
} from "./core/analytics-lifecycle";
export { useTuringSemanticChat } from "./hooks/use-turing-semantic-chat";
export type {
  UseTuringSemanticChatReturn,
  UseTuringSemanticChatOptions,
} from "./hooks/use-turing-semantic-chat";
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
export type {
  UseTuringSlotReturn,
  UseTuringSlotOptions,
  UseTuringSlotParseMode,
} from "./hooks/use-turing-slot";
export { useTuringWorkspace } from "./hooks/use-turing-workspace";
export type {
  UseTuringWorkspaceReturn,
  UseTuringWorkspaceOptions,
  UseTuringWorkspaceAgent,
  WorkspaceStatus,
} from "./hooks/use-turing-workspace";
export { useTuringRoutineWaiting } from "./hooks/use-turing-routine-waiting";
export type {
  UseTuringRoutineWaitingReturn,
  UseTuringRoutineWaitingOptions,
} from "./hooks/use-turing-routine-waiting";
export { useTuringScheduleAgentWaiting } from "./hooks/use-turing-schedule-agent-waiting";
export type {
  UseTuringScheduleAgentWaitingReturn,
  UseTuringScheduleAgentWaitingOptions,
} from "./hooks/use-turing-schedule-agent-waiting";
export { useTuringLlmInstances } from "./hooks/use-turing-llm-instances";
export type {
  UseTuringLlmInstancesReturn,
  UseTuringLlmInstancesOptions,
} from "./hooks/use-turing-llm-instances";
export { useTuringSlotWriter } from "./hooks/use-turing-slot-writer";
export type {
  UseTuringSlotWriterReturn,
  UseTuringSlotWriterOptions,
  SlotWriteStatus,
} from "./hooks/use-turing-slot-writer";
export { useTuringSlotExtract } from "./hooks/use-turing-slot-extract";
export type {
  UseTuringSlotExtractReturn,
  UseTuringSlotExtractOptions,
  SlotExtractStatus,
} from "./hooks/use-turing-slot-extract";
export { useTuringHandoff } from "./hooks/use-turing-handoff";
export type {
  UseTuringHandoffReturn,
  UseTuringHandoffOptions,
  HandoffStatus,
} from "./hooks/use-turing-handoff";
export { useTuringVoice } from "./hooks/use-turing-voice";
export type {
  UseTuringVoiceReturn,
  UseTuringVoiceOptions,
} from "./hooks/use-turing-voice";
export { useTuringRealtimeVoice } from "./hooks/use-turing-realtime-voice";
export type {
  UseTuringRealtimeVoiceReturn,
  UseTuringRealtimeVoiceOptions,
  RealtimeVoiceStatus,
  RealtimeToolCall,
} from "./hooks/use-turing-realtime-voice";
export { useTuringFlowState } from "./hooks/use-turing-flow-state";
export type {
  UseTuringFlowStateReturn,
  UseTuringFlowStateOptions,
} from "./hooks/use-turing-flow-state";
export { useTuringExperiment } from "./hooks/use-turing-experiment";
export type { UseTuringExperimentReturn } from "./hooks/use-turing-experiment";
export { useTuringFlowChooser } from "./hooks/use-turing-flow-chooser";
export type {
  UseTuringFlowChooserReturn,
  UseTuringFlowChooserOptions,
  FlowChooserStatus,
} from "./hooks/use-turing-flow-chooser";
export { useTuringClickTracking } from "./hooks/use-turing-click-tracking";
export type { UseTuringClickTrackingReturn } from "./hooks/use-turing-click-tracking";

// ── Block S — SDK parity hooks (v2026.3.4) ──
export { useTuringSimilar } from "./hooks/use-turing-similar";
export type {
  UseTuringSimilarReturn,
  UseTuringSimilarOptions,
} from "./hooks/use-turing-similar";
export { useTuringSpellCheck } from "./hooks/use-turing-spell-check";
export { useTuringRelatedTerms } from "./hooks/use-turing-related-terms";
export type {
  UseTuringRelatedTermsReturn,
  UseTuringRelatedTermsOptions,
} from "./hooks/use-turing-related-terms";
export type {
  UseTuringSpellCheckReturn,
  UseTuringSpellCheckOptions,
} from "./hooks/use-turing-spell-check";
export { useTuringSlotUpload } from "./hooks/use-turing-slot-upload";
export type {
  UseTuringSlotUploadReturn,
  UseTuringSlotUploadOptions,
  SlotUploadStatus,
} from "./hooks/use-turing-slot-upload";
export { useTuringResume } from "./hooks/use-turing-resume";
export type {
  UseTuringResumeReturn,
  UseTuringResumeOptions,
  ResumeStatus,
} from "./hooks/use-turing-resume";
export { useTuringDslSearch } from "./hooks/use-turing-dsl-search";
export type {
  UseTuringDslSearchReturn,
  UseTuringDslSearchOptions,
  DslSearchStatus,
} from "./hooks/use-turing-dsl-search";
export { useTuringPlatformInfo } from "./hooks/use-turing-platform-info";
export type {
  UseTuringPlatformInfoReturn,
  UseTuringPlatformInfoOptions,
} from "./hooks/use-turing-platform-info";

// ── UI Components ──
// TuringSearchBar / TuringResultList / TuringPagination moved to
// @viglet/turing-react-ui (T306) and are re-exported below alongside the other
// headless components, so SDK consumers' imports are unchanged.
export { TuringSearchField } from "./ui/TuringSearchField";
export { TuringWorkspacePanel } from "./ui/TuringWorkspacePanel";
export type { TuringWorkspacePanelProps } from "./ui/TuringWorkspacePanel";
export { TuringCopilot } from "./ui/TuringCopilot";
export type { TuringCopilotProps, TuringCopilotClassNames } from "./ui/TuringCopilot";

// ── Headless UI (re-exported from @viglet/turing-react-ui) ──
// Single import surface for the admin console: it can pull the design-agnostic
// chat / rich-content components from this SDK alongside the API hooks above.
// react-ui itself stays axios-free so viglet.com keeps consuming it directly
// (core SDK + react-ui + its own Next transport) without ever seeing axios.
// react-markdown / @terrastruct/d2 are optional peers here, mirroring react-ui:
// the components that need them lazy-load them, so re-exporting adds no hard dep.
export {
  TuringHtmlSandbox,
  TuringRichContent,
  splitRichContent,
  TuringMarkdown,
  resolveSandboxUrl,
  TuringD2Diagram,
  TuringCopyButton,
  TuringThinkingDots,
  TuringCodeBlock,
  TuringChatMessage,
  TuringSourceChips,
  TuringToolActivity,
  TuringGenerativeContent,
  TuringCitedAnswer,
  segmentCitedAnswer,
  TuringSearchBar,
  TuringResultList,
  TuringPagination,
  TuringSearchFieldView,
  TuringWorkspacePanelView,
  formatBytes,
  TURING_UI_THEME_CLASS,
  TURING_UI_TOKENS,
} from "@viglet/turing-react-ui";
export type {
  TuringHtmlSandboxProps,
  TuringHtmlSandboxClassNames,
  TuringHtmlSandboxLabels,
  TuringHtmlSandboxIcons,
  TuringRichContentProps,
  TuringRichContentClassNames,
  TuringRichSegment,
  TuringMarkdownSegmentProps,
  TuringD2Props,
  TuringHtmlSegmentProps,
  TuringMarkdownProps,
  TuringD2DiagramProps,
  TuringD2DiagramClassNames,
  TuringD2DiagramLabels,
  TuringD2Renderer,
  TuringCopyButtonProps,
  TuringCopyButtonClassNames,
  TuringCopyButtonLabels,
  TuringCopyButtonIcons,
  TuringThinkingDotsProps,
  TuringThinkingDotsClassNames,
  TuringCodeBlockProps,
  TuringCodeBlockClassNames,
  TuringCodeBlockLabels,
  TuringChatMessageProps,
  TuringChatMessageRole,
  TuringChatMessageClassNames,
  TuringSourceChipsProps,
  TuringSourceChipsClassNames,
  TuringSourceChipsLabels,
  TuringSourceChipsIcons,
  TuringRagSource,
  TuringSourceConfidence,
  TuringToolActivityProps,
  TuringToolActivityClassNames,
  TuringToolActivityLabels,
  TuringToolActivityIcons,
  TuringToolCall,
  TuringGenerativeContentProps,
  TuringGenerativeContentClassNames,
  TuringGenerativeItem,
  TuringGenerativeComponentProps,
  TuringGenerativeRegistry,
  TuringCitedAnswerProps,
  TuringCitedAnswerClassNames,
  TuringCitation,
  TuringCitedMark,
  TuringSearchBarProps,
  TuringSearchBarInputProps,
  TuringResultListProps,
  TuringResolvedDocument,
  TuringRawDocument,
  TuringPaginationProps,
  TuringPaginationItemData,
  TuringSearchFieldViewProps,
  TuringSearchFieldViewContextValue,
  TuringSearchFieldViewInputProps,
  TuringSearchFieldViewButtonProps,
  TuringSearchFieldViewDropdownProps,
  TuringWorkspacePanelViewProps,
  TuringWorkspaceArtifact,
  TuringWorkspaceStatus,
  TuringUiTheme,
  TuringUiTokenName,
} from "@viglet/turing-react-ui";

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
  TurSearchSuggestions,
  TurChatToolCall,
  TurChatForm,
  TurChatFormField,
  TurChatFormSubmitResponse,
  TurIntent,
  TurIntentAction,
  TurLlmInstance,
  TurLlmVendor,
  TurLlmContextInfo,
  SearchStatus,
  SearchState,
  ResolvedDocument,
  ResolvedGroup,
  TurGroupBean,
  ResultItemProps,
  FacetGroupProps,
  PaginationItemProps,
} from "./core/types";
