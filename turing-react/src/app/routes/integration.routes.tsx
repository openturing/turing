import { Navigate, Route, useParams } from "react-router-dom"
import IntegrationInstanceDetailPage from "../console/integration/integration.instance.detail.page"
import IntegrationInstanceIndexAdminPage from "../console/integration/integration.instance.indexing.manager.page"
import IntegrationInstanceIndexingRulesListPage from "../console/integration/integration.instance.indexing.rules.list.page"
import IntegrationInstanceIndexingRulesPage from "../console/integration/integration.instance.indexing.rules.page"
import IntegrationInstanceListPage from "../console/integration/integration.instance.list.page"
import IntegrationInstanceMonitoringPage from "../console/integration/integration.instance.monitoring.page"
import IntegrationInstancePage from "../console/integration/integration.instance.page"
import IntegrationInstanceRootPage from "../console/integration/integration.instance.root.page"
import IntegrationInstanceSourceListPage from "../console/integration/integration.instance.source.list.page"
import IntegrationInstanceSourcePage from "../console/integration/integration.instance.source.page"
import { ROUTES } from "../routes.const"

function RedirectToIntegrationDetail() {
    const { id } = useParams();
    return <Navigate to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/detail`} replace />;
}

export const IntegrationRoutes = (
    <Route path={ROUTES.INTEGRATION_ROOT} element={<IntegrationInstanceRootPage />}>
        <Route index element={<Navigate to={ROUTES.INTEGRATION_INSTANCE} replace />} />
        <Route path={ROUTES.INTEGRATION_INSTANCE} element={<IntegrationInstanceListPage />} />
        <Route path={`${ROUTES.INTEGRATION_INSTANCE}/:id`} element={<IntegrationInstancePage />}>
            <Route index element={<RedirectToIntegrationDetail />} />
            <Route path="detail" element={<IntegrationInstanceDetailPage />} />
            <Route path="source" element={<IntegrationInstanceSourceListPage />} />
            <Route path="source/:sourceId" element={<IntegrationInstanceSourcePage />} />
            <Route path="indexing-rule" element={<IntegrationInstanceIndexingRulesListPage />} />
            <Route path="indexing-rule/:ruleId" element={<IntegrationInstanceIndexingRulesPage />} />
            <Route path="indexing-manager" element={<IntegrationInstanceIndexAdminPage />} />
            <Route path="indexing-manager/:mode" element={<IntegrationInstanceIndexAdminPage />} />
            <Route path="monitoring">
                <Route index element={<Navigate to="all" replace />} />
                <Route path=":source" element={<IntegrationInstanceMonitoringPage />} />
            </Route>
        </Route>
    </Route>
)
