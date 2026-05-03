import { lazy } from "react"
import { Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const GraphiQLPage = lazy(() => import("../console/graphql/graphql.page"))

export const GraphqlRoutes = (
    <Route path={ROUTES.GRAPHQL_ROOT} element={<GraphiQLPage />} />
)
