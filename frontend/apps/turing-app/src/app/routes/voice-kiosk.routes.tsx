import { lazy } from "react"
import { Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

// T149 / §X.6.c — full-screen, chrome-free voice kiosk. Mounted as a standalone
// route (outside the console shell) so booth/lobby deployments have no sidebar.
const VoiceKioskPage = lazy(() => import("../console/voice-kiosk/voice-kiosk.page"))

export const VoiceKioskRoutes = (
    <Route path={ROUTES.VOICE_KIOSK} element={<VoiceKioskPage />} />
)
