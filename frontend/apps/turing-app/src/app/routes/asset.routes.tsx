import { Route } from "react-router-dom";
import AssetPage from "../console/asset/asset.page";
import { ROUTES } from "../routes.const";

export const AssetRoutes = (
    <Route path={ROUTES.ASSET_ROOT} element={<AssetPage />} />
);
