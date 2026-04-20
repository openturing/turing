import { Route } from "react-router-dom";
import MarketplaceListPage from "../console/marketplace/marketplace.list.page";
import MarketplaceRootPage from "../console/marketplace/marketplace.root.page";
import { ROUTES } from "../routes.const";

export const MarketplaceRoutes = (
    <Route path={ROUTES.MARKETPLACE_ROOT} element={<MarketplaceRootPage />}>
        <Route index element={<MarketplaceListPage />} />
    </Route>
);
