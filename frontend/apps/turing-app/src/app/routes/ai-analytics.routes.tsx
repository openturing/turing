import { lazy } from "react"
import { Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const AiAnalyticsRootPage = lazy(() => import("../console/ai-analytics/ai-analytics.root.page"))
const AiAnalyticsLayoutPage = lazy(() => import("../console/ai-analytics/ai-analytics.layout.page"))
const AiAnalyticsIndexRedirect = lazy(() => import("../console/ai-analytics/ai-analytics.index.redirect"))
const CostGovernanceSubPage = lazy(() => import("../console/cost-governance/cost-governance.sub.page"))
const ChatAnalyticsSubPage = lazy(() => import("../console/chat-analytics/chat-analytics.sub.page"))
const ParkedConversationsSubPage = lazy(() => import("../console/parked-conversations/parked-conversations.sub.page"))

export const AiAnalyticsRoutes = (
    <Route path={ROUTES.AI_ANALYTICS_ROOT} element={<AiAnalyticsRootPage />}>
        <Route element={<AiAnalyticsLayoutPage />}>
            <Route index element={<AiAnalyticsIndexRedirect />} />
            <Route path="cost-governance" element={<CostGovernanceSubPage />} />
            <Route path="chat-analytics" element={<ChatAnalyticsSubPage />} />
            <Route path="parked-conversations" element={<ParkedConversationsSubPage />} />
        </Route>
    </Route>
)
