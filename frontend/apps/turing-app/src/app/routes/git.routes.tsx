import { Route } from "react-router-dom";
import GitPage from "../console/git/git.page";
import GitRepoPage from "../console/git/git.repo.page";
import { ROUTES } from "../routes.const";

export const GitRoutes = (
    <>
        <Route path={ROUTES.GIT_ROOT} element={<GitPage />} />
        <Route path={`${ROUTES.GIT_ROOT}/:name`} element={<GitRepoPage />} />
    </>
);
