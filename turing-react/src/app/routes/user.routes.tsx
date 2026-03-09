import { Route } from "react-router-dom"
import UserAccountPage from "../console/user/user.account.page"
import { ROUTES } from "../routes.const"

export const UserRoutes = (
    <Route path={ROUTES.USER_ACCOUNT} element={<UserAccountPage />} />
)
