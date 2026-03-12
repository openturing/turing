import { Route } from "react-router-dom";
import GlobalSettingsPage from "../console/system/global-settings.page";
import GlobalSettingsRootPage from "../console/system/global-settings.root.page";
import { ROUTES } from "../routes.const";

export const GlobalSettingsRoutes = (
    <Route path={ROUTES.GLOBAL_SETTINGS} element={<GlobalSettingsRootPage />}>
        <Route index element={<GlobalSettingsPage />} />
    </Route>
);
