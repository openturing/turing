import { Navigate, Route } from "react-router-dom"
import LLMInstanceListPage from "../console/llm/llm.instance.list.page"
import LLMInstancePage from "../console/llm/llm.instance.page"
import LLMInstanceRootPage from "../console/llm/llm.instance.root.page"
import { ROUTES } from "../routes.const"

export const LLMRoutes = (
    <Route path={ROUTES.LLM_ROOT} element={<LLMInstanceRootPage />}>
        <Route index element={<Navigate to={ROUTES.LLM_INSTANCE} replace />} />
        <Route path={ROUTES.LLM_INSTANCE} element={<LLMInstanceListPage />} />
        <Route path={`${ROUTES.LLM_INSTANCE}/:id`} element={<LLMInstancePage />} />
    </Route>
)
