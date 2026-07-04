import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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

export function useSeInstance(id: string | undefined) {
  return useQuery<TurSEInstance>({
    queryKey: id ? queryKeys.seInstances.detail(id) : ['se-instances', 'detail', 'pending'],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id),
  });
}

function invalidateSeInstances(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.seInstances.all() });
}

export function useCreateSeInstance() {
  const queryClient = useQueryClient();
  return useMutation<TurSEInstance, Error, TurSEInstance>({
    mutationFn: (instance) => service.create(instance),
    onSuccess: () => invalidateSeInstances(queryClient),
  });
}

export function useUpdateSeInstance() {
  const queryClient = useQueryClient();
  return useMutation<TurSEInstance, Error, TurSEInstance>({
    mutationFn: (instance) => service.update(instance),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.seInstances.detail(saved.id), saved);
      }
      invalidateSeInstances(queryClient);
    },
  });
}

export function useDeleteSeInstance() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurSEInstance>({
    mutationFn: (instance) => service.delete(instance),
    onSuccess: (_ok, instance) => {
      if (instance?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.seInstances.detail(instance.id) });
      }
      invalidateSeInstances(queryClient);
    },
  });
}
