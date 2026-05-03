import { lazy } from "react"
import { Navigate, Route, useParams } from "react-router-dom"
import { ROUTES } from "../routes.const"

const StoreInstanceCollectionImportPage = lazy(() => import("../console/store/collections/store.instance.collection.import.page"))
const StoreInstanceCollectionsPage = lazy(() => import("../console/store/collections/store.instance.collections.page"))
const StoreInstanceDetailPage = lazy(() => import("../console/store/detail/store.instance.detail.page"))
const StoreInstanceSystemInfoPage = lazy(() => import("../console/store/system-info/store.instance.system-info.page"))
const StoreInstanceListPage = lazy(() => import("../console/store/store.instance.list.page"))
const StoreInstancePage = lazy(() => import("../console/store/store.instance.page"))
const StoreInstanceRootPage = lazy(() => import("../console/store/store.instance.root.page"))

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
            <Route path="collections/:collectionName/import" element={<StoreInstanceCollectionImportPage />} />
            <Route path="system-info" element={<StoreInstanceSystemInfoPage />} />
        </Route>
    </Route>
)
