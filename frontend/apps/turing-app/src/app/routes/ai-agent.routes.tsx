import { lazy } from "react"
import { Navigate, Route, useParams } from "react-router-dom"
import { ROUTES } from "../routes.const"

// chat-flow.page.tsx alone is 668 lines and pulls react-flow; lazy-loading
// keeps that out of the initial bundle.
const AIAgentListPage = lazy(() => import("../console/ai-agent/ai-agent.list.page"))
const AIAgentPage = lazy(() => import("../console/ai-agent/ai-agent.page"))
const AIAgentRootPage = lazy(() => import("../console/ai-agent/ai-agent.root.page"))
const AIAgentSettingsPage = lazy(() => import("../console/ai-agent/settings/ai-agent.settings.page"))
const AIAgentSystemPromptPage = lazy(() => import("../console/ai-agent/system-prompt/ai-agent.system-prompt.page"))
const AIAgentLlmPage = lazy(() => import("../console/ai-agent/llm/ai-agent.llm.page"))
const AIAgentPersonaPage = lazy(() => import("../console/ai-agent/persona/ai-agent.persona.page"))
const AIAgentMcpPage = lazy(() => import("../console/ai-agent/mcp/ai-agent.mcp.page"))
const AIAgentCustomToolPage = lazy(() => import("../console/ai-agent/custom-tool/ai-agent.custom-tool.page"))
const AIAgentToolsPage = lazy(() => import("../console/ai-agent/tools/ai-agent.tools.page"))
const AIAgentSlotPage = lazy(() => import("../console/ai-agent/slot/ai-agent.slot.page"))
const IntentListPage = lazy(() => import("../console/intent/intent.list.page"))
const IntentSettingsPage = lazy(() => import("../console/intent/settings/intent.settings.page"))
const IntentAiChatPage = lazy(() => import("../console/intent/ai-chat/intent.ai-chat.page"))
const AnalyticsIntentListPage = lazy(() => import("../console/analytics-intent/analytics-intent.list.page"))
const ChatFlowListPage = lazy(() => import("../console/chat-flow/chat-flow.list.page"))
const ChatFlowPage = lazy(() => import("../console/chat-flow/chat-flow.page"))
const ChatFlowAiChatPage = lazy(() => import("../console/chat-flow/ai-chat/chat-flow.ai-chat.page"))
const AIAgentHistoryPage = lazy(() => import("../console/ai-agent/history/ai-agent.history.page"))

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
            <Route path="system-prompt" element={<AIAgentSystemPromptPage />} />
            <Route path="llm" element={<AIAgentLlmPage />} />
            <Route path="persona" element={<AIAgentPersonaPage />} />
            <Route path="mcp" element={<AIAgentMcpPage />} />
            <Route path="custom-tool" element={<AIAgentCustomToolPage />} />
            <Route path="tools" element={<AIAgentToolsPage />} />
            <Route path="slot" element={<AIAgentSlotPage />} />
            <Route path="chat-flow" element={<ChatFlowListPage />} />
            <Route path="chat-flow/new/ai-chat" element={<ChatFlowAiChatPage />} />
            <Route path="chat-flow/:flowId" element={<ChatFlowPage />} />
            <Route path="history" element={<AIAgentHistoryPage />} />
            <Route path="intent" element={<IntentListPage />} />
            <Route path="intent/new/ai-chat" element={<IntentAiChatPage />} />
            <Route path="intent/:intentId" element={<RedirectToIntentSettings />} />
            <Route path="intent/:intentId/settings" element={<IntentSettingsPage />} />
            <Route path="analytics-intent" element={<AnalyticsIntentListPage />} />
        </Route>
    </Route>
)
