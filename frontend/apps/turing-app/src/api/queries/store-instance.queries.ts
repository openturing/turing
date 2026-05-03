import { useQuery } from '@tanstack/react-query';
import type { TurStoreInstance } from '@/models/store/store-instance.model.ts';
import { TurStoreInstanceService } from '@/services/store/store.service';
import { queryKeys } from './keys';

const service = new TurStoreInstanceService();

export function useStoreInstances() {
  return useQuery<TurStoreInstance[]>({
    queryKey: queryKeys.storeInstances.list(),
    queryFn: () => service.query(),
  });
}
