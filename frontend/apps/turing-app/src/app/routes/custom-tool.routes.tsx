import { lazy } from "react";
import { Navigate, Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const CustomToolListPage = lazy(() => import("../console/custom-tool/custom-tool.list.page"));
const CustomToolPage = lazy(() => import("../console/custom-tool/custom-tool.page"));
const CustomToolRootPage = lazy(() => import("../console/custom-tool/custom-tool.root.page"));

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
