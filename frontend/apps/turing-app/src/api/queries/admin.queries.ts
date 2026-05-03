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
