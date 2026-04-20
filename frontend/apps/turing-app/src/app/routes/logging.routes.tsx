import { Navigate, Route } from "react-router-dom"
import LoggingAemPage from "../console/logging/instance/logging.aem.page"
import LoggingIndexingPage from "../console/logging/instance/logging.indexing.page"
import LoggingServerPage from "../console/logging/instance/logging.server.page"
import LoggingInstanceListPage from "../console/logging/logging.instance.list.page"
import LoggingInstanceRootPage from "../console/logging/logging.instance.root.page"
import { ROUTES } from "../routes.const"

export const LoggingRoutes = (
    <Route path={ROUTES.LOGGING_ROOT} element={<LoggingInstanceRootPage />}>
        <Route index element={<Navigate to={ROUTES.LOGGING_INSTANCE} replace />} />
        <Route path={ROUTES.LOGGING_INSTANCE} element={<LoggingInstanceListPage />} />
        <Route path={`${ROUTES.LOGGING_INSTANCE}/server`} element={<LoggingServerPage />} />
        <Route path={`${ROUTES.LOGGING_INSTANCE}/aem`} element={<LoggingAemPage />} />
        <Route path={`${ROUTES.LOGGING_INSTANCE}/indexing`} element={<LoggingIndexingPage />} />
    </Route>
)
