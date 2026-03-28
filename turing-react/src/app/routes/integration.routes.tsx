import { Navigate, Route, useParams } from "react-router-dom"
import IntegrationInstanceDetailPage from "../console/integration/integration.instance.detail.page"
import IntegrationInstanceDumontPage from "../console/integration/integration.instance.dumont.page"
import IntegrationInstanceListPage from "../console/integration/integration.instance.list.page"
import IntegrationInstancePage from "../console/integration/integration.instance.page"
import IntegrationInstanceRootPage from "../console/integration/integration.instance.root.page"
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
            {/* Turing-owned: Settings */}
            <Route path="detail" element={<IntegrationInstanceDetailPage />} />
            {/* All dumont-owned routes via Module Federation */}
            <Route path="*" element={<IntegrationInstanceDumontPage />} />
        </Route>
    </Route>
)
