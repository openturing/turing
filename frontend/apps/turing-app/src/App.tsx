import { Navigate, Route, Routes } from "react-router-dom"
import ConsoleRootPage from "./app/console/console.root.page"
import DashboardPage from "./app/console/dashboard/dashboard.page"
import HomePage from "./app/console/home/home.page"
import LoginPage from "./app/login/login.page"
import RegisterPage from "./app/register/register.page"
import SetupPage from "./app/setup/setup.page"
import {
  AdminRoutes,
  AIAgentRoutes,
  AssetRoutes,
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
  CustomToolRoutes,
  SERoutes,
  SNRoutes,
  StoreRoutes,
  TokenUsageRoutes,
  UserRoutes
} from "./app/routes"
import { ROUTES } from "./app/routes.const"
import { ThemeProvider } from "./components/theme-provider"
import { Toaster } from "./components/ui/sonner"
import { TuringServiceProvider } from "./contexts/TuringServiceContext"
import { BreadcrumbProvider } from "./contexts/breadcrumb.context"
import AnnSearchPage from "./search/pages/ann-search.page"
import SearchPage from "./search/pages/search.page"

function App() {
  return (
    <div className="App">
      <ThemeProvider defaultTheme="dark" storageKey="vite-ui-theme">
        <BreadcrumbProvider>
          <TuringServiceProvider>
            <Toaster />
            <Routes>
              <Route path={ROUTES.ROOT} element={<Navigate to={ROUTES.CONSOLE} replace />} />
              <Route path={ROUTES.LOGIN} element={<LoginPage />} />
              <Route path={ROUTES.REGISTER} element={<RegisterPage />} />
              <Route path={ROUTES.SETUP} element={<SetupPage />} />
              <Route path={`${ROUTES.SN_SEARCH}/:siteName`} element={<SearchPage />} />
              <Route path={`${ROUTES.ANN_SEARCH}/:siteName`} element={<AnnSearchPage />} />
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
                {AIAgentRoutes}
                {IntegrationRoutes}
                {LoggingRoutes}
                {ExchangeRoutes}
                {MarketplaceRoutes}
                {GraphqlRoutes}
                {AssetRoutes}
                {GitRoutes}
                {PageRoutes}
                {ChatRoutes}
                {TokenUsageRoutes}
                {UserRoutes}
                {AdminRoutes}
              </Route>
            </Routes>
          </TuringServiceProvider>
        </BreadcrumbProvider>
      </ThemeProvider>
    </div>
  )
}

export default App