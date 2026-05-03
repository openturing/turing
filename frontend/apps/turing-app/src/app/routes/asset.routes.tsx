import { lazy } from "react";
import { Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const AssetPage = lazy(() => import("../console/asset/asset.page"));

export const AssetRoutes = (
    <Route path={ROUTES.ASSET_ROOT} element={<AssetPage />} />
);
