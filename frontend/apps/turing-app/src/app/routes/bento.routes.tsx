import { lazy } from "react";
import { Navigate, Route, useParams } from "react-router-dom";
import { ROUTES } from "../routes.const";

const BentoRootPage = lazy(() => import("../bento/bento.root.page"));
const BentoEvalPage = lazy(() => import("../bento/eval/bento.eval.page"));
const BentoHomePage = lazy(() => import("../bento/home/bento.home.page"));
const BentoAreaPage = lazy(() => import("../bento/area/bento.area.page"));
const BentoAIAgentListPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.list.page"));
const BentoAIAgentPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.page"));
// AI agent instance sub-sections in the bento shell (cards on the agent dashboard).
const BentoAIAgentSettingsPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.settings.page"));
const BentoAIAgentLlmPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.llm.page"));
const BentoAIAgentToolsPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.tools.page"));
const BentoAIAgentMcpPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.mcp.page"));
const BentoAIAgentCustomToolPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.custom-tool.page"));
const BentoAIAgentHistoryPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.history.page"));
const BentoAIAgentIntentListPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.intent.list.page"));
const BentoAIAgentIntentPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.intent.page"));
const BentoAIAgentChatFlowListPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.chat-flow.list.page"));
const BentoAIAgentLivePreviewPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.live-preview.page"));
const BentoAIAgentTriggerConflictsPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.trigger-conflicts.page"));
const BentoAIAgentAnalyticsIntentPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.analytics-intent.page"));
const BentoAIAgentSystemPromptPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.system-prompt.page"));
const BentoAIAgentPersonaPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.persona.page"));
const BentoAIAgentCapabilitiesPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.capabilities.page"));
const BentoAIAgentRequestOptionsPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.request-options.page"));
const BentoAIAgentSlotPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.slot.page"));
// Reused console pages, mounted in the bento shell via baseRoute/chrome props.
const IntentAiChatPage = lazy(() => import("../console/intent/ai-chat/intent.ai-chat.page"));
const ChatFlowPage = lazy(() => import("../console/chat-flow/chat-flow.page"));
const ChatFlowAiChatPage = lazy(() => import("../console/chat-flow/ai-chat/chat-flow.ai-chat.page"));
const BentoLLMInstanceListPage = lazy(() => import("../bento/llm/bento.llm.instance.list.page"));
const BentoLLMInstancePage = lazy(() => import("../bento/llm/bento.llm.instance.page"));
const BentoLLMAdvisorPage = lazy(() => import("../bento/llm/bento.llm.advisor.page"));
const BentoSEInstanceListPage = lazy(() => import("../bento/se/bento.se.instance.list.page"));
const BentoSEInstancePage = lazy(() => import("../bento/se/bento.se.instance.page"));
const BentoSECoresPage = lazy(() => import("../bento/se/bento.se.cores.page"));
const BentoSECoreNewPage = lazy(() => import("../bento/se/bento.se.core.new.page"));
const BentoSESystemInfoPage = lazy(() => import("../bento/se/bento.se.system-info.page"));
const BentoStoreInstanceListPage = lazy(() => import("../bento/store/bento.store.instance.list.page"));
const BentoStoreInstancePage = lazy(() => import("../bento/store/bento.store.instance.page"));
const BentoStoreSystemInfoPage = lazy(() => import("../bento/store/bento.store.system-info.page"));
const BentoStoreCollectionImportPage = lazy(() => import("../bento/store/bento.store.instance.collection.import.page"));
const BentoTokenInstanceListPage = lazy(() => import("../bento/token/bento.token.instance.list.page"));
const BentoTokenInstancePage = lazy(() => import("../bento/token/bento.token.instance.page"));
const BentoGatewayPage = lazy(() => import("../bento/gateway/bento.gateway.page"));
const BentoIntegrationInstanceListPage = lazy(() => import("../bento/integration/bento.integration.instance.list.page"));
const BentoIntegrationInstancePage = lazy(() => import("../bento/integration/bento.integration.instance.page"));
const BentoMcpServerListPage = lazy(() => import("../bento/mcp/bento.mcp.server.list.page"));
const BentoMcpServerPage = lazy(() => import("../bento/mcp/bento.mcp.server.page"));
const BentoCustomToolListPage = lazy(() => import("../bento/custom-tool/bento.custom-tool.list.page"));
const BentoCustomToolPage = lazy(() => import("../bento/custom-tool/bento.custom-tool.page"));
const BentoRoutineListPage = lazy(() => import("../bento/routine/bento.routine.list.page"));
const BentoRoutinePage = lazy(() => import("../bento/routine/bento.routine.page"));
const BentoWebhookListPage = lazy(() => import("../bento/webhook/bento.webhook.list.page"));
const BentoWebhookPage = lazy(() => import("../bento/webhook/bento.webhook.page"));
const BentoPersonaListPage = lazy(() => import("../bento/persona/bento.persona.list.page"));
const BentoPersonaPage = lazy(() => import("../bento/persona/bento.persona.page"));
// Block AI (T582) — persona detail split into an AI-agent-style dashboard
// landing + a shared-form section editor (both fed by the loader above).
const BentoPersonaDashboard = lazy(() => import("../bento/persona/bento.persona.dashboard"));
const BentoPersonaEditor = lazy(() => import("../bento/persona/bento.persona.editor"));
// Block AI (T584) — content-fit validation as a first-class launch surface.
const BentoPersonaValidatePage = lazy(() => import("../bento/persona/bento.persona.validate.page"));
// Block AU (T706) — global persona↔persona dialogue as saved multi-projects.
const BentoPersonaDialogueListPage = lazy(() => import("../bento/persona/dialogue/bento.persona.dialogue.list.page"));
const BentoPersonaDialogueWorkspacePage = lazy(() => import("../bento/persona/dialogue/bento.persona.dialogue.workspace.page"));
// Block AT (T701-T702) — Persona Match: N×N persona↔content fit projects (UI prototype).
const BentoPersonaMatchListPage = lazy(() => import("../bento/persona/match/bento.persona.match.list.page"));
const BentoPersonaMatchWorkspacePage = lazy(() => import("../bento/persona/match/bento.persona.match.workspace.page"));
// Block AW (T730) — Synthetic User Research: cohort interview studies.
const BentoPersonaResearchListPage = lazy(() => import("../bento/persona/research/bento.persona.research.list.page"));
const BentoPersonaResearchWorkspacePage = lazy(() => import("../bento/persona/research/bento.persona.research.workspace.page"));
// Block AW (T733) — multi-study program planner + cross-study rollup (PRISMA).
const BentoPersonaResearchProgramPage = lazy(() => import("../bento/persona/research/bento.persona.research.program.page"));
// Block AW (T731) — audience cohort synthesis from a one-paragraph brief.
const BentoPersonaCohortPage = lazy(() => import("../bento/persona/cohort/bento.persona.cohort.page"));
// Block AG follow-up (T619) — auto-suggest best-fit persona for content (T471).
const BentoPersonaSuggestPage = lazy(() => import("../bento/persona/bento.persona.suggest.page"));
const BentoGitPage = lazy(() => import("../bento/git/bento.git.page"));
const GitRepoPage = lazy(() => import("../console/git/git.repo.page"));
// Persona section sub-pages (reused from the console) — mounted under the
// Bento persona detail so its shared-form Outlet resolves inside Bento (T556).
const PersonaGeneralSection = lazy(() => import("../console/persona/sections/persona.instance.general.section"));
const PersonaSystemInstructionSection = lazy(() => import("../console/persona/sections/persona.instance.system-instruction.section"));
const PersonaStyleSection = lazy(() => import("../console/persona/sections/persona.instance.style.section"));
const PersonaAudienceSection = lazy(() => import("../console/persona/sections/persona.instance.audience.section"));
const PersonaNotebookSection = lazy(() => import("../console/persona/sections/persona.instance.notebook.section"));
const PersonaFitReportSection = lazy(() => import("../console/persona/sections/persona.instance.fit-report.section"));
const PersonaGuidelinesSection = lazy(() => import("../console/persona/sections/persona.instance.guidelines.section"));
// Phase 2 (T557) — Semantic Navigation instance CRUD in the bento shell.
const BentoSNInstanceListPage = lazy(() => import("../bento/sn/bento.sn.instance.list.page"));
const BentoSNInstancePage = lazy(() => import("../bento/sn/bento.sn.instance.page"));
// Phase 2 (T558) — SN fields sub-CRUD + field-coverage in the bento shell.
const BentoSNFieldListPage = lazy(() => import("../bento/sn/bento.sn.field.list.page"));
const BentoSNFieldPage = lazy(() => import("../bento/sn/bento.sn.field.page"));
const BentoSNFieldCoveragePage = lazy(() => import("../bento/sn/bento.sn.field.coverage.page"));
// SN instance sub-sections in the bento shell (T576).
const BentoSNInsightsPage = lazy(() => import("../bento/sn/bento.sn.insights.page"));
const BentoSNLocaleListPage = lazy(() => import("../bento/sn/bento.sn.locale.list.page"));
const BentoSNLocalePage = lazy(() => import("../bento/sn/bento.sn.locale.page"));
const BentoSNBehaviorPage = lazy(() => import("../bento/sn/bento.sn.behavior.page"));
const BentoSNFacetListPage = lazy(() => import("../bento/sn/bento.sn.facet.list.page"));
const BentoSNCustomFacetPage = lazy(() => import("../bento/sn/bento.sn.custom-facet.page"));
const BentoSNCustomFacetItemPage = lazy(() => import("../bento/sn/bento.sn.custom-facet.item.page"));
const BentoSNFacetedFieldPage = lazy(() => import("../bento/sn/bento.sn.faceted-field.page"));
const BentoSNCustomSortListPage = lazy(() => import("../bento/sn/bento.sn.custom-sort.list.page"));
const BentoSNCustomSortPage = lazy(() => import("../bento/sn/bento.sn.custom-sort.page"));
const BentoSNSearchRuleListPage = lazy(() => import("../bento/sn/bento.sn.search-rule.list.page"));
const BentoSNSearchRulePage = lazy(() => import("../bento/sn/bento.sn.search-rule.page"));
const BentoSNGenAiPage = lazy(() => import("../bento/sn/bento.sn.genai.page"));
const BentoSNThesaurusPage = lazy(() => import("../bento/sn/bento.sn.thesaurus.page"));
const BentoSNResultRankingListPage = lazy(() => import("../bento/sn/bento.sn.result-ranking.list.page"));
const BentoSNResultRankingPage = lazy(() => import("../bento/sn/bento.sn.result-ranking.page"));
const BentoSNMergeProvidersListPage = lazy(() => import("../bento/sn/bento.sn.merge-providers.list.page"));
const BentoSNMergeProvidersPage = lazy(() => import("../bento/sn/bento.sn.merge-providers.page"));
const BentoSNSpotlightListPage = lazy(() => import("../bento/sn/bento.sn.spotlight.list.page"));
const BentoSNSpotlightPage = lazy(() => import("../bento/sn/bento.sn.spotlight.page"));
const BentoSNSynonymListPage = lazy(() => import("../bento/sn/bento.sn.synonym.list.page"));
const BentoSNSynonymPage = lazy(() => import("../bento/sn/bento.sn.synonym.page"));
const BentoSNTopTermsPage = lazy(() => import("../bento/sn/bento.sn.top-terms.page"));
// Phase 2 (T559) — SN onboarding wizard in the bento shell.
const BentoSNOnboardingPage = lazy(() => import("../bento/sn/bento.sn.onboarding.page"));
// Phase 2 (T560) — skill folder editor (mini VS Code) in the bento shell.
const BentoSkillListPage = lazy(() => import("../bento/skill/bento.skill.list.page"));
const BentoSkillEditorPage = lazy(() => import("../bento/skill/bento.skill.editor.page"));
// Block AQ (§XL) — Thesaurus (controlled vocabulary) library.
const BentoThesaurusListPage = lazy(() => import("../bento/thesaurus/bento.thesaurus.list.page"));
const BentoThesaurusKbPage = lazy(() => import("../bento/thesaurus/bento.thesaurus.kb.page"));
const BentoThesaurusMicrothesaurusPage = lazy(
  () => import("../bento/thesaurus/bento.thesaurus.microthesaurus.page"),
);
// Phase 2 (T561) — asset browser/manager in the bento shell.
const BentoAssetPage = lazy(() => import("../bento/asset/bento.asset.page"));
// Phase 2 (T562) — Page CRUD (ZIP-deployed SPAs) in the bento shell.
const BentoPagePage = lazy(() => import("../bento/page/bento.page.page"));
// Phase 3 (T563) — chat workspace + conversation detail in the bento shell.
const BentoChatPage = lazy(() => import("../bento/chat/bento.chat.page"));
// Block AI (T580/T581) — persona chat workspace (talk directly to a persona).
const BentoPersonaChatPage = lazy(() => import("../bento/chat/bento.persona.chat.page"));
const BentoConversationPage = lazy(() => import("../bento/conversation/bento.conversation.page"));
// Phase 3 (T564) — AI Analytics suite (reuses the console sub-page content).
const BentoAiAnalyticsPage = lazy(() => import("../bento/ai-analytics/bento.ai-analytics.page"));
const BentoAiAnalyticsIndexRedirect = lazy(() => import("../bento/ai-analytics/bento.ai-analytics.index.redirect"));
const CostGovernancePage = lazy(() => import("../console/cost-governance/cost-governance.page"));
const ChatAnalyticsPage = lazy(() => import("../console/chat-analytics/chat-analytics.page"));
const ParkedConversationsPage = lazy(() => import("../console/parked-conversations/parked-conversations.page"));
const CapabilityMatrixPage = lazy(() => import("../console/capability-matrix/capability-matrix.page"));
// Phase 3 (T565) — GraphQL explorer + Dashboard + Marketplace.
const BentoGraphqlPage = lazy(() => import("../bento/graphql/bento.graphql.page"));
const BentoDashboardPage = lazy(() => import("../bento/dashboard/bento.dashboard.page"));
const BentoMarketplacePage = lazy(() => import("../bento/marketplace/bento.marketplace.page"));
// Phase 3 (T566) — Global Settings + Import + Logging + System Info.
const BentoGlobalSettingsPage = lazy(() => import("../bento/settings/bento.global-settings.page"));
const BentoImportPage = lazy(() => import("../bento/import/bento.import.page"));
const BentoLoggingPage = lazy(() => import("../bento/logging/bento.logging.page"));
const BentoSystemInfoPage = lazy(() => import("../bento/system-info/bento.system-info.page"));
const BentoLoggingSourcePage = lazy(() => import("../bento/logging/bento.logging.source.page"));
// Phase 4 (T567) — Admin settings (users/groups/roles) in the bento shell.
const BentoAdminUsersListPage = lazy(() => import("../bento/admin/bento.admin.users.list.page"));
const BentoAdminUserPage = lazy(() => import("../bento/admin/bento.admin.user.page"));
const BentoAdminGroupsListPage = lazy(() => import("../bento/admin/bento.admin.groups.list.page"));
const BentoAdminGroupPage = lazy(() => import("../bento/admin/bento.admin.group.page"));
const BentoAdminRolesListPage = lazy(() => import("../bento/admin/bento.admin.roles.list.page"));
const BentoAdminRolePage = lazy(() => import("../bento/admin/bento.admin.role.page"));
// Phase 4 (T568) — tenant settings + tenant admin + user account in the bento shell.
const BentoUserAccountPage = lazy(() => import("../bento/user/bento.user.account.page"));
const UserProfilePage = lazy(() => import("../console/user/profile/user.profile.page"));
const UserPreferencesPage = lazy(() => import("../console/user/preferences/user.preferences.page"));
const BentoTenantSettingsPage = lazy(() => import("../bento/tenant/bento.tenant.settings.page"));
const BentoTenantAdminPage = lazy(() => import("../bento/tenant/bento.tenant.admin.page"));

function RedirectToBentoIntentSettings() {
  const { id, intentId } = useParams();
  return <Navigate to={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${id}/intent/${intentId}/settings`} replace />;
}

export const BentoRoutes = (
  <Route path={ROUTES.BENTO_ROOT} element={<BentoRootPage />}>
    <Route index element={<Navigate to={ROUTES.BENTO_HOME} replace />} />
    <Route path="home" element={<BentoHomePage />} />
    {/* Section hubs (T573) — one shared page resolves its section from the path. */}
    <Route path="area/generative-ai" element={<BentoAreaPage />} />
    <Route path="area/enterprise-search" element={<BentoAreaPage />} />
    <Route path="area/management" element={<BentoAreaPage />} />
    <Route path="ai-agent" element={<Navigate to={ROUTES.BENTO_AI_AGENT_INSTANCE} replace />} />
    <Route path="ai-agent/instance" element={<BentoAIAgentListPage />} />
    <Route path="ai-agent/instance/:id" element={<BentoAIAgentPage />} />
    {/* AI agent instance sub-sections (dashboard cards) in the bento shell. */}
    <Route path="ai-agent/instance/:id/settings" element={<BentoAIAgentSettingsPage />} />
    <Route path="ai-agent/instance/:id/system-prompt" element={<BentoAIAgentSystemPromptPage />} />
    <Route path="ai-agent/instance/:id/llm" element={<BentoAIAgentLlmPage />} />
    <Route path="ai-agent/instance/:id/persona" element={<BentoAIAgentPersonaPage />} />
    <Route path="ai-agent/instance/:id/capabilities" element={<BentoAIAgentCapabilitiesPage />} />
    <Route path="ai-agent/instance/:id/request-options" element={<BentoAIAgentRequestOptionsPage />} />
    <Route path="ai-agent/instance/:id/slot" element={<BentoAIAgentSlotPage />} />
    <Route path="ai-agent/instance/:id/tools" element={<BentoAIAgentToolsPage />} />
    <Route path="ai-agent/instance/:id/mcp" element={<BentoAIAgentMcpPage />} />
    <Route path="ai-agent/instance/:id/custom-tool" element={<BentoAIAgentCustomToolPage />} />
    <Route path="ai-agent/instance/:id/history" element={<BentoAIAgentHistoryPage />} />
    <Route path="ai-agent/instance/:id/intent" element={<BentoAIAgentIntentListPage />} />
    <Route path="ai-agent/instance/:id/intent/new/ai-chat" element={<IntentAiChatPage baseRoute={ROUTES.BENTO_AI_AGENT_INSTANCE} chrome="bento" />} />
    <Route path="ai-agent/instance/:id/intent/:intentId" element={<RedirectToBentoIntentSettings />} />
    <Route path="ai-agent/instance/:id/intent/:intentId/settings" element={<BentoAIAgentIntentPage />} />
    <Route path="ai-agent/instance/:id/chat-flow" element={<BentoAIAgentChatFlowListPage />} />
    <Route path="ai-agent/instance/:id/chat-flow/new/ai-chat" element={<ChatFlowAiChatPage baseRoute={ROUTES.BENTO_AI_AGENT_INSTANCE} />} />
    <Route path="ai-agent/instance/:id/chat-flow/:flowId" element={<ChatFlowPage baseRoute={ROUTES.BENTO_AI_AGENT_INSTANCE} chrome="bento" />} />
    <Route path="ai-agent/instance/:id/live-preview" element={<BentoAIAgentLivePreviewPage />} />
    <Route path="ai-agent/instance/:id/trigger-conflicts" element={<BentoAIAgentTriggerConflictsPage />} />
    <Route path="ai-agent/instance/:id/analytics-intent" element={<BentoAIAgentAnalyticsIntentPage />} />
    <Route path="llm" element={<Navigate to={ROUTES.BENTO_LLM_INSTANCE} replace />} />
    <Route path="llm/instance" element={<BentoLLMInstanceListPage />} />
    <Route path="llm/advisor" element={<BentoLLMAdvisorPage />} />
    <Route path="llm/instance/:id" element={<BentoLLMInstancePage />} />
    <Route path="se" element={<Navigate to={ROUTES.BENTO_SE_INSTANCE} replace />} />
    <Route path="se/instance" element={<BentoSEInstanceListPage />} />
    <Route path="se/instance/:id" element={<BentoSEInstancePage />} />
    <Route path="se/instance/:id/cores" element={<BentoSECoresPage />} />
    <Route path="se/instance/:id/cores/new" element={<BentoSECoreNewPage />} />
    <Route path="se/instance/:id/system-info" element={<BentoSESystemInfoPage />} />
    <Route path="store" element={<Navigate to={ROUTES.BENTO_STORE_INSTANCE} replace />} />
    <Route path="store/instance" element={<BentoStoreInstanceListPage />} />
    <Route path="store/instance/:id" element={<BentoStoreInstancePage />} />
    <Route path="store/instance/:id/system-info" element={<BentoStoreSystemInfoPage />} />
    <Route path="store/instance/:id/collections/:collectionName/import" element={<BentoStoreCollectionImportPage />} />
    {/* T774 — the standalone embedding-model surface is retired; embeddings now
        live on the unified LLM instance (cloud default T757, in-process ONNX T773).
        All /bento/embedding paths redirect to the unified LLM instance surface. */}
    <Route path="embedding" element={<Navigate to={ROUTES.BENTO_LLM_INSTANCE} replace />} />
    <Route path="embedding/instance" element={<Navigate to={ROUTES.BENTO_LLM_INSTANCE} replace />} />
    <Route path="embedding/instance/:id" element={<Navigate to={ROUTES.BENTO_LLM_INSTANCE} replace />} />
    <Route path="token" element={<Navigate to={ROUTES.BENTO_TOKEN_INSTANCE} replace />} />
    <Route path="token/instance" element={<BentoTokenInstanceListPage />} />
    <Route path="token/instance/:id" element={<BentoTokenInstancePage />} />
    <Route path="gateway" element={<BentoGatewayPage />} />
    <Route path="integration" element={<Navigate to={ROUTES.BENTO_INTEGRATION_INSTANCE} replace />} />
    <Route path="integration/instance" element={<BentoIntegrationInstanceListPage />} />
    {/* Splat: Turing-owned settings at the base, Dumont's remote (relative) routes under it. */}
    <Route path="integration/instance/:id/*" element={<BentoIntegrationInstancePage />} />
    <Route path="mcp" element={<Navigate to={ROUTES.BENTO_MCP_INSTANCE} replace />} />
    <Route path="mcp/instance" element={<BentoMcpServerListPage />} />
    <Route path="mcp/instance/:id" element={<BentoMcpServerPage />} />
    <Route path="custom-tool" element={<Navigate to={ROUTES.BENTO_CUSTOM_TOOL_INSTANCE} replace />} />
    <Route path="custom-tool/instance" element={<BentoCustomToolListPage />} />
    <Route path="custom-tool/instance/:id" element={<BentoCustomToolPage />} />
    <Route path="routine" element={<Navigate to={ROUTES.BENTO_ROUTINE_INSTANCE} replace />} />
    <Route path="routine/instance" element={<BentoRoutineListPage />} />
    <Route path="routine/instance/:id" element={<BentoRoutinePage />} />
    <Route path="chat-webhook" element={<Navigate to={ROUTES.BENTO_CHAT_WEBHOOK_INSTANCE} replace />} />
    <Route path="chat-webhook/instance" element={<BentoWebhookListPage />} />
    <Route path="chat-webhook/instance/:id" element={<BentoWebhookPage />} />
    <Route path="persona" element={<Navigate to={ROUTES.BENTO_PERSONA_INSTANCE} replace />} />
    {/* Block AI (T584) — persona content-fit validation launch surface. */}
    <Route path="persona/validate/:personaId" element={<BentoPersonaValidatePage />} />
    {/* Block AU (T706) — GLOBAL persona↔persona dialogue as saved multi-projects.
        No separate `/new` route: `/dialogue/new` matches `:projectId="new"` (same
        as Persona Match), so useParams().projectId is "new" and isNew resolves. */}
    <Route path="persona/dialogue" element={<BentoPersonaDialogueListPage />} />
    <Route path="persona/dialogue/:projectId" element={<BentoPersonaDialogueWorkspacePage />} />
    {/* Block AT (T701-T702) — GLOBAL Persona Match: N×N fit projects (UI prototype). */}
    <Route path="persona/match" element={<BentoPersonaMatchListPage />} />
    <Route path="persona/match/:projectId" element={<BentoPersonaMatchWorkspacePage />} />
    {/* Block AW (T730) — GLOBAL Synthetic User Research studio: study list + workspace.
        No separate `/new` route: `/research/new` matches `:studyId="new"`. */}
    <Route path="persona/research" element={<BentoPersonaResearchListPage />} />
    {/* Static "program" route declared before :studyId (v6 ranks static ahead anyway). */}
    <Route path="persona/research/program" element={<BentoPersonaResearchProgramPage />} />
    <Route path="persona/research/:studyId" element={<BentoPersonaResearchWorkspacePage />} />
    {/* Block AW (T731) — audience cohort synthesis from a one-paragraph brief. */}
    <Route path="persona/cohort" element={<BentoPersonaCohortPage />} />
    {/* Block AG follow-up (T619) — GLOBAL auto-suggest best-fit persona for content. */}
    <Route path="persona/suggest" element={<BentoPersonaSuggestPage />} />
    <Route path="persona/instance" element={<BentoPersonaListPage />} />
    <Route path="persona/instance/:id" element={<BentoPersonaPage />}>
      {/* Landing = AI-agent-style dashboard (T582); new personas redirect to the editor. */}
      <Route index element={<BentoPersonaDashboard />} />
      {/* Section editor: one shared useForm + pill tab-bar + save bar. */}
      <Route element={<BentoPersonaEditor />}>
        <Route path="general" element={<PersonaGeneralSection />} />
        <Route path="system-instruction" element={<PersonaSystemInstructionSection />} />
        <Route path="style" element={<PersonaStyleSection />} />
        <Route path="audience" element={<PersonaAudienceSection />} />
        <Route path="notebook" element={<PersonaNotebookSection />} />
        <Route path="fit-report" element={<PersonaFitReportSection />} />
        <Route path="guidelines" element={<PersonaGuidelinesSection />} />
      </Route>
    </Route>
    <Route path="git" element={<BentoGitPage />} />
    <Route path="git/:name" element={<GitRepoPage />} />
    <Route path="sn" element={<Navigate to={ROUTES.BENTO_SN_INSTANCE} replace />} />
    <Route path="sn/onboarding" element={<BentoSNOnboardingPage />} />
    <Route path="sn/instance" element={<BentoSNInstanceListPage />} />
    <Route path="sn/instance/:id" element={<BentoSNInstancePage />} />
    <Route path="sn/instance/:id/field" element={<BentoSNFieldListPage />} />
    <Route path="sn/instance/:id/field/:fieldId" element={<BentoSNFieldPage />} />
    <Route path="sn/instance/:id/field-coverage" element={<BentoSNFieldCoveragePage />} />
    {/* SN instance sub-sections (T576). */}
    <Route path="sn/instance/:id/insights" element={<BentoSNInsightsPage />} />
    <Route path="sn/instance/:id/locale" element={<BentoSNLocaleListPage />} />
    <Route path="sn/instance/:id/locale/:localeId" element={<BentoSNLocalePage />} />
    <Route path="sn/instance/:id/behavior" element={<BentoSNBehaviorPage />} />
    <Route path="sn/instance/:id/facet" element={<BentoSNFacetListPage />} />
    <Route path="sn/instance/:id/facet/custom/:customFacetId" element={<BentoSNCustomFacetPage />} />
    <Route path="sn/instance/:id/facet/custom/:customFacetId/item/:itemIndex" element={<BentoSNCustomFacetItemPage />} />
    <Route path="sn/instance/:id/facet/field/:facetedFieldId" element={<BentoSNFacetedFieldPage />} />
    <Route path="sn/instance/:id/custom-sort" element={<BentoSNCustomSortListPage />} />
    <Route path="sn/instance/:id/custom-sort/:customSortId" element={<BentoSNCustomSortPage />} />
    <Route path="sn/instance/:id/search-rule" element={<BentoSNSearchRuleListPage />} />
    <Route path="sn/instance/:id/search-rule/:searchRuleId" element={<BentoSNSearchRulePage />} />
    <Route path="sn/instance/:id/ai" element={<BentoSNGenAiPage />} />
    <Route path="sn/instance/:id/microthesaurus" element={<BentoSNThesaurusPage />} />
    <Route path="sn/instance/:id/result-ranking" element={<BentoSNResultRankingListPage />} />
    <Route path="sn/instance/:id/result-ranking/:resultRankingId" element={<BentoSNResultRankingPage />} />
    <Route path="sn/instance/:id/merge-providers" element={<BentoSNMergeProvidersListPage />} />
    <Route path="sn/instance/:id/merge-providers/:mergeProviderId" element={<BentoSNMergeProvidersPage />} />
    <Route path="sn/instance/:id/spotlight" element={<BentoSNSpotlightListPage />} />
    <Route path="sn/instance/:id/spotlight/:spotlightId" element={<BentoSNSpotlightPage />} />
    <Route path="sn/instance/:id/synonym" element={<BentoSNSynonymListPage />} />
    <Route path="sn/instance/:id/synonym/:synonymId" element={<BentoSNSynonymPage />} />
    <Route path="sn/instance/:id/top-terms/:period?" element={<BentoSNTopTermsPage />} />
    <Route path="skill" element={<BentoSkillListPage />} />
    <Route path="skill/:id" element={<BentoSkillEditorPage />} />
    {/* Block AQ (§XL) — Thesaurus library */}
    <Route path="thesaurus" element={<BentoThesaurusListPage />} />
    <Route path="thesaurus/:kbId" element={<BentoThesaurusKbPage />} />
    <Route
      path="thesaurus/:kbId/m/:microId"
      element={<BentoThesaurusMicrothesaurusPage />}
    />
    <Route path="asset" element={<BentoAssetPage />} />
    <Route path="page" element={<BentoPagePage />} />
    {/* Phase 3 (T563) — chat + conversation. */}
    <Route path="chat" element={<BentoChatPage />} />
    {/* Block AI (T580) — deterministic, shareable chat URLs. */}
    <Route path="chat/agent/:agentId" element={<BentoChatPage />} />
    <Route path="chat/persona/:personaId" element={<BentoPersonaChatPage />} />
    <Route path="conversation/:conversationId" element={<BentoConversationPage />} />
    {/* Phase 3 (T564) — AI Analytics suite; index redirects to the first
        visible sub-page, tabs render the reused console content components. */}
    <Route path="ai-analytics" element={<BentoAiAnalyticsPage />}>
      <Route index element={<BentoAiAnalyticsIndexRedirect />} />
      <Route path="cost-governance" element={<CostGovernancePage />} />
      <Route path="chat-analytics" element={<ChatAnalyticsPage />} />
      <Route path="parked-conversations" element={<ParkedConversationsPage />} />
      <Route path="capability-matrix" element={<CapabilityMatrixPage />} />
    </Route>
    {/* Phase 3 (T565) — GraphQL + Dashboard + Marketplace. */}
    <Route path="graphql" element={<BentoGraphqlPage />} />
    <Route path="dashboard" element={<BentoDashboardPage />} />
    <Route path="marketplace" element={<BentoMarketplacePage />} />
    {/* Phase 3 (T566) — Global Settings + Import + Logging + System Info. */}
    <Route path="global-settings" element={<BentoGlobalSettingsPage />} />
    <Route path="import" element={<BentoImportPage />} />
    <Route path="logging" element={<BentoLoggingPage />} />
    <Route path="logging/server" element={<BentoLoggingSourcePage source="server" />} />
    <Route path="logging/indexing" element={<BentoLoggingSourcePage source="indexing" />} />
    <Route path="logging/aem" element={<BentoLoggingSourcePage source="aem" />} />
    <Route path="system-info" element={<BentoSystemInfoPage />} />
    {/* Users / Groups / Roles are now standalone Management cards (no
        Administration hub); each list owns its BentoHero and links back to the
        Management area. Old /bento/admin links redirect there. */}
    <Route path="admin" element={<Navigate to={ROUTES.BENTO_AREA_MANAGEMENT} replace />} />
    <Route path="admin/users" element={<BentoAdminUsersListPage />} />
    <Route path="admin/users/:username" element={<BentoAdminUserPage />} />
    <Route path="admin/groups" element={<BentoAdminGroupsListPage />} />
    <Route path="admin/groups/:groupId" element={<BentoAdminGroupPage />} />
    <Route path="admin/roles" element={<BentoAdminRolesListPage />} />
    <Route path="admin/roles/:roleId" element={<BentoAdminRolePage />} />
    {/* Phase 4 (T568) — user account (profile/preferences) + tenant surfaces. */}
    <Route path="user/account" element={<BentoUserAccountPage />}>
      <Route index element={<Navigate to={`${ROUTES.BENTO_USER_ACCOUNT}/profile`} replace />} />
      <Route path="profile" element={<UserProfilePage header={null} />} />
      <Route path="preferences" element={<UserPreferencesPage header={null} />} />
    </Route>
    <Route path="tenant" element={<BentoTenantSettingsPage />} />
    <Route path="tenant-admin" element={<BentoTenantAdminPage />} />
    {/* Block AJ (T599) — Eval Studio. */}
    <Route path="eval" element={<BentoEvalPage />} />
  </Route>
);
