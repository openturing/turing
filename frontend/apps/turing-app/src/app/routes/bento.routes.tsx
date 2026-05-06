import { lazy } from "react";
import { Navigate, Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const BentoRootPage = lazy(() => import("../bento/bento.root.page"));
const BentoHomePage = lazy(() => import("../bento/home/bento.home.page"));
const BentoAIAgentListPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.list.page"));
const BentoAIAgentPage = lazy(() => import("../bento/ai-agent/bento.ai-agent.page"));

export const BentoRoutes = (
  <Route path={ROUTES.BENTO_ROOT} element={<BentoRootPage />}>
    <Route index element={<Navigate to={ROUTES.BENTO_HOME} replace />} />
    <Route path="home" element={<BentoHomePage />} />
    <Route path="ai-agent" element={<Navigate to={ROUTES.BENTO_AI_AGENT_INSTANCE} replace />} />
    <Route path="ai-agent/instance" element={<BentoAIAgentListPage />} />
    <Route path="ai-agent/instance/:id" element={<BentoAIAgentPage />} />
  </Route>
);
