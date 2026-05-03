import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurSNSite } from '@/models/sn/sn-site.model';
import type { TurSNSiteListItem } from '@/models/sn/sn-site-list-item.model.ts';
import { TurSNSiteService } from '@/services/sn/sn.service';
import { queryKeys } from './keys';

const service = new TurSNSiteService();

/** Cached list of SN sites — list page uses this; cards on dashboard reuse the same cache. */
export function useSnSites() {
  return useQuery<TurSNSiteListItem[]>({
    queryKey: queryKeys.snSites.list(),
    queryFn: () => service.query(),
  });
}

function invalidateSnSites(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.snSites.all() });
}

export function useCreateSnSite() {
  const queryClient = useQueryClient();
  return useMutation<TurSNSite, Error, TurSNSite>({
    mutationFn: (site) => service.create(site),
    onSuccess: () => invalidateSnSites(queryClient),
  });
}

export function useUpdateSnSite() {
  const queryClient = useQueryClient();
  return useMutation<TurSNSite, Error, TurSNSite>({
    mutationFn: (site) => service.update(site),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.snSites.detail(saved.id), saved);
      }
      invalidateSnSites(queryClient);
    },
  });
}

export function useDeleteSnSite() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurSNSite>({
    mutationFn: (site) => service.delete(site),
    onSuccess: (_ok, site) => {
      if (site?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.snSites.detail(site.id) });
      }
      invalidateSnSites(queryClient);
    },
  });
}
