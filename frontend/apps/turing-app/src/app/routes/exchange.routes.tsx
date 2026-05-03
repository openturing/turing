import { lazy } from "react";
import { Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const ExchangeImportPage = lazy(() => import("../console/exchange/exchange.import.page"));
const ExchangeImportRootPage = lazy(() => import("../console/exchange/exchange.import.root.page"));

export const ExchangeRoutes = (
    <Route path={ROUTES.EXCHANGE_ROOT} element={<ExchangeImportRootPage />}>
        <Route path={ROUTES.EXCHANGE_IMPORT} element={<ExchangeImportPage />} />
    </Route>
);
