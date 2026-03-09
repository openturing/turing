import { Route } from "react-router-dom"
import TokenUsagePage from "../console/token-usage/token-usage.page"
import { ROUTES } from "../routes.const"

export const TokenUsageRoutes = (
    <Route path={ROUTES.TOKEN_USAGE} element={<TokenUsagePage />} />
)
