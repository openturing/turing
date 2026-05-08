import { lazy } from "react"
import { Navigate, Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const PersonaListPage = lazy(() => import("../console/persona/persona.list.page"))
const PersonaPage = lazy(() => import("../console/persona/persona.page"))
const PersonaRootPage = lazy(() => import("../console/persona/persona.root.page"))
const PersonaAiChatPage = lazy(() => import("../console/persona/ai-chat/persona.ai-chat.page"))

export const PersonaRoutes = (
    <Route path={ROUTES.PERSONA_ROOT} element={<PersonaRootPage />}>
        <Route index element={<Navigate to={ROUTES.PERSONA_INSTANCE} replace />} />
        <Route path={ROUTES.PERSONA_INSTANCE} element={<PersonaListPage />} />
        <Route path={`${ROUTES.PERSONA_INSTANCE}/new/ai-chat`} element={<PersonaAiChatPage />} />
        <Route path={`${ROUTES.PERSONA_INSTANCE}/:id`} element={<PersonaPage />} />
    </Route>
)
