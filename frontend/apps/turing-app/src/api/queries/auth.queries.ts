import { useQuery } from '@tanstack/react-query';
import type { TurDiscoveryAPI } from '@/models/auth/discovery';
import { TurAuthorizationService } from '@/services/auth/authorization.service';
import { queryKeys } from './keys';

const service = new TurAuthorizationService();

/**
 * Auth backend discovery — tells the UI whether Keycloak is the active
 * authentication backend or the embedded JPA-backed one. Cached generously
 * because this only changes when the server is reconfigured + restarted.
 */
export function useAuthDiscovery() {
  return useQuery<TurDiscoveryAPI>({
    queryKey: queryKeys.authDiscovery.current(),
    queryFn: () => service.discovery(),
    staleTime: 10 * 60_000,
    retry: 0,
  });
}
