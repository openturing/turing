import { useQuery } from '@tanstack/react-query';
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
