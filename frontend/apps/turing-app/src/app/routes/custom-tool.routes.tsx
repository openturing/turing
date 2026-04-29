import { Navigate, Route } from "react-router-dom";
import CustomToolListPage from "../console/custom-tool/custom-tool.list.page";
import CustomToolPage from "../console/custom-tool/custom-tool.page";
import CustomToolRootPage from "../console/custom-tool/custom-tool.root.page";
import { ROUTES } from "../routes.const";

/**
 * @since 2026.2.5
 */
export const CustomToolRoutes = (
  <Route path={ROUTES.CUSTOM_TOOL_ROOT} element={<CustomToolRootPage />}>
    <Route index element={<Navigate to={ROUTES.CUSTOM_TOOL_INSTANCE} replace />} />
    <Route path={ROUTES.CUSTOM_TOOL_INSTANCE} element={<CustomToolListPage />} />
    <Route path={`${ROUTES.CUSTOM_TOOL_INSTANCE}/:id`} element={<CustomToolPage />} />
  </Route>
);
