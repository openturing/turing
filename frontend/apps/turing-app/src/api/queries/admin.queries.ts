import { useQuery } from '@tanstack/react-query';
import type { TurGroup } from '@/models/auth/group';
import type { TurKeycloakGroup } from '@/models/auth/keycloak-group';
import type { TurKeycloakUser } from '@/models/auth/keycloak-user';
import type { TurRole } from '@/models/auth/role';
import type { TurUser } from '@/models/auth/user';
import { TurAdminUserService } from '@/services/auth/admin-user.service';
import { TurGroupService } from '@/services/auth/group.service';
import { TurKeycloakAdminService } from '@/services/auth/keycloak-admin.service';
import { TurRoleService } from '@/services/auth/role.service';
import { queryKeys } from './keys';

const userService = new TurAdminUserService();
const groupService = new TurGroupService();
const keycloakAdminService = new TurKeycloakAdminService();
const roleService = new TurRoleService();

/**
 * Normalised view shared by both Keycloak and JPA-backed user lists. Pages
 * shouldn't care which source produced the row — they only render the
 * fields that exist on both.
 */
export type AdminUserItem = {
  username: string;
  email?: string;
  description?: string;
};

export type AdminGroupItem = {
  id: string;
  name: string;
  description?: string;
};

/**
 * Lists users via the active auth backend. The {@code keycloak} flag comes
 * from {@code useAuthDiscovery}; the hook is disabled until discovery
 * finishes so the wrong backend is never queried by mistake. The query key
 * splits cache by source so toggling backends invalidates cleanly.
 */
export function useAdminUsers(keycloak: boolean | null) {
  const source = keycloak ? 'keycloak' : 'jpa';
  return useQuery<AdminUserItem[]>({
    queryKey: keycloak === null
      ? ['admin-users', 'list', 'pending']
      : queryKeys.adminUsers.list(source),
    queryFn: async () => {
      if (keycloak) {
        const list = await keycloakAdminService.listUsers();
        return list.map((u: TurKeycloakUser) => ({
          username: u.username,
          email: u.email,
          description: [u.firstName, u.lastName].filter(Boolean).join(' ') || u.email,
        }));
      }
      const list = await userService.query();
      return list.map((u: TurUser) => ({
        username: u.username,
        email: u.email,
        description: u.email,
      }));
    },
    enabled: keycloak !== null,
  });
}

export function useAdminGroups(keycloak: boolean | null) {
  const source = keycloak ? 'keycloak' : 'jpa';
  return useQuery<AdminGroupItem[]>({
    queryKey: keycloak === null
      ? ['admin-groups', 'list', 'pending']
      : queryKeys.adminGroups.list(source),
    queryFn: async () => {
      if (keycloak) {
        const list = await keycloakAdminService.listGroups();
        return list.map((g: TurKeycloakGroup) => ({
          id: g.id,
          name: g.name,
          description: g.path,
        }));
      }
      const list = await groupService.query();
      return list.map((g: TurGroup) => ({
        id: g.id,
        name: g.name,
        description: g.description,
      }));
    },
    enabled: keycloak !== null,
  });
}

export function useAdminRoles() {
  return useQuery<TurRole[]>({
    queryKey: queryKeys.adminRoles.list(),
    queryFn: () => roleService.query(),
  });
}

/**
 * T1018 — one user from whichever backend is live.
 *
 * The two sources return different shapes, so this returns the raw record and
 * the caller renders the form it already has for that source. Disabled while
 * discovery is pending and on the `new` route, which is what the detail page's
 * two guards did inside an effect.
 */
export function useAdminUser(username: string | undefined, keycloak: boolean | null) {
  const source = keycloak ? 'keycloak' : 'jpa';
  return useQuery<TurUser | TurKeycloakUser>({
    queryKey:
      keycloak === null || !username
        ? ['admin-users', 'detail', 'pending']
        : queryKeys.adminUsers.detail(source, username),
    queryFn: () =>
      keycloak
        ? keycloakAdminService.getUser(username as string)
        : userService.get(username as string),
    enabled: keycloak !== null && Boolean(username) && username !== 'new',
  });
}

/** T1018 — one group, from whichever backend is live. See {@link useAdminUser}. */
export function useAdminGroup(groupId: string | undefined, keycloak: boolean | null) {
  const source = keycloak ? 'keycloak' : 'jpa';
  return useQuery<TurGroup | TurKeycloakGroup>({
    queryKey:
      keycloak === null || !groupId
        ? ['admin-groups', 'detail', 'pending']
        : queryKeys.adminGroups.detail(source, groupId),
    queryFn: () =>
      keycloak
        ? keycloakAdminService.getGroup(groupId as string)
        : groupService.get(groupId as string),
    enabled: keycloak !== null && Boolean(groupId) && groupId !== 'new',
  });
}

/** T1018 — one role. Roles are JPA-only, so there is no source to split on. */
export function useAdminRole(roleId: string | undefined) {
  return useQuery<TurRole>({
    queryKey: roleId ? queryKeys.adminRoles.detail(roleId) : ['admin-roles', 'detail', 'pending'],
    queryFn: () => roleService.get(roleId as string),
    enabled: Boolean(roleId) && roleId !== 'new',
  });
}
