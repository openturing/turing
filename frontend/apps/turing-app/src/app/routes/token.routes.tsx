import { lazy } from "react"
import { Navigate, Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const TokenInstanceListPage = lazy(() => import("../console/token/token.instance.list.page"))
const TokenInstancePage = lazy(() => import("../console/token/token.instance.page"))
const TokenInstanceRootPage = lazy(() => import("../console/token/token.instance.root.page"))

export const TokenRoutes = (
    <Route path={ROUTES.TOKEN_ROOT} element={<TokenInstanceRootPage />}>
        <Route index element={<Navigate to={ROUTES.TOKEN_INSTANCE} replace />} />
        <Route path={ROUTES.TOKEN_INSTANCE} element={<TokenInstanceListPage />} />
        <Route path={`${ROUTES.TOKEN_INSTANCE}/:id`} element={<TokenInstancePage />} />
    </Route>
)
