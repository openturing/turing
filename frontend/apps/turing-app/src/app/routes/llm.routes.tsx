import { lazy } from "react"
import { Navigate, Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const LLMInstanceListPage = lazy(() => import("../console/llm/llm.instance.list.page"))
const LLMInstancePage = lazy(() => import("../console/llm/llm.instance.page"))
const LLMInstanceRootPage = lazy(() => import("../console/llm/llm.instance.root.page"))

export const LLMRoutes = (
    <Route path={ROUTES.LLM_ROOT} element={<LLMInstanceRootPage />}>
        <Route index element={<Navigate to={ROUTES.LLM_INSTANCE} replace />} />
        <Route path={ROUTES.LLM_INSTANCE} element={<LLMInstanceListPage />} />
        <Route path={`${ROUTES.LLM_INSTANCE}/:id`} element={<LLMInstancePage />} />
    </Route>
)
