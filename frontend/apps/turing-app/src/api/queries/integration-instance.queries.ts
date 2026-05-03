import { useQuery } from '@tanstack/react-query';
import type { TurIntegrationInstance } from '@/models/integration/integration-instance.model.ts';
import { TurIntegrationInstanceService } from '@/services/integration/integration.service';
import { queryKeys } from './keys';

const service = new TurIntegrationInstanceService();

export function useIntegrationInstances() {
  return useQuery<TurIntegrationInstance[]>({
    queryKey: queryKeys.integrationInstances.list(),
    queryFn: () => service.query(),
  });
}
