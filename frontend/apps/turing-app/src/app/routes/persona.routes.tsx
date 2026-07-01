import { lazy } from "react"
import { Navigate, Route, useParams } from "react-router-dom"
import { ROUTES } from "../routes.const"

const PersonaListPage = lazy(() => import("../console/persona/persona.list.page"))
const PersonaPage = lazy(() => import("../console/persona/persona.page"))
const PersonaRootPage = lazy(() => import("../console/persona/persona.root.page"))
const PersonaAiChatPage = lazy(() => import("../console/persona/ai-chat/persona.ai-chat.page"))
const PersonaSuggestPage = lazy(() => import("../console/persona/persona.suggest.page"))
const PersonaGeneralSection = lazy(() => import("../console/persona/sections/persona.instance.general.section"))
const PersonaSystemInstructionSection = lazy(() => import("../console/persona/sections/persona.instance.system-instruction.section"))
const PersonaStyleSection = lazy(() => import("../console/persona/sections/persona.instance.style.section"))
const PersonaAudienceSection = lazy(() => import("../console/persona/sections/persona.instance.audience.section"))
const PersonaNotebookSection = lazy(() => import("../console/persona/sections/persona.instance.notebook.section"))
const PersonaFitReportSection = lazy(() => import("../console/persona/sections/persona.instance.fit-report.section"))
const PersonaGuidelinesSection = lazy(() => import("../console/persona/sections/persona.instance.guidelines.section"))

function RedirectToPersonaGeneral() {
    const { id } = useParams();
    return <Navigate to={`${ROUTES.PERSONA_INSTANCE}/${id}/general`} replace />;
}

export const PersonaRoutes = (
    <Route path={ROUTES.PERSONA_ROOT} element={<PersonaRootPage />}>
        <Route index element={<Navigate to={ROUTES.PERSONA_INSTANCE} replace />} />
        <Route path={ROUTES.PERSONA_INSTANCE} element={<PersonaListPage />} />
        <Route path={`${ROUTES.PERSONA_INSTANCE}/suggest`} element={<PersonaSuggestPage />} />
        <Route path={`${ROUTES.PERSONA_INSTANCE}/new/ai-chat`} element={<PersonaAiChatPage />} />
        <Route path={`${ROUTES.PERSONA_INSTANCE}/:id`} element={<PersonaPage />}>
            <Route index element={<RedirectToPersonaGeneral />} />
            <Route path={'general'} element={<PersonaGeneralSection />} />
            <Route path={'system-instruction'} element={<PersonaSystemInstructionSection />} />
            <Route path={'style'} element={<PersonaStyleSection />} />
            <Route path={'audience'} element={<PersonaAudienceSection />} />
            <Route path={'notebook'} element={<PersonaNotebookSection />} />
            <Route path={'fit-report'} element={<PersonaFitReportSection />} />
            <Route path={'guidelines'} element={<PersonaGuidelinesSection />} />
        </Route>
    </Route>
)
