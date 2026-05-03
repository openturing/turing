import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurChatFlow } from '@/models/agent/chat-flow.model';
import { TurChatFlowService } from '@/services/agent/chat-flow.service';
import { queryKeys } from './keys';

const service = new TurChatFlowService();

/** Chat flows scoped to a specific AI agent. Disabled when agentId is missing. */
export function useChatFlows(agentId: string | undefined) {
  return useQuery<TurChatFlow[]>({
    queryKey: agentId ? queryKeys.chatFlows.listByAgent(agentId) : ['chat-flows', 'list', 'pending'],
    queryFn: () => service.query(agentId as string),
    enabled: Boolean(agentId),
  });
}

function invalidateChatFlows(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.chatFlows.all() });
}

type ChatFlowMutationVars = { agentId: string; flow: TurChatFlow };

export function useCreateChatFlow() {
  const queryClient = useQueryClient();
  return useMutation<TurChatFlow, Error, ChatFlowMutationVars>({
    mutationFn: ({ agentId, flow }) => service.create(agentId, flow),
    onSuccess: () => invalidateChatFlows(queryClient),
  });
}

export function useUpdateChatFlow() {
  const queryClient = useQueryClient();
  return useMutation<TurChatFlow, Error, ChatFlowMutationVars>({
    mutationFn: ({ agentId, flow }) => service.update(agentId, flow),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.chatFlows.detail(saved.id), saved);
      }
      invalidateChatFlows(queryClient);
    },
  });
}

export function useDeleteChatFlow() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, ChatFlowMutationVars>({
    mutationFn: ({ agentId, flow }) => service.delete(agentId, flow),
    onSuccess: (_ok, { flow }) => {
      if (flow?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.chatFlows.detail(flow.id) });
      }
      invalidateChatFlows(queryClient);
    },
  });
}
