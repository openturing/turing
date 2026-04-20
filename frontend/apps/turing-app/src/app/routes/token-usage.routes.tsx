import { Route } from "react-router-dom"
import TokenUsagePage from "../console/token-usage/token-usage.page"
import TokenUsageRootPage from "../console/token-usage/token-usage.root.page"
import { ROUTES } from "../routes.const"

export const TokenUsageRoutes = (
    <Route path={ROUTES.TOKEN_USAGE} element={<TokenUsageRootPage />}>
        <Route index element={<TokenUsagePage />} />
    </Route>
)
