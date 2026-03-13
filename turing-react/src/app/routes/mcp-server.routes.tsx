import { Navigate, Route } from "react-router-dom"
import McpServerListPage from "../console/mcp/mcp.server.list.page"
import McpServerPage from "../console/mcp/mcp.server.page"
import McpServerRootPage from "../console/mcp/mcp.server.root.page"
import { ROUTES } from "../routes.const"

export const McpServerRoutes = (
    <Route path={ROUTES.MCP_ROOT} element={<McpServerRootPage />}>
        <Route index element={<Navigate to={ROUTES.MCP_INSTANCE} replace />} />
        <Route path={ROUTES.MCP_INSTANCE} element={<McpServerListPage />} />
        <Route path={`${ROUTES.MCP_INSTANCE}/:id`} element={<McpServerPage />} />
    </Route>
)
