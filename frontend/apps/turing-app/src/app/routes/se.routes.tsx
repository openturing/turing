import { Navigate, Route, useParams } from "react-router-dom"
import SEInstanceCoreNewPage from "../console/se/cores/se.instance.core.new.page"
import SEInstanceCoresPage from "../console/se/cores/se.instance.cores.page"
import SEInstanceDetailPage from "../console/se/detail/se.instance.detail.page"
import SEInstanceSystemInfoPage from "../console/se/system-info/se.instance.system-info.page"
import SEInstanceListPage from "../console/se/se.instance.list.page"
import SEInstancePage from "../console/se/se.instance.page"
import SEInstanceRootPage from "../console/se/se.instance.root.page"
import { ROUTES } from "../routes.const"

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
