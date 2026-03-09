import { Route } from "react-router-dom";
import GlobalSettingsPage from "../console/system/global-settings.page";
import { ROUTES } from "../routes.const";

export const GlobalSettingsRoutes = (
    <Route path={ROUTES.GLOBAL_SETTINGS} element={<GlobalSettingsPage />} />
);
