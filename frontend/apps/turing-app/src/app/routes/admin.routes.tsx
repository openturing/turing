import { lazy } from "react"
import { Navigate, Route } from "react-router-dom"
import { ROUTES } from "../routes.const"

const AdminSettingsRootPage = lazy(() => import("../console/admin-settings/admin.settings.root.page"))
const AdminSettingsPage = lazy(() => import("../console/admin-settings/admin.settings.page"))
const AdminGroupPage = lazy(() => import("../console/admin-settings/admin.group.page"))
const AdminGroupsListPage = lazy(() => import("../console/admin-settings/admin.groups.list.page"))
const AdminRolePage = lazy(() => import("../console/admin-settings/admin.role.page"))
const AdminRolesListPage = lazy(() => import("../console/admin-settings/admin.roles.list.page"))
const AdminUserPage = lazy(() => import("../console/admin-settings/admin.user.page"))
const AdminUsersListPage = lazy(() => import("../console/admin-settings/admin.users.list.page"))
const GlobalSettingsPage = lazy(() => import("../console/system/global-settings.page"))
const SystemInfoPage = lazy(() => import("../console/system/system-info.page"))
const TokenInstanceListPage = lazy(() => import("../console/token/token.instance.list.page"))
const TokenInstancePage = lazy(() => import("../console/token/token.instance.page"))

export const AdminRoutes = (
    <Route path={ROUTES.ADMIN_ROOT} element={<AdminSettingsRootPage />}>
        <Route element={<AdminSettingsPage />}>
            <Route index element={<Navigate to={`${ROUTES.ADMIN_ROOT}/users`} replace />} />
            <Route path="users" element={<AdminUsersListPage />} />
            <Route path="users/:username" element={<AdminUserPage />} />
            <Route path="groups" element={<AdminGroupsListPage />} />
            <Route path="groups/:groupId" element={<AdminGroupPage />} />
            <Route path="roles" element={<AdminRolesListPage />} />
            <Route path="roles/:roleId" element={<AdminRolePage />} />
            <Route path="tokens" element={<TokenInstanceListPage />} />
            <Route path="tokens/:id" element={<TokenInstancePage />} />
            <Route path="settings" element={<GlobalSettingsPage />} />
            <Route path="system-info" element={<SystemInfoPage />} />
        </Route>
    </Route>
)
