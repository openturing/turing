import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  TurChatFlow,
  TurChatFlowFunnelReport,
  TurChatFlowLintIssue,
  TurChatFlowTriggerConflict,
  TurChatFlowVariantRequest,
  TurChatFlowVariantResponse,
} from '@/models/agent/chat-flow.model';
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
  // `chatFlows.all()` covers list, detail, the T91 trigger-conflicts cache
  // AND the T94 lint cache, so a save/delete that changed a trigger
  // description or a graph refreshes every dependent panel on the next
  // render without an explicit second call.
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
      queryClient.invalidateQueries({ queryKey: queryKeys.aiAgentSlots.all() });
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
      queryClient.invalidateQueries({ queryKey: queryKeys.aiAgentSlots.all() });
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

/**
 * T94 — static-analysis warnings for a single chat flow. Renders the editor's
 * sidebar warnings panel. Disabled until both ids are known.
 */
export function useChatFlowLint(agentId: string | undefined, flowId: string | undefined) {
  return useQuery<TurChatFlowLintIssue[]>({
    queryKey: agentId && flowId
      ? queryKeys.chatFlows.lint(agentId, flowId)
      : ['chat-flows', 'lint', 'pending'],
    queryFn: () => service.lint(agentId as string, flowId as string),
    enabled: Boolean(agentId && flowId),
  });
}

/**
 * T85 / §VII.10.b — per-node funnel report. Polled every 30s while the
 * editor is open so an admin tweaking the graph can watch how the
 * cursor counts shift as visitors land on new nodes.
 *
 * @since 2026.3.1
 */
export function useChatFlowFunnel(agentId: string | undefined, flowId: string | undefined) {
  return useQuery<TurChatFlowFunnelReport>({
    queryKey: agentId && flowId
      ? queryKeys.chatFlows.funnel(agentId, flowId)
      : ['chat-flows', 'funnel', 'pending'],
    queryFn: () => service.funnel(agentId as string, flowId as string),
    enabled: Boolean(agentId && flowId),
    refetchInterval: 30_000,
  });
}

type ChatFlowVariantVars = {
  agentId: string;
  flowId: string;
  request: TurChatFlowVariantRequest;
};

/**
 * T97 / §VII.11.g — call the variant-generator endpoint. The mutation does
 * NOT touch the cache: the backend returns an unpersisted candidate, and
 * the dialog hands it to {@link useCreateChatFlow} when (and if) the author
 * accepts the rewrite. Failures surface through {@code mutate}/{@code mutateAsync}
 * with the standard React Query error path.
 */
export function useGenerateChatFlowVariant() {
  return useMutation<TurChatFlowVariantResponse, Error, ChatFlowVariantVars>({
    mutationFn: ({ agentId, flowId, request }) =>
      service.generateVariant(agentId, flowId, request),
  });
}

/**
 * T91 — pairs of flows on this agent whose trigger descriptions overlap
 * enough that the procedural router will silently swing on small scoring
 * deltas. Empty list means no conflict detected.
 */
export function useChatFlowTriggerConflicts(agentId: string | undefined) {
  return useQuery<TurChatFlowTriggerConflict[]>({
    queryKey: agentId
      ? queryKeys.chatFlows.triggerConflicts(agentId)
      : ['chat-flows', 'trigger-conflicts', 'pending'],
    queryFn: () => service.triggerConflicts(agentId as string),
    enabled: Boolean(agentId),
  });
}
