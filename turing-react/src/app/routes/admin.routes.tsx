import { Navigate, Route } from "react-router-dom"
import AdminSettingsPage from "../console/admin-settings/admin.settings.page"
import AdminGroupPage from "../console/admin-settings/admin.group.page"
import AdminGroupsListPage from "../console/admin-settings/admin.groups.list.page"
import AdminRolePage from "../console/admin-settings/admin.role.page"
import AdminRolesListPage from "../console/admin-settings/admin.roles.list.page"
import AdminUserPage from "../console/admin-settings/admin.user.page"
import AdminUsersListPage from "../console/admin-settings/admin.users.list.page"
import { ROUTES } from "../routes.const"

export const AdminRoutes = (
    <Route path={ROUTES.ADMIN_ROOT} element={<AdminSettingsPage />}>
        <Route index element={<Navigate to={`${ROUTES.ADMIN_ROOT}/users`} replace />} />
        <Route path="users" element={<AdminUsersListPage />} />
        <Route path="users/:username" element={<AdminUserPage />} />
        <Route path="groups" element={<AdminGroupsListPage />} />
        <Route path="groups/:groupId" element={<AdminGroupPage />} />
        <Route path="roles" element={<AdminRolesListPage />} />
        <Route path="roles/:roleId" element={<AdminRolePage />} />
    </Route>
)
