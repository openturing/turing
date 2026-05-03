import { lazy } from "react"
import { Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const ChatPage = lazy(() => import("../console/chat/chat.page"))

export const ChatRoutes = (
    <Route path={ROUTES.CHAT_ROOT} element={<ChatPage />} />
)
