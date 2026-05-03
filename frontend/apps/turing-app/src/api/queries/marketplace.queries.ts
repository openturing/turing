import { useQuery } from '@tanstack/react-query';
import type { TurMarketplaceItem } from '@/models/marketplace/marketplace-item.model';
import { TurMarketplaceService } from '@/services/marketplace/marketplace.service';
import { queryKeys } from './keys';

const service = new TurMarketplaceService();

export function useMarketplaceItems() {
  return useQuery<TurMarketplaceItem[]>({
    queryKey: queryKeys.marketplaceItems.list(),
    queryFn: () => service.list(),
  });
}
