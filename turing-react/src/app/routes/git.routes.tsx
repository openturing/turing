import { Route } from "react-router-dom";
import GitPage from "../console/git/git.page";
import { ROUTES } from "../routes.const";

export const GitRoutes = (
    <Route path={ROUTES.GIT_ROOT} element={<GitPage />} />
);
