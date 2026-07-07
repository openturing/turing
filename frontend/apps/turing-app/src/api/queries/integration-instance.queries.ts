import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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

export function useIntegrationInstance(id: string | undefined) {
  return useQuery<TurIntegrationInstance>({
    queryKey: id ? queryKeys.integrationInstances.detail(id) : ['integration-instances', 'detail', 'pending'],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id),
  });
}

function invalidateIntegrationInstances(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.integrationInstances.all() });
}

export function useCreateIntegrationInstance() {
  const queryClient = useQueryClient();
  return useMutation<TurIntegrationInstance, Error, TurIntegrationInstance>({
    mutationFn: (instance) => service.create(instance),
    onSuccess: () => invalidateIntegrationInstances(queryClient),
  });
}

export function useUpdateIntegrationInstance() {
  const queryClient = useQueryClient();
  return useMutation<TurIntegrationInstance, Error, TurIntegrationInstance>({
    mutationFn: (instance) => service.update(instance),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.integrationInstances.detail(saved.id), saved);
      }
      invalidateIntegrationInstances(queryClient);
    },
  });
}

export function useDeleteIntegrationInstance() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurIntegrationInstance>({
    mutationFn: (instance) => service.delete(instance),
    onSuccess: (_ok, instance) => {
      if (instance?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.integrationInstances.detail(instance.id) });
      }
      invalidateIntegrationInstances(queryClient);
    },
  });
}
