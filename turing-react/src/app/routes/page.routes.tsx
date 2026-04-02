import { Route } from "react-router-dom";
import PagePage from "../console/page/page.page";
import { ROUTES } from "../routes.const";

export const PageRoutes = (
    <Route path={ROUTES.PAGE_ROOT} element={<PagePage />} />
);
