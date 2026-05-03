import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurSECoreInfo } from '@/models/se/se-core-info.model.ts';
import { TurSEInstanceService } from '@/services/se/se.service';
import { queryKeys } from './keys';

const service = new TurSEInstanceService();

/** Cores under a search-engine instance. Disabled when seId is missing. */
export function useSeCores(seId: string | undefined) {
  return useQuery<TurSECoreInfo[]>({
    queryKey: seId ? queryKeys.seCores.listByInstance(seId) : ['se-cores', 'pending'],
    queryFn: () => service.getCores(seId as string),
    enabled: Boolean(seId),
  });
}

function invalidateSeCores(queryClient: ReturnType<typeof useQueryClient>, seId: string) {
  queryClient.invalidateQueries({ queryKey: queryKeys.seCores.listByInstance(seId) });
}

type CreateCoreVars = { seId: string; name: string; locale: string };
type CoreOpVars = { seId: string; coreName: string };

export function useCreateSeCore() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, CreateCoreVars>({
    mutationFn: ({ seId, name, locale }) => service.createCore(seId, name, locale),
    onSuccess: (_void, { seId }) => invalidateSeCores(queryClient, seId),
  });
}

export function useDeleteSeCore() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, CoreOpVars>({
    mutationFn: ({ seId, coreName }) => service.deleteCore(seId, coreName),
    onSuccess: (_void, { seId }) => invalidateSeCores(queryClient, seId),
  });
}

/** Removes all documents from a core but keeps the core itself. */
export function useClearSeCore() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, CoreOpVars>({
    mutationFn: ({ seId, coreName }) => service.clearCore(seId, coreName),
    onSuccess: (_void, { seId }) => invalidateSeCores(queryClient, seId),
  });
}
