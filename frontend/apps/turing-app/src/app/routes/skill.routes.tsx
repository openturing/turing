import { lazy } from "react";
import { Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const SkillPage = lazy(() => import("../console/skill/skill.page"));
const SkillEditorPage = lazy(() => import("../console/skill/skill-editor.page"));

export const SkillRoutes = (
    <>
        <Route path={ROUTES.SKILL_ROOT} element={<SkillPage />} />
        <Route path={`${ROUTES.SKILL_ROOT}/:id`} element={<SkillEditorPage />} />
    </>
);
