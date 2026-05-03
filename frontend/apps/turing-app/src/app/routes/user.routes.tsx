import { lazy } from "react"
import { Navigate, Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const UserAccountPage = lazy(() => import("../console/user/user.account.page"))
const UserPreferencesPage = lazy(() => import("../console/user/preferences/user.preferences.page"))
const UserProfilePage = lazy(() => import("../console/user/profile/user.profile.page"))

export const UserRoutes = (
    <Route path={ROUTES.USER_ACCOUNT} element={<UserAccountPage />}>
        <Route index element={<Navigate to="profile" replace />} />
        <Route path="profile" element={<UserProfilePage />} />
        <Route path="preferences" element={<UserPreferencesPage />} />
    </Route>
)
