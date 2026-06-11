/**
 * T278 / §XIV.6.1 — React Query hooks for multi-tenancy: "my tenants", the
 * current-tenant switch (which re-scopes every cached query), and signup.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/api/queries/keys";
import { setCurrentTenant } from "@/lib/axios";
import type {
  TurTenant,
  TurTenantSignupRequest,
} from "@/models/tenant/tenant.model";
import { TurTenantService } from "@/services/tenant/tur-tenant.service";

const service = new TurTenantService();

export function useMyTenants() {
  return useQuery<TurTenant[]>({
    queryKey: queryKeys.tenants.mine(),
    queryFn: () => service.myTenants(),
  });
}

export function useSwitchTenant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (slug: string) => service.switchTenant(slug),
    onSuccess: (tenant) => {
      // Re-scope the whole client to the new tenant: persist the header value
      // then drop every cached query so reads refetch under the new tenant.
      setCurrentTenant(tenant.slug);
      queryClient.clear();
    },
  });
}

// ---- T279 platform-admin console -------------------------------------------

export function useAllTenants() {
  return useQuery<TurTenant[]>({
    queryKey: queryKeys.tenants.adminList(),
    queryFn: () => service.listAll(),
  });
}

export function useSetTenantStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, suspend }: { id: string; suspend: boolean }) =>
      suspend ? service.suspend(id) : service.activate(id),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.tenants.adminList() }),
  });
}

export function useImpersonateTenant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => service.impersonate(id),
    onSuccess: (tenant) => {
      setCurrentTenant(tenant.slug);
      queryClient.clear();
    },
  });
}

export function useTenantSignup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: TurTenantSignupRequest) => service.signup(request),
    onSuccess: (tenant) => {
      setCurrentTenant(tenant.slug);
      queryClient.invalidateQueries({ queryKey: queryKeys.tenants.all() });
    },
  });
}
