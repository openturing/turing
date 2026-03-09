import { Navigate, Route, Routes } from "react-router-dom"
import ConsoleRootPage from "./app/console/console.root.page"
import RealTimeDashboardPage from "./app/console/dashboard/dashboard.page"
import LoginPage from "./app/login/login.page"
import {
  ChatRoutes,
  ExchangeRoutes,
  GlobalSettingsRoutes,
  GraphqlRoutes,
  IntegrationRoutes,
  LLMRoutes,
  LoggingRoutes,
  SERoutes,
  SNRoutes,
  StoreRoutes,
  TokenRoutes,
  TokenUsageRoutes,
  UserRoutes
} from "./app/routes"
import { ROUTES } from "./app/routes.const"
import { ThemeProvider } from "./components/theme-provider"
import { Toaster } from "./components/ui/sonner"
import { TuringServiceProvider } from "./contexts/TuringServiceContext"
import { BreadcrumbProvider } from "./contexts/breadcrumb.context"
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
              <Route path={ROUTES.DASHBOARD} element={<RealTimeDashboardPage />} />
              <Route path={ROUTES.LOGIN} element={<LoginPage />} />
              <Route path={`${ROUTES.SN_SEARCH}/:siteName`} element={<SearchPage />} />
              <Route path={ROUTES.CONSOLE} element={<ConsoleRootPage />}>
                <Route index element={<Navigate to={ROUTES.SN_INSTANCE} replace />} />
                {SERoutes}
                {SNRoutes}
                {StoreRoutes}
                {TokenRoutes}
                {LLMRoutes}
                {IntegrationRoutes}
                {LoggingRoutes}
                {ExchangeRoutes}
                {GlobalSettingsRoutes}
                {GraphqlRoutes}
                {ChatRoutes}
                {TokenUsageRoutes}
                {UserRoutes}
              </Route>
            </Routes>
          </TuringServiceProvider>
        </BreadcrumbProvider>
      </ThemeProvider>
    </div>
  )
}

export default App