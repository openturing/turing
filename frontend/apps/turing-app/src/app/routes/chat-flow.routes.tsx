import { Route } from "react-router-dom"
import ChatFlowPage from "../console/chat-flow/chat-flow.page"
import { ROUTES } from "../routes.const"

export const ChatFlowRoutes = (
    <Route path={ROUTES.CHAT_FLOW_ROOT} element={<ChatFlowPage />} />
)
