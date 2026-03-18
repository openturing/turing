import { Navigate, Route, useParams } from "react-router-dom"
import StoreInstanceCollectionsPage from "../console/store/collections/store.instance.collections.page"
import StoreInstanceDetailPage from "../console/store/detail/store.instance.detail.page"
import StoreInstanceListPage from "../console/store/store.instance.list.page"
import StoreInstancePage from "../console/store/store.instance.page"
import StoreInstanceRootPage from "../console/store/store.instance.root.page"
import { ROUTES } from "../routes.const"

function RedirectToStoreDetail() {
    const { id } = useParams();
    return <Navigate to={`${ROUTES.STORE_INSTANCE}/${id}/detail`} replace />;
}

export const StoreRoutes = (
    <Route path={ROUTES.STORE_ROOT} element={<StoreInstanceRootPage />}>
        <Route path={ROUTES.STORE_INSTANCE} element={<StoreInstanceListPage />} />
        <Route path={`${ROUTES.STORE_INSTANCE}/:id`} element={<StoreInstancePage />}>
            <Route index element={<RedirectToStoreDetail />} />
            <Route path="detail" element={<StoreInstanceDetailPage />} />
            <Route path="collections" element={<StoreInstanceCollectionsPage />} />
        </Route>
    </Route>
)
