import { lazy } from "react"
import { Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const TokenUsagePage = lazy(() => import("../console/token-usage/token-usage.page"))
const TokenUsageRootPage = lazy(() => import("../console/token-usage/token-usage.root.page"))

export const TokenUsageRoutes = (
    <Route path={ROUTES.TOKEN_USAGE} element={<TokenUsageRootPage />}>
        <Route index element={<TokenUsagePage />} />
    </Route>
)
