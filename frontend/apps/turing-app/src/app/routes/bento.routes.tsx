import { lazy } from "react";
import { Navigate, Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const BentoRootPage = lazy(() => import("../bento/bento.root.page"));
const BentoHomePage = lazy(() => import("../bento/home/bento.home.page"));
const BentoAIAgentListPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.list.page"));
const BentoAIAgentPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.page"));
const BentoLLMInstanceListPage = lazy(() => import("../bento/llm/bento.llm.instance.list.page"));
const BentoLLMInstancePage = lazy(() => import("../bento/llm/bento.llm.instance.page"));

export const BentoRoutes = (
  <Route path={ROUTES.BENTO_ROOT} element={<BentoRootPage />}>
    <Route index element={<Navigate to={ROUTES.BENTO_HOME} replace />} />
    <Route path="home" element={<BentoHomePage />} />
    <Route path="ai-agent" element={<Navigate to={ROUTES.BENTO_AI_AGENT_INSTANCE} replace />} />
    <Route path="ai-agent/instance" element={<BentoAIAgentListPage />} />
    <Route path="ai-agent/instance/:id" element={<BentoAIAgentPage />} />
    <Route path="llm" element={<Navigate to={ROUTES.BENTO_LLM_INSTANCE} replace />} />
    <Route path="llm/instance" element={<BentoLLMInstanceListPage />} />
    <Route path="llm/instance/:id" element={<BentoLLMInstancePage />} />
  </Route>
);
