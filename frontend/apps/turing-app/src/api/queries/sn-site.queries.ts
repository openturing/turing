import { useQuery } from '@tanstack/react-query';
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
