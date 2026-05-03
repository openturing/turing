import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurEmbeddingModel } from '@/models/embedding/embedding-model.model.ts';
import { TurEmbeddingModelService } from '@/services/embedding/embedding-model.service';
import { queryKeys } from './keys';

const service = new TurEmbeddingModelService();

/**
 * Cached list of embedding model instances. Uses the global default
 * staleTime/gcTime from {@link turingQueryClient}; pass `enabled: false`
 * to defer fetching until a precondition is met.
 */
export function useEmbeddingModels() {
  return useQuery<TurEmbeddingModel[]>({
    queryKey: queryKeys.embeddingModels.list(),
    queryFn: () => service.query(),
  });
}

/**
 * Cached detail fetch for a single embedding model. Disabled when {@code id}
 * is empty so the hook can be called unconditionally from page components.
 */
export function useEmbeddingModel(id: string | undefined) {
  return useQuery<TurEmbeddingModel>({
    queryKey: id ? queryKeys.embeddingModels.detail(id) : ['embedding-models', 'detail', 'pending'],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id),
  });
}

/**
 * Invalidates every cached embedding-model query (list and details). Used by
 * the create/update/delete mutations so the next render of the list page or
 * any open detail view picks up the new state without manual refresh.
 */
function invalidateEmbeddingModels(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.embeddingModels.all() });
}

export function useCreateEmbeddingModel() {
  const queryClient = useQueryClient();
  return useMutation<TurEmbeddingModel, Error, TurEmbeddingModel>({
    mutationFn: (model) => service.create(model),
    onSuccess: () => invalidateEmbeddingModels(queryClient),
  });
}

export function useUpdateEmbeddingModel() {
  const queryClient = useQueryClient();
  return useMutation<TurEmbeddingModel, Error, TurEmbeddingModel>({
    mutationFn: (model) => service.update(model),
    onSuccess: (saved) => {
      // Seed the detail cache so navigating back to the same id is instant.
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.embeddingModels.detail(saved.id), saved);
      }
      invalidateEmbeddingModels(queryClient);
    },
  });
}

export function useDeleteEmbeddingModel() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurEmbeddingModel>({
    mutationFn: (model) => service.delete(model),
    onSuccess: (_ok, model) => {
      // Drop the detail cache for the deleted id outright; otherwise React Query
      // would happily serve stale data if the user navigates back.
      if (model?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.embeddingModels.detail(model.id) });
      }
      invalidateEmbeddingModels(queryClient);
    },
  });
}
