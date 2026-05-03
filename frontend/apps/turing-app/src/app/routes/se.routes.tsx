import { lazy } from "react"
import { Navigate, Route, useParams } from "react-router-dom"
import { ROUTES } from "../routes.const"

const SEInstanceCoreNewPage = lazy(() => import("../console/se/cores/se.instance.core.new.page"))
const SEInstanceCoresPage = lazy(() => import("../console/se/cores/se.instance.cores.page"))
const SEInstanceDetailPage = lazy(() => import("../console/se/detail/se.instance.detail.page"))
const SEInstanceSystemInfoPage = lazy(() => import("../console/se/system-info/se.instance.system-info.page"))
const SEInstanceListPage = lazy(() => import("../console/se/se.instance.list.page"))
const SEInstancePage = lazy(() => import("../console/se/se.instance.page"))
const SEInstanceRootPage = lazy(() => import("../console/se/se.instance.root.page"))

function RedirectToSEDetail() {
    const { id } = useParams();
    return <Navigate to={`${ROUTES.SE_INSTANCE}/${id}/detail`} replace />;
}

export const SERoutes = (
    <Route path={ROUTES.SE_ROOT} element={<SEInstanceRootPage />}>
        <Route path={ROUTES.SE_INSTANCE} element={<SEInstanceListPage />} />
        <Route path={`${ROUTES.SE_INSTANCE}/:id`} element={<SEInstancePage />}>
            <Route index element={<RedirectToSEDetail />} />
            <Route path="detail" element={<SEInstanceDetailPage />} />
            <Route path="cores" element={<SEInstanceCoresPage />} />
            <Route path="cores/new" element={<SEInstanceCoreNewPage />} />
            <Route path="system-info" element={<SEInstanceSystemInfoPage />} />
        </Route>
    </Route>
)
