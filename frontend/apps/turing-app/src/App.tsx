import { lazy, Suspense } from "react"
import { Navigate, Route, Routes } from "react-router-dom"
// Top-level pages are lazy-loaded so the auth/login bundle stays small —
// users hitting /login don't need to download the dashboard, and vice-versa.
const LoginPage = lazy(() => import("./app/login/login.page"))
const RegisterPage = lazy(() => import("./app/register/register.page"))
const SetupPage = lazy(() => import("./app/setup/setup.page"))
const ConsoleToBentoRedirect = lazy(() => import("./app/routes/console-redirect"))
// Block AG (T569/T570) — Bento is the interface; the legacy console tree is
// retired. Only two route groups survive outside bento: the standalone,
// chrome-free voice kiosk and the bento tree itself.
import { BentoRoutes, VoiceKioskRoutes } from "./app/routes"
import { ROUTES } from "./app/routes.const"
import { ThemeProvider } from "./components/theme-provider"
import { Toaster } from "./components/ui/sonner"
import { TuringServiceProvider } from "./contexts/TuringServiceContext"
import { BreadcrumbProvider } from "./contexts/breadcrumb.context"
const AnnSearchPage = lazy(() => import("./search/pages/ann-search.page"))
const SearchPage = lazy(() => import("./search/pages/search.page"))

function App() {
  return (
    <div className="App">
      <ThemeProvider defaultTheme="dark" storageKey="vite-ui-theme">
        <BreadcrumbProvider>
          <TuringServiceProvider>
            <Toaster />
            <Suspense fallback={<div className="h-screen w-screen" aria-busy="true" />}>
            <Routes>
              <Route path={ROUTES.ROOT} element={<Navigate to={ROUTES.BENTO_HOME} replace />} />
              <Route path={ROUTES.LOGIN} element={<LoginPage />} />
              <Route path={ROUTES.REGISTER} element={<RegisterPage />} />
              <Route path={ROUTES.SETUP} element={<SetupPage />} />
              <Route path={`${ROUTES.SN_SEARCH}/:siteName`} element={<SearchPage />} />
              <Route path={`${ROUTES.ANN_SEARCH}/:siteName`} element={<AnnSearchPage />} />
              {BentoRoutes}
              {VoiceKioskRoutes}
              {/* Retired console deep-links resolve to their bento equivalent. */}
              <Route path={`${ROUTES.CONSOLE}/*`} element={<ConsoleToBentoRedirect />} />
            </Routes>
            </Suspense>
          </TuringServiceProvider>
        </BreadcrumbProvider>
      </ThemeProvider>
    </div>
  )
}

export default App