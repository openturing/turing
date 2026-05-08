import { lazy } from "react"
import { Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const ChatAnalyticsPage = lazy(() => import("../console/chat-analytics/chat-analytics.page"))
const ChatAnalyticsRootPage = lazy(() => import("../console/chat-analytics/chat-analytics.root.page"))

export const ChatAnalyticsRoutes = (
    <Route path={ROUTES.CHAT_ANALYTICS} element={<ChatAnalyticsRootPage />}>
        <Route index element={<ChatAnalyticsPage />} />
    </Route>
)
