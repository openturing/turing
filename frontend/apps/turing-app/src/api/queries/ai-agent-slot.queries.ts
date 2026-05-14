import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurAIAgentSlot } from '@/models/agent/ai-agent-slot.model';
import { TurAIAgentSlotService } from '@/services/agent/ai-agent-slot.service';
import { queryKeys } from './keys';

const service = new TurAIAgentSlotService();

/** Slots declared on a specific AI agent. Disabled when agentId is missing. */
export function useAIAgentSlots(agentId: string | undefined) {
  return useQuery<TurAIAgentSlot[]>({
    queryKey: agentId
      ? queryKeys.aiAgentSlots.listByAgent(agentId)
      : ['ai-agent-slots', 'list', 'pending'],
    queryFn: () => service.query(agentId as string),
    enabled: Boolean(agentId),
  });
}

function invalidate(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.aiAgentSlots.all() });
}

type SlotMutationVars = { agentId: string; slot: TurAIAgentSlot };

export function useCreateAIAgentSlot() {
  const queryClient = useQueryClient();
  return useMutation<TurAIAgentSlot, Error, SlotMutationVars>({
    mutationFn: ({ agentId, slot }) => service.create(agentId, slot),
    onSuccess: () => invalidate(queryClient),
  });
}

export function useUpdateAIAgentSlot() {
  const queryClient = useQueryClient();
  return useMutation<TurAIAgentSlot, Error, SlotMutationVars>({
    mutationFn: ({ agentId, slot }) => service.update(agentId, slot),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.aiAgentSlots.detail(saved.id), saved);
      }
      invalidate(queryClient);
    },
  });
}

export function useDeleteAIAgentSlot() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, SlotMutationVars>({
    mutationFn: ({ agentId, slot }) => service.delete(agentId, slot),
    onSuccess: (_ok, { slot }) => {
      if (slot?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.aiAgentSlots.detail(slot.id) });
      }
      invalidate(queryClient);
    },
  });
}
