import { lazy } from "react"
import { Navigate, Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const McpServerListPage = lazy(() => import("../console/mcp/mcp.server.list.page"))
const McpServerPage = lazy(() => import("../console/mcp/mcp.server.page"))
const McpServerRootPage = lazy(() => import("../console/mcp/mcp.server.root.page"))

export const McpServerRoutes = (
    <Route path={ROUTES.MCP_ROOT} element={<McpServerRootPage />}>
        <Route index element={<Navigate to={ROUTES.MCP_INSTANCE} replace />} />
        <Route path={ROUTES.MCP_INSTANCE} element={<McpServerListPage />} />
        <Route path={`${ROUTES.MCP_INSTANCE}/:id`} element={<McpServerPage />} />
    </Route>
)
