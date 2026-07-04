import { lazy } from "react";
import { Navigate, Route } from "react-router-dom";
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
    <Route path="integration/instance/:id" element={<BentoIntegrationInstancePage />} />
    <Route path="mcp" element={<Navigate to={ROUTES.BENTO_MCP_INSTANCE} replace />} />
    <Route path="mcp/instance" element={<BentoMcpServerListPage />} />
    <Route path="mcp/instance/:id" element={<BentoMcpServerPage />} />
    <Route path="custom-tool" element={<Navigate to={ROUTES.BENTO_CUSTOM_TOOL_INSTANCE} replace />} />
    <Route path="custom-tool/instance" element={<BentoCustomToolListPage />} />
    <Route path="custom-tool/instance/:id" element={<BentoCustomToolPage />} />
  </Route>
);
