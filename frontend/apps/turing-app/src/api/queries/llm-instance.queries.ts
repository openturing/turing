import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  TurLLMInstance,
  TurLlmCatalogChangeNotification,
  TurLlmDeprecatedModelUsage,
} from '@/models/llm/llm-instance.model.ts';
import { TurLLMInstanceService } from '@/services/llm/llm.service';
import { queryKeys } from './keys';

const service = new TurLLMInstanceService();

export function useLlmInstances() {
  return useQuery<TurLLMInstance[]>({
    queryKey: queryKeys.llmInstances.list(),
    queryFn: () => service.query(),
  });
}

/** T782 — instances configured with a catalog-deprecated/retired model. */
export function useLlmDeprecations() {
  return useQuery<TurLlmDeprecatedModelUsage[]>({
    queryKey: queryKeys.llmInstances.deprecations(),
    queryFn: () => service.deprecations(),
  });
}

/** T788 — catalog change-feed notifications for in-use models. */
export function useLlmCatalogChanges() {
  return useQuery<TurLlmCatalogChangeNotification[]>({
    queryKey: queryKeys.llmInstances.changeFeed(),
    queryFn: () => service.changeFeed(),
  });
}

export function useLlmInstance(id: string | undefined) {
  return useQuery<TurLLMInstance>({
    queryKey: id ? queryKeys.llmInstances.detail(id) : ['llm-instances', 'pending'],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id) && id !== 'new',
  });
}

function invalidateLlmInstances(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.llmInstances.all() });
}

export function useCreateLlmInstance() {
  const queryClient = useQueryClient();
  return useMutation<TurLLMInstance, Error, TurLLMInstance>({
    mutationFn: (instance) => service.create(instance),
    onSuccess: () => invalidateLlmInstances(queryClient),
  });
}

export function useUpdateLlmInstance() {
  const queryClient = useQueryClient();
  return useMutation<TurLLMInstance, Error, TurLLMInstance>({
    mutationFn: (instance) => service.update(instance),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.llmInstances.detail(saved.id), saved);
      }
      invalidateLlmInstances(queryClient);
    },
  });
}

export function useDeleteLlmInstance() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurLLMInstance>({
    mutationFn: (instance) => service.delete(instance),
    onSuccess: (_ok, instance) => {
      if (instance?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.llmInstances.detail(instance.id) });
      }
      invalidateLlmInstances(queryClient);
    },
  });
}
