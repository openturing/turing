import { lazy, Suspense } from "react"
import { Navigate, Route, Routes } from "react-router-dom"
import ConsoleRootPage from "./app/console/console.root.page"
// Top-level pages are lazy-loaded so the auth/login bundle stays small —
// users hitting /login don't need to download the dashboard, and vice-versa.
const DashboardPage = lazy(() => import("./app/console/dashboard/dashboard.page"))
const HomePage = lazy(() => import("./app/console/home/home.page"))
const LoginPage = lazy(() => import("./app/login/login.page"))
const RegisterPage = lazy(() => import("./app/register/register.page"))
const SetupPage = lazy(() => import("./app/setup/setup.page"))
import {
  AdminRoutes,
  AIAgentRoutes,
  AssetRoutes,
  SkillRoutes,
  TenantRoutes,
  BentoRoutes,
  GitRoutes,
  PageRoutes,
  ChatRoutes,
  EmbeddingModelRoutes,
  ExchangeRoutes,
  GraphqlRoutes,
  IntegrationRoutes,
  LLMRoutes,
  LoggingRoutes,
  MarketplaceRoutes,
  McpServerRoutes,
  PersonaRoutes,
  CustomToolRoutes,
  RoutineRoutes,
  ChatWebhookRoutes,
  SERoutes,
  SNRoutes,
  StoreRoutes,
  TokenUsageRoutes,
  ChatAnalyticsRoutes,
  ParkedConversationsRoutes,
  UserRoutes
} from "./app/routes"
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
              <Route path={ROUTES.ROOT} element={<Navigate to={ROUTES.CONSOLE} replace />} />
              <Route path={ROUTES.LOGIN} element={<LoginPage />} />
              <Route path={ROUTES.REGISTER} element={<RegisterPage />} />
              <Route path={ROUTES.SETUP} element={<SetupPage />} />
              <Route path={`${ROUTES.SN_SEARCH}/:siteName`} element={<SearchPage />} />
              <Route path={`${ROUTES.ANN_SEARCH}/:siteName`} element={<AnnSearchPage />} />
              {BentoRoutes}
              <Route path={ROUTES.CONSOLE} element={<ConsoleRootPage />}>
                <Route index element={<Navigate to={ROUTES.HOME} replace />} />
                <Route path="home" element={<HomePage />} />
                <Route path="dashboard" element={<DashboardPage />} />
                {SERoutes}
                {SNRoutes}
                {StoreRoutes}
                {EmbeddingModelRoutes}
                {LLMRoutes}
                {McpServerRoutes}
                {CustomToolRoutes}
                {RoutineRoutes}
                {ChatWebhookRoutes}
                {AIAgentRoutes}
                {PersonaRoutes}
                {IntegrationRoutes}
                {LoggingRoutes}
                {ExchangeRoutes}
                {MarketplaceRoutes}
                {GraphqlRoutes}
                {AssetRoutes}
                {SkillRoutes}
                {TenantRoutes}
                {GitRoutes}
                {PageRoutes}
                {ChatRoutes}
                {TokenUsageRoutes}
                {ChatAnalyticsRoutes}
                {ParkedConversationsRoutes}
                {UserRoutes}
                {AdminRoutes}
              </Route>
            </Routes>
            </Suspense>
          </TuringServiceProvider>
        </BreadcrumbProvider>
      </ThemeProvider>
    </div>
  )
}

export default App