import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { TurAgentSuggestion } from "@/models/agent/agent-suggestion.model";
import { TurAgentSuggestionService } from "@/services/agent/agent-suggestion.service";
import { queryKeys } from "./keys";

const service = new TurAgentSuggestionService();

/**
 * T447 / §XXIII.6 — list + approve/reject/delete the self-tuning suggestions for
 * one agent. Approve/reject/delete invalidate the agent's suggestion list.
 */
export function useAgentSuggestions(agentId: string | undefined) {
  return useQuery<TurAgentSuggestion[]>({
    queryKey: agentId
      ? queryKeys.agentSuggestions.list(agentId)
      : ["agent-suggestions", "pending"],
    queryFn: () => service.query(agentId as string),
    enabled: Boolean(agentId),
  });
}

function invalidate(
  queryClient: ReturnType<typeof useQueryClient>,
  agentId: string,
) {
  queryClient.invalidateQueries({
    queryKey: queryKeys.agentSuggestions.list(agentId),
  });
}

export function useApproveAgentSuggestion(agentId: string) {
  const queryClient = useQueryClient();
  return useMutation<TurAgentSuggestion, Error, string>({
    mutationFn: (id) => service.approve(agentId, id),
    onSuccess: () => invalidate(queryClient, agentId),
  });
}

export function useRejectAgentSuggestion(agentId: string) {
  const queryClient = useQueryClient();
  return useMutation<TurAgentSuggestion, Error, string>({
    mutationFn: (id) => service.reject(agentId, id),
    onSuccess: () => invalidate(queryClient, agentId),
  });
}

export function useDeleteAgentSuggestion(agentId: string) {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, string>({
    mutationFn: (id) => service.delete(agentId, id),
    onSuccess: () => invalidate(queryClient, agentId),
  });
}
