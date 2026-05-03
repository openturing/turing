import { lazy } from "react";
import { Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const MarketplaceListPage = lazy(() => import("../console/marketplace/marketplace.list.page"));
const MarketplaceRootPage = lazy(() => import("../console/marketplace/marketplace.root.page"));

export const MarketplaceRoutes = (
    <Route path={ROUTES.MARKETPLACE_ROOT} element={<MarketplaceRootPage />}>
        <Route index element={<MarketplaceListPage />} />
    </Route>
);
