import { lazy } from "react"
import { Navigate, Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const ProfileListPage = lazy(() => import("../console/profile/profile.list.page"))
const ProfileRootPage = lazy(() => import("../console/profile/profile.root.page"))

export const ProfileRoutes = (
    <Route path={ROUTES.PROFILE_ROOT} element={<ProfileRootPage />}>
        <Route index element={<Navigate to={ROUTES.PROFILE_INSTANCE} replace />} />
        <Route path={ROUTES.PROFILE_INSTANCE} element={<ProfileListPage />} />
    </Route>
)
