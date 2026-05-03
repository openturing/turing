import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurIntent } from '@/models/intent/intent.model';
import { TurIntentService } from '@/services/intent/intent.service';
import { queryKeys } from './keys';

const service = new TurIntentService();

/** Intents scoped to a specific AI agent. Disabled when agentId is missing. */
export function useIntents(agentId: string | undefined) {
  return useQuery<TurIntent[]>({
    queryKey: agentId ? queryKeys.intents.listByAgent(agentId) : ['intents', 'list', 'pending'],
    queryFn: () => service.query(agentId as string),
    enabled: Boolean(agentId),
  });
}

function invalidateIntents(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.intents.all() });
}

/**
 * Mutations take {@code (agentId, intent)} as a single object so callers don't
 * have to bind the agent at hook-construction time — convenient when the agent
 * id only becomes available after a router parameter resolves.
 */
type IntentMutationVars = { agentId: string; intent: TurIntent };

export function useCreateIntent() {
  const queryClient = useQueryClient();
  return useMutation<TurIntent, Error, IntentMutationVars>({
    mutationFn: ({ agentId, intent }) => service.create(agentId, intent),
    onSuccess: () => invalidateIntents(queryClient),
  });
}

export function useUpdateIntent() {
  const queryClient = useQueryClient();
  return useMutation<TurIntent, Error, IntentMutationVars>({
    mutationFn: ({ agentId, intent }) => service.update(agentId, intent),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.intents.detail(saved.id), saved);
      }
      invalidateIntents(queryClient);
    },
  });
}

export function useDeleteIntent() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, IntentMutationVars>({
    mutationFn: ({ agentId, intent }) => service.delete(agentId, intent),
    onSuccess: (_ok, { intent }) => {
      if (intent?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.intents.detail(intent.id) });
      }
      invalidateIntents(queryClient);
    },
  });
}
