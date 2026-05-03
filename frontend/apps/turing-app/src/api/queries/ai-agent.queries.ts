import { useQuery } from '@tanstack/react-query';
import type { TurAIAgent } from '@/models/agent/ai-agent.model.ts';
import { TurAIAgentService } from '@/services/agent/ai-agent.service';
import { queryKeys } from './keys';

const service = new TurAIAgentService();

export function useAiAgents() {
  return useQuery<TurAIAgent[]>({
    queryKey: queryKeys.aiAgents.list(),
    queryFn: () => service.query(),
  });
}
