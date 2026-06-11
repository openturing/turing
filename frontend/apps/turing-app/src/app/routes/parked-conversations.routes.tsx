import { lazy } from "react"
import { Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const ParkedConversationsPage = lazy(() => import("../console/parked-conversations/parked-conversations.page"))
const ParkedConversationsRootPage = lazy(() => import("../console/parked-conversations/parked-conversations.root.page"))

export const ParkedConversationsRoutes = (
    <Route path={ROUTES.PARKED_CONVERSATIONS} element={<ParkedConversationsRootPage />}>
        <Route index element={<ParkedConversationsPage />} />
    </Route>
)
