import { Navigate, Route } from "react-router-dom"
import UserAccountPage from "../console/user/user.account.page"
import UserPreferencesPage from "../console/user/preferences/user.preferences.page"
import UserProfilePage from "../console/user/profile/user.profile.page"
import { ROUTES } from "../routes.const"

export const UserRoutes = (
    <Route path={ROUTES.USER_ACCOUNT} element={<UserAccountPage />}>
        <Route index element={<Navigate to="profile" replace />} />
        <Route path="profile" element={<UserProfilePage />} />
        <Route path="preferences" element={<UserPreferencesPage />} />
    </Route>
)
