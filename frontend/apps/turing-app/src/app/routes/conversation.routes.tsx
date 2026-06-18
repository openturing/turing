import { lazy } from "react"
import { Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

// T120 — Spectator + Co-pilot mode. Opened per-conversation (carries a
// :conversationId param) from the parked-conversations dashboard or a deep link
// like /console/conversation/{id}?spectate=true.
const ConversationPage = lazy(() => import("../console/conversation/conversation.page"))

export const ConversationRoutes = (
    <Route path={`${ROUTES.CONVERSATION_DETAIL}/:conversationId`} element={<ConversationPage />} />
)
