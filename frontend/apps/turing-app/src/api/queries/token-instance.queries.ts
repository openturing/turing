import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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

function invalidateTokenInstances(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.tokenInstances.all() });
}

export function useCreateTokenInstance() {
  const queryClient = useQueryClient();
  return useMutation<TurTokenInstance, Error, TurTokenInstance>({
    mutationFn: (token) => service.create(token),
    onSuccess: () => invalidateTokenInstances(queryClient),
  });
}

export function useUpdateTokenInstance() {
  const queryClient = useQueryClient();
  return useMutation<TurTokenInstance, Error, TurTokenInstance>({
    mutationFn: (token) => service.update(token),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.tokenInstances.detail(saved.id), saved);
      }
      invalidateTokenInstances(queryClient);
    },
  });
}

export function useDeleteTokenInstance() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurTokenInstance>({
    mutationFn: (token) => service.delete(token),
    onSuccess: (_ok, token) => {
      if (token?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.tokenInstances.detail(token.id) });
      }
      invalidateTokenInstances(queryClient);
    },
  });
}
