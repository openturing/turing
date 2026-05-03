import { useQuery } from '@tanstack/react-query';
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
