import { lazy } from "react";
import { Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const PagePage = lazy(() => import("../console/page/page.page"));

export const PageRoutes = (
    <Route path={ROUTES.PAGE_ROOT} element={<PagePage />} />
);
