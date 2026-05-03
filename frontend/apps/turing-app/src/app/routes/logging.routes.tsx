import { lazy } from "react"
import { Navigate, Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const LoggingAemPage = lazy(() => import("../console/logging/instance/logging.aem.page"))
const LoggingIndexingPage = lazy(() => import("../console/logging/instance/logging.indexing.page"))
const LoggingServerPage = lazy(() => import("../console/logging/instance/logging.server.page"))
const LoggingInstanceListPage = lazy(() => import("../console/logging/logging.instance.list.page"))
const LoggingInstanceRootPage = lazy(() => import("../console/logging/logging.instance.root.page"))

export const LoggingRoutes = (
    <Route path={ROUTES.LOGGING_ROOT} element={<LoggingInstanceRootPage />}>
        <Route index element={<Navigate to={ROUTES.LOGGING_INSTANCE} replace />} />
        <Route path={ROUTES.LOGGING_INSTANCE} element={<LoggingInstanceListPage />} />
        <Route path={`${ROUTES.LOGGING_INSTANCE}/server`} element={<LoggingServerPage />} />
        <Route path={`${ROUTES.LOGGING_INSTANCE}/aem`} element={<LoggingAemPage />} />
        <Route path={`${ROUTES.LOGGING_INSTANCE}/indexing`} element={<LoggingIndexingPage />} />
    </Route>
)
