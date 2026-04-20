import { Navigate, Route } from "react-router-dom"
import EmbeddingModelListPage from "../console/embedding-model/embedding-model.list.page"
import EmbeddingModelPage from "../console/embedding-model/embedding-model.page"
import EmbeddingModelRootPage from "../console/embedding-model/embedding-model.root.page"
import { ROUTES } from "../routes.const"

export const EmbeddingModelRoutes = (
    <Route path={ROUTES.EMBEDDING_MODEL_ROOT} element={<EmbeddingModelRootPage />}>
        <Route index element={<Navigate to={ROUTES.EMBEDDING_MODEL_INSTANCE} replace />} />
        <Route path={ROUTES.EMBEDDING_MODEL_INSTANCE} element={<EmbeddingModelListPage />} />
        <Route path={`${ROUTES.EMBEDDING_MODEL_INSTANCE}/:id`} element={<EmbeddingModelPage />} />
    </Route>
)
