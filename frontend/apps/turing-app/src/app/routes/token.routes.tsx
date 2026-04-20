import { Navigate, Route } from "react-router-dom"
import TokenInstanceListPage from "../console/token/token.instance.list.page"
import TokenInstancePage from "../console/token/token.instance.page"
import TokenInstanceRootPage from "../console/token/token.instance.root.page"
import { ROUTES } from "../routes.const"

export const TokenRoutes = (
    <Route path={ROUTES.TOKEN_ROOT} element={<TokenInstanceRootPage />}>
        <Route index element={<Navigate to={ROUTES.TOKEN_INSTANCE} replace />} />
        <Route path={ROUTES.TOKEN_INSTANCE} element={<TokenInstanceListPage />} />
        <Route path={`${ROUTES.TOKEN_INSTANCE}/:id`} element={<TokenInstancePage />} />
    </Route>
)
