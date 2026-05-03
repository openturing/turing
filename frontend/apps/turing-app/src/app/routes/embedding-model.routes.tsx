import { lazy } from "react"
import { Navigate, Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const EmbeddingModelListPage = lazy(() => import("../console/embedding-model/embedding-model.list.page"))
const EmbeddingModelPage = lazy(() => import("../console/embedding-model/embedding-model.page"))
const EmbeddingModelRootPage = lazy(() => import("../console/embedding-model/embedding-model.root.page"))

export const EmbeddingModelRoutes = (
    <Route path={ROUTES.EMBEDDING_MODEL_ROOT} element={<EmbeddingModelRootPage />}>
        <Route index element={<Navigate to={ROUTES.EMBEDDING_MODEL_INSTANCE} replace />} />
        <Route path={ROUTES.EMBEDDING_MODEL_INSTANCE} element={<EmbeddingModelListPage />} />
        <Route path={`${ROUTES.EMBEDDING_MODEL_INSTANCE}/:id`} element={<EmbeddingModelPage />} />
    </Route>
)
