import { Navigate, Route, useParams } from "react-router-dom"
import AIAgentListPage from "../console/ai-agent/ai-agent.list.page"
import AIAgentPage from "../console/ai-agent/ai-agent.page"
import AIAgentRootPage from "../console/ai-agent/ai-agent.root.page"
import AIAgentSettingsPage from "../console/ai-agent/settings/ai-agent.settings.page"
import AIAgentLlmPage from "../console/ai-agent/llm/ai-agent.llm.page"
import AIAgentMcpPage from "../console/ai-agent/mcp/ai-agent.mcp.page"
import AIAgentToolsPage from "../console/ai-agent/tools/ai-agent.tools.page"
import IntentListPage from "../console/intent/intent.list.page"
import IntentPage from "../console/intent/intent.page"
import IntentSettingsPage from "../console/intent/settings/intent.settings.page"
import ChatFlowPage from "../console/chat-flow/chat-flow.page"
import { ROUTES } from "../routes.const"

function RedirectToAIAgentSettings() {
    const { id } = useParams();
    return <Navigate to={`${ROUTES.AI_AGENT_INSTANCE}/${id}/settings`} replace />;
}

function RedirectToIntentSettings() {
    const { id, intentId } = useParams();
    return <Navigate to={`${ROUTES.AI_AGENT_INSTANCE}/${id}/intent/${intentId}/settings`} replace />;
}

export const AIAgentRoutes = (
    <Route path={ROUTES.AI_AGENT_ROOT} element={<AIAgentRootPage />}>
        <Route index element={<Navigate to={ROUTES.AI_AGENT_INSTANCE} replace />} />
        <Route path={ROUTES.AI_AGENT_INSTANCE} element={<AIAgentListPage />} />
        <Route path={`${ROUTES.AI_AGENT_INSTANCE}/:id`} element={<AIAgentPage />}>
            <Route index element={<RedirectToAIAgentSettings />} />
            <Route path="settings" element={<AIAgentSettingsPage />} />
            <Route path="llm" element={<AIAgentLlmPage />} />
            <Route path="mcp" element={<AIAgentMcpPage />} />
            <Route path="tools" element={<AIAgentToolsPage />} />
            <Route path="chat-flow" element={<ChatFlowPage />} />
            <Route path="intent" element={<IntentListPage />} />
            <Route path="intent/:intentId" element={<IntentPage />}>
                <Route index element={<RedirectToIntentSettings />} />
                <Route path="settings" element={<IntentSettingsPage />} />
            </Route>
        </Route>
    </Route>
)
