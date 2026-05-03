import { lazy } from "react";
import { Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const GitPage = lazy(() => import("../console/git/git.page"));
const GitRepoPage = lazy(() => import("../console/git/git.repo.page"));

export const GitRoutes = (
    <>
        <Route path={ROUTES.GIT_ROOT} element={<GitPage />} />
        <Route path={`${ROUTES.GIT_ROOT}/:name`} element={<GitRepoPage />} />
    </>
);
