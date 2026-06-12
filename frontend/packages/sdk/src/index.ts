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
  postLlmChat,
  deleteSiteConversationState,
  deleteAgentFlowState,
  fetchIntents,
  fetchSiteChatSlots,
  fetchAgentChatSlots,
  postSiteChatSlot,
  postSiteSlotExtract,
  fetchSiteChatState,
  postSiteHandoff,
  postSiteFlowSelect,
  postSiteFormSubmit,
  fetchLlmInstances,
  fetchLlmContextInfo,
  fetchAgentContextInfo,
  fetchAutoComplete,
  fetchSortOptions,
  parseHrefToParams,
  createTuringApi,
  CHAT_DISABLED_HINTS,
} from "./api";
export type {
  SearchParams,
  ClickTrackParams,
  ChatEnabledResponse,
  ChatDisabledReason,
  PostChatConversationOptions,
  PostAgentChatOptions,
  PostLlmChatOptions,
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

// ── Controllers ──
export { createSearchController } from "./controllers/search";
export type {
  SearchController,
  SearchControllerState,
} from "./controllers/search";
export { createChatController } from "./controllers/chat";
export type {
  ChatController,
  ChatControllerState,
  ChatControllerOptions,
  ChatControllerAgent,
  ChatMessage,
  ChatStatus,
  SendOverrides,
} from "./controllers/chat";
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
  TurChatSource,
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
