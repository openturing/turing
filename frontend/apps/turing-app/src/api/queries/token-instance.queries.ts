import { useQuery } from '@tanstack/react-query';
import type { TurTokenInstance } from '@/models/token/token-instance.model.ts';
import { TurTokenInstanceService } from '@/services/token/token.service';
import { queryKeys } from './keys';

const service = new TurTokenInstanceService();

export function useTokenInstances() {
  return useQuery<TurTokenInstance[]>({
    queryKey: queryKeys.tokenInstances.list(),
    queryFn: () => service.query(),
  });
}
