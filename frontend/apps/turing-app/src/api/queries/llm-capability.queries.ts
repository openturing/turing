import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurLLMInstanceCapability } from '@/models/llm/llm-capability.model.ts';
import { TurLLMInstanceCapabilityService } from '@/services/llm/llm-capability.service';
import { queryKeys } from './keys';

const service = new TurLLMInstanceCapabilityService();

export function useLlmInstanceCapabilities(instanceId: string | undefined) {
  return useQuery<TurLLMInstanceCapability[]>({
    queryKey: instanceId
      ? queryKeys.llmInstanceCapabilities.list(instanceId)
      : ['llm-instance-capabilities', 'pending'],
    queryFn: () => service.query(instanceId as string),
    enabled: Boolean(instanceId) && instanceId !== 'new',
  });
}

export function useUpsertLlmInstanceCapability(instanceId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    TurLLMInstanceCapability,
    Error,
    { key: string; enabled: boolean; configJson?: string | null }
  >({
    mutationFn: ({ key, enabled, configJson }) =>
      service.upsert(instanceId, key, enabled, configJson ?? null),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.llmInstanceCapabilities.list(instanceId),
      });
    },
  });
}
