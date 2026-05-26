import { lazy } from "react";
import { Navigate, Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

const RoutineListPage = lazy(() => import("../console/routine/routine.list.page"));
const RoutinePage = lazy(() => import("../console/routine/routine.page"));
const RoutineRootPage = lazy(() => import("../console/routine/routine.root.page"));

/**
 * T48 admin UI — list/create/edit routines fired by {@code scheduleAgent}
 * chat-flow nodes. Deployment-wide (not agent-scoped) because the same
 * routine is reused across agents.
 *
 * @since 2026.3.1
 */
export const RoutineRoutes = (
  <Route path={ROUTES.ROUTINE_ROOT} element={<RoutineRootPage />}>
    <Route index element={<Navigate to={ROUTES.ROUTINE_INSTANCE} replace />} />
    <Route path={ROUTES.ROUTINE_INSTANCE} element={<RoutineListPage />} />
    <Route path={`${ROUTES.ROUTINE_INSTANCE}/:id`} element={<RoutinePage />} />
  </Route>
);
