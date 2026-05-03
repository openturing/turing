import { lazy } from "react";
import { Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const GlobalSettingsPage = lazy(() => import("../console/system/global-settings.page"));
const GlobalSettingsRootPage = lazy(() => import("../console/system/global-settings.root.page"));

export const GlobalSettingsRoutes = (
    <Route path={ROUTES.GLOBAL_SETTINGS} element={<GlobalSettingsRootPage />}>
        <Route index element={<GlobalSettingsPage />} />
    </Route>
);
