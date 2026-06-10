import { lazy } from "react";
import { Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const SkillListPage = lazy(() => import("../console/skill/skill.list.page"));
const SkillRootPage = lazy(() => import("../console/skill/skill.root.page"));

export const SkillRoutes = (
    <Route path={ROUTES.SKILL_ROOT} element={<SkillRootPage />}>
        <Route index element={<SkillListPage />} />
    </Route>
);
