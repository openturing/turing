import { lazy } from "react"
import { Navigate, Route, useParams } from "react-router-dom"
import { ROUTES } from "../routes.const"

const LLMInstanceListPage = lazy(() => import("../console/llm/llm.instance.list.page"))
const LLMInstancePage = lazy(() => import("../console/llm/llm.instance.page"))
const LLMInstanceRootPage = lazy(() => import("../console/llm/llm.instance.root.page"))
const LLMInstanceGeneralSection = lazy(() => import("../console/llm/sections/llm.instance.general.section"))
const LLMInstanceModelSection = lazy(() => import("../console/llm/sections/llm.instance.model.section"))
const LLMInstanceGenerationSection = lazy(() => import("../console/llm/sections/llm.instance.generation.section"))
const LLMInstanceCapabilitiesSection = lazy(() => import("../console/llm/sections/llm.instance.capabilities.section"))

function RedirectToLLMGeneral() {
    const { id } = useParams();
    return <Navigate to={`${ROUTES.LLM_INSTANCE}/${id}/general`} replace />;
}

export const LLMRoutes = (
    <Route path={ROUTES.LLM_ROOT} element={<LLMInstanceRootPage />}>
        <Route index element={<Navigate to={ROUTES.LLM_INSTANCE} replace />} />
        <Route path={ROUTES.LLM_INSTANCE} element={<LLMInstanceListPage />} />
        <Route path={`${ROUTES.LLM_INSTANCE}/:id`} element={<LLMInstancePage />}>
            <Route index element={<RedirectToLLMGeneral />} />
            <Route path={'general'} element={<LLMInstanceGeneralSection />} />
            <Route path={'model'} element={<LLMInstanceModelSection />} />
            <Route path={'generation'} element={<LLMInstanceGenerationSection />} />
            <Route path={'capabilities'} element={<LLMInstanceCapabilitiesSection />} />
        </Route>
    </Route>
)
