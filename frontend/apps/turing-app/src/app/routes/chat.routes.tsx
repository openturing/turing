import { Route } from "react-router-dom"
import ChatPage from "../console/chat/chat.page"
import { ROUTES } from "../routes.const"

export const ChatRoutes = (
    <Route path={ROUTES.CHAT_ROOT} element={<ChatPage />} />
)
