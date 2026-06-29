import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurSNSite } from '@/models/sn/sn-site.model';
import type { TurSNSiteListItem } from '@/models/sn/sn-site-list-item.model.ts';
import type { TurSNFieldCoverageReport } from '@/models/sn/sn-field-coverage.model.ts';
import type { TurSNContentFitReport } from '@/models/sn/sn-content-fit.model.ts';
import { TurSNSiteService } from '@/services/sn/sn.service';
import { queryKeys } from './keys';

const service = new TurSNSiteService();

/** T388 — per-field coverage / completeness report for an SN site. */
export function useSnSiteFieldCoverage(siteId: string | undefined) {
  return useQuery<TurSNFieldCoverageReport>({
    queryKey: siteId
      ? queryKeys.snSiteFieldCoverage.bySite(siteId)
      : ['sn-site-field-coverage', 'pending'],
    queryFn: () => service.getFieldCoverage(siteId as string),
    enabled: Boolean(siteId),
  });
}

/** T472 — index-time audience content-fit coverage report for an SN site. */
export function useSnSiteContentFit(siteId: string | undefined) {
  return useQuery<TurSNContentFitReport>({
    queryKey: siteId
      ? queryKeys.snSiteContentFit.bySite(siteId)
      : ['sn-site-content-fit', 'pending'],
    queryFn: () => service.getContentFit(siteId as string),
    enabled: Boolean(siteId),
  });
}

/** Cached list of SN sites — list page uses this; cards on dashboard reuse the same cache. */
export function useSnSites() {
  return useQuery<TurSNSiteListItem[]>({
    queryKey: queryKeys.snSites.list(),
    queryFn: () => service.query(),
  });
}

/** Cached single SN site. Used by the field-derivation wizard to resolve the site name + SE instance. */
export function useSnSite(id: string | undefined) {
  return useQuery<TurSNSite>({
    queryKey: id ? queryKeys.snSites.detail(id) : ['sn-sites', 'pending'],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id),
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
