import { useQuery } from '@tanstack/react-query';
import type { TurSystemInfo } from '@/models/system/system-info.model';
import { TurSystemInfoService } from '@/services/system/system-info.service';
import { queryKeys } from './keys';

const service = new TurSystemInfoService();

/**
 * T1018 — the running JVM's memory, disk and database reading.
 *
 * Read by the dashboard's health widget and by the System Info page, which each
 * held their own `useState` + `useEffect` copy of it before this hook existed.
 */
export function useSystemInfo(options?: { refetchInterval?: number }) {
  return useQuery<TurSystemInfo>({
    queryKey: queryKeys.systemInfo.info(),
    queryFn: () => service.getInfo(),
    // The dashboard widget polls; the System Info page does not. Passing the
    // interval in keeps one cache entry either way, so an open dashboard also
    // keeps the page fresh.
    refetchInterval: options?.refetchInterval,
  });
}

/** The server's resolved environment variables, as System Info renders them. */
export function useSystemVariables() {
  return useQuery<Record<string, string>>({
    queryKey: queryKeys.systemInfo.variables(),
    queryFn: () => service.getVariables(),
  });
}
