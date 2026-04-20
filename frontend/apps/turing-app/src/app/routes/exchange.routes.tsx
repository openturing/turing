import { Route } from "react-router-dom";
import ExchangeImportPage from "../console/exchange/exchange.import.page";
import ExchangeImportRootPage from "../console/exchange/exchange.import.root.page";
import { ROUTES } from "../routes.const";

export const ExchangeRoutes = (
    <Route path={ROUTES.EXCHANGE_ROOT} element={<ExchangeImportRootPage />}>
        <Route path={ROUTES.EXCHANGE_IMPORT} element={<ExchangeImportPage />} />
    </Route>
);
