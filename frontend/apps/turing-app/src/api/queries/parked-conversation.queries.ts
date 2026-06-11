import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  TurParkedConversation,
  TurParkedConversationUnblockResponse,
} from '@/models/parked-conversation/parked-conversation.model';
import { TurParkedConversationService } from '@/services/parked-conversation/parked-conversation.service';
import { queryKeys } from './keys';

const service = new TurParkedConversationService();

/**
 * Every conversation currently suspended on a chat-flow {@code suspend} node.
 * Polls every 15s so the dashboard reflects new suspensions / external
 * resumes without a manual refresh.
 */
export function useParkedConversations(options: { refetchInterval?: number } = {}) {
  const { refetchInterval = 15000 } = options;
  return useQuery<TurParkedConversation[]>({
    queryKey: queryKeys.parkedConversations.list(),
    queryFn: () => service.query(),
    refetchInterval,
  });
}

/** Admin unblock — resumes the conversation, then refreshes the list. */
export function useResumeParkedConversation() {
  const queryClient = useQueryClient();
  return useMutation<TurParkedConversationUnblockResponse, Error, string>({
    mutationFn: (conversationId) => service.resume(conversationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.parkedConversations.all() });
    },
  });
}
