import { useQuery } from '@tanstack/react-query';
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
