import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  TurAnalyticsIntent,
  ReindexAnalyticsIntentResponse,
} from '@/models/analytics-intent/analytics-intent.model';
import { TurAnalyticsIntentService } from '@/services/analytics-intent/analytics-intent.service';
import { queryKeys } from './keys';

const service = new TurAnalyticsIntentService();

/** Analytics intent catalog scoped to a specific AI agent. */
export function useAnalyticsIntents(agentId: string | undefined) {
  return useQuery<TurAnalyticsIntent[]>({
    queryKey: agentId
      ? queryKeys.analyticsIntents.listByAgent(agentId)
      : ['analytics-intents', 'list', 'pending'],
    queryFn: () => service.query(agentId as string),
    enabled: Boolean(agentId),
  });
}

function invalidateAnalyticsIntents(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.analyticsIntents.all() });
}

type AnalyticsIntentMutationVars = { agentId: string; intent: TurAnalyticsIntent };

export function useCreateAnalyticsIntent() {
  const queryClient = useQueryClient();
  return useMutation<TurAnalyticsIntent, Error, AnalyticsIntentMutationVars>({
    mutationFn: ({ agentId, intent }) => service.create(agentId, intent),
    onSuccess: () => invalidateAnalyticsIntents(queryClient),
  });
}

export function useUpdateAnalyticsIntent() {
  const queryClient = useQueryClient();
  return useMutation<TurAnalyticsIntent, Error, AnalyticsIntentMutationVars>({
    mutationFn: ({ agentId, intent }) => service.update(agentId, intent),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.analyticsIntents.detail(saved.id), saved);
      }
      invalidateAnalyticsIntents(queryClient);
    },
  });
}

export function useDeleteAnalyticsIntent() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, AnalyticsIntentMutationVars>({
    mutationFn: ({ agentId, intent }) => service.delete(agentId, intent),
    onSuccess: (_ok, { intent }) => {
      if (intent?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.analyticsIntents.detail(intent.id) });
      }
      invalidateAnalyticsIntents(queryClient);
    },
  });
}

/**
 * Bootstrap path that wipes the agent's slice in the SE-side index and
 * re-pushes every enabled catalog row. Returns the count of rows the
 * SE accepted (0 when no SE is bound).
 */
export function useReindexAnalyticsIntents() {
  return useMutation<ReindexAnalyticsIntentResponse, Error, string>({
    mutationFn: (agentId) => service.reindex(agentId),
  });
}
