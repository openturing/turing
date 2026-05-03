import { useQuery } from '@tanstack/react-query';
import type { TurSEInstance } from '@/models/se/se-instance.model.ts';
import { TurSEInstanceService } from '@/services/se/se.service';
import { queryKeys } from './keys';

const service = new TurSEInstanceService();

export function useSeInstances() {
  return useQuery<TurSEInstance[]>({
    queryKey: queryKeys.seInstances.list(),
    queryFn: () => service.query(),
  });
}
