import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurChatFlow } from '@/models/agent/chat-flow.model';
import { TurChatFlowService, type TurChatFlowImportPayload } from '@/services/agent/chat-flow.service';
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

type ChatFlowImportVars = { agentId: string; payload: TurChatFlowImportPayload };

/**
 * Backend-side chat-flow import. The backend creates any embedded personas
 * the agent does not yet have, attaches them to the agent, rewrites
 * personaId references in the graph, and saves the flow — all in one
 * round-trip. The mutation invalidates personas + agent caches so the
 * editor's persona dropdown picks up the new voices on the next render.
 */
export function useImportChatFlow() {
  const queryClient = useQueryClient();
  return useMutation<TurChatFlow, Error, ChatFlowImportVars>({
    mutationFn: ({ agentId, payload }) => service.import(agentId, payload),
    onSuccess: () => {
      invalidateChatFlows(queryClient);
      queryClient.invalidateQueries({ queryKey: queryKeys.personas.all() });
      queryClient.invalidateQueries({ queryKey: queryKeys.aiAgents.all() });
    },
  });
}

type ChatFlowImportBundleVars = { agentId: string; bundle: TurChatFlowImportPayload[] };

/**
 * Bundle import: same as {@link useImportChatFlow} but accepts a list of flows that the backend
 * persists in one transaction, auto-wiring sub-flow references across items in the bundle.
 */
export function useImportChatFlowBundle() {
  const queryClient = useQueryClient();
  return useMutation<TurChatFlow[], Error, ChatFlowImportBundleVars>({
    mutationFn: ({ agentId, bundle }) => service.importBundle(agentId, bundle),
    onSuccess: () => {
      invalidateChatFlows(queryClient);
      queryClient.invalidateQueries({ queryKey: queryKeys.personas.all() });
      queryClient.invalidateQueries({ queryKey: queryKeys.aiAgents.all() });
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
