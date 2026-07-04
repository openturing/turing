import { lazy } from "react";
import { Navigate, Route, useParams } from "react-router-dom";
import { ROUTES } from "../routes.const";

const BentoRootPage = lazy(() => import("../bento/bento.root.page"));
const BentoHomePage = lazy(() => import("../bento/home/bento.home.page"));
const BentoAreaPage = lazy(() => import("../bento/area/bento.area.page"));
const BentoAIAgentListPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.list.page"));
const BentoAIAgentPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.page"));
const BentoLLMInstanceListPage = lazy(() => import("../bento/llm/bento.llm.instance.list.page"));
const BentoLLMInstancePage = lazy(() => import("../bento/llm/bento.llm.instance.page"));
const BentoSEInstanceListPage = lazy(() => import("../bento/se/bento.se.instance.list.page"));
const BentoSEInstancePage = lazy(() => import("../bento/se/bento.se.instance.page"));
const BentoStoreInstanceListPage = lazy(() => import("../bento/store/bento.store.instance.list.page"));
const BentoStoreInstancePage = lazy(() => import("../bento/store/bento.store.instance.page"));
const BentoEmbeddingModelListPage = lazy(() => import("../bento/embedding/bento.embedding.list.page"));
const BentoEmbeddingModelPage = lazy(() => import("../bento/embedding/bento.embedding.page"));
const BentoTokenInstanceListPage = lazy(() => import("../bento/token/bento.token.instance.list.page"));
const BentoTokenInstancePage = lazy(() => import("../bento/token/bento.token.instance.page"));
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
// Phase 2 (T559) — SN onboarding wizard in the bento shell.
const BentoSNOnboardingPage = lazy(() => import("../bento/sn/bento.sn.onboarding.page"));

function RedirectToBentoPersonaGeneral() {
  const { id } = useParams();
  return <Navigate to={`${ROUTES.BENTO_PERSONA_INSTANCE}/${id}/general`} replace />;
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
    <Route path="llm" element={<Navigate to={ROUTES.BENTO_LLM_INSTANCE} replace />} />
    <Route path="llm/instance" element={<BentoLLMInstanceListPage />} />
    <Route path="llm/instance/:id" element={<BentoLLMInstancePage />} />
    <Route path="se" element={<Navigate to={ROUTES.BENTO_SE_INSTANCE} replace />} />
    <Route path="se/instance" element={<BentoSEInstanceListPage />} />
    <Route path="se/instance/:id" element={<BentoSEInstancePage />} />
    <Route path="store" element={<Navigate to={ROUTES.BENTO_STORE_INSTANCE} replace />} />
    <Route path="store/instance" element={<BentoStoreInstanceListPage />} />
    <Route path="store/instance/:id" element={<BentoStoreInstancePage />} />
    <Route path="embedding" element={<Navigate to={ROUTES.BENTO_EMBEDDING_MODEL_INSTANCE} replace />} />
    <Route path="embedding/instance" element={<BentoEmbeddingModelListPage />} />
    <Route path="embedding/instance/:id" element={<BentoEmbeddingModelPage />} />
    <Route path="token" element={<Navigate to={ROUTES.BENTO_TOKEN_INSTANCE} replace />} />
    <Route path="token/instance" element={<BentoTokenInstanceListPage />} />
    <Route path="token/instance/:id" element={<BentoTokenInstancePage />} />
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
    <Route path="persona/instance" element={<BentoPersonaListPage />} />
    <Route path="persona/instance/:id" element={<BentoPersonaPage />}>
      <Route index element={<RedirectToBentoPersonaGeneral />} />
      <Route path="general" element={<PersonaGeneralSection />} />
      <Route path="system-instruction" element={<PersonaSystemInstructionSection />} />
      <Route path="style" element={<PersonaStyleSection />} />
      <Route path="audience" element={<PersonaAudienceSection />} />
      <Route path="notebook" element={<PersonaNotebookSection />} />
      <Route path="fit-report" element={<PersonaFitReportSection />} />
      <Route path="guidelines" element={<PersonaGuidelinesSection />} />
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
  </Route>
);
