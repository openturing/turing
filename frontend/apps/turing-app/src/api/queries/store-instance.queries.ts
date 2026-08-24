import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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

export function useStoreInstance(id: string | undefined) {
  return useQuery<TurStoreInstance>({
    queryKey: id ? queryKeys.storeInstances.detail(id) : ['store-instances', 'detail', 'pending'],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id),
  });
}

function invalidateStoreInstances(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.storeInstances.all() });
}

/**
 * T1018 — the instance's own system-info reading, straight off the backend it
 * points at. Disabled for the `new` route, which has no instance to ask.
 */
export function useStoreInstanceSystemInfo(id: string | undefined) {
  return useQuery({
    queryKey: id
      ? queryKeys.storeInstances.systemInfo(id)
      : ['storeInstances', 'system-info', 'pending'],
    queryFn: () => service.getSystemInfo(id as string),
    enabled: Boolean(id) && id !== 'new',
  });
}

export function useCreateStoreInstance() {
  const queryClient = useQueryClient();
  return useMutation<TurStoreInstance, Error, TurStoreInstance>({
    mutationFn: (instance) => service.create(instance),
    onSuccess: () => invalidateStoreInstances(queryClient),
  });
}

export function useUpdateStoreInstance() {
  const queryClient = useQueryClient();
  return useMutation<TurStoreInstance, Error, TurStoreInstance>({
    mutationFn: (instance) => service.update(instance),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.storeInstances.detail(saved.id), saved);
      }
      invalidateStoreInstances(queryClient);
    },
  });
}

export function useDeleteStoreInstance() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurStoreInstance>({
    mutationFn: (instance) => service.delete(instance),
    onSuccess: (_ok, instance) => {
      if (instance?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.storeInstances.detail(instance.id) });
      }
      invalidateStoreInstances(queryClient);
    },
  });
}
