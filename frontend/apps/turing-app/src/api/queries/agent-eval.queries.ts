import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  TurAgentEvalGate,
  TurAgentEvalReport,
  TurAgentEvalSet,
} from "@/models/agent/agent-eval.model";
import { TurAgentEvalService } from "@/services/agent/agent-eval.service";
import { queryKeys } from "./keys";

/**
 * T285–T288 / §XV — React Query hooks for the Agent-CI eval gate.
 *
 * @since 2026.3.1
 */
const service = new TurAgentEvalService();

/** Golden sets for an agent. Disabled until the id is known. */
export function useAgentEvalSets(agentId: string | undefined) {
  return useQuery<TurAgentEvalSet[]>({
    queryKey: agentId ? queryKeys.agentEval.sets(agentId) : ["agent-eval", "sets", "pending"],
    queryFn: () => service.listSets(agentId as string),
    enabled: Boolean(agentId),
  });
}

/** Pre-publish gate status — drives the flow editor's eval-gate panel. */
export function useAgentEvalGate(agentId: string | undefined) {
  return useQuery<TurAgentEvalGate>({
    queryKey: agentId ? queryKeys.agentEval.gate(agentId) : ["agent-eval", "gate", "pending"],
    queryFn: () => service.gate(agentId as string),
    enabled: Boolean(agentId),
  });
}

function invalidateAgentEval(
  queryClient: ReturnType<typeof useQueryClient>,
  agentId: string,
) {
  queryClient.invalidateQueries({ queryKey: queryKeys.agentEval.gate(agentId) });
  queryClient.invalidateQueries({ queryKey: queryKeys.agentEval.report(agentId) });
}

/** Run the gate; refreshes the gate + report caches on success. */
export function useRunAgentEval() {
  const queryClient = useQueryClient();
  return useMutation<TurAgentEvalReport, Error, { agentId: string }>({
    mutationFn: ({ agentId }) => service.run(agentId),
    onSuccess: (_report, { agentId }) => invalidateAgentEval(queryClient, agentId),
  });
}

type SetMutationVars = { agentId: string; set: TurAgentEvalSet };

export function useSaveAgentEvalSet() {
  const queryClient = useQueryClient();
  return useMutation<TurAgentEvalSet, Error, SetMutationVars>({
    mutationFn: ({ agentId, set }) =>
      set.id ? service.updateSet(agentId, set.id, set) : service.createSet(agentId, set),
    onSuccess: (_set, { agentId }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.agentEval.sets(agentId) });
      invalidateAgentEval(queryClient, agentId);
    },
  });
}

export function useDeleteAgentEvalSet() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, { agentId: string; id: string }>({
    mutationFn: ({ agentId, id }) => service.deleteSet(agentId, id),
    onSuccess: (_ok, { agentId }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.agentEval.sets(agentId) });
      invalidateAgentEval(queryClient, agentId);
    },
  });
}
