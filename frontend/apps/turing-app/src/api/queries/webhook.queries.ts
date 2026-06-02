import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurChatWebhook } from '@/models/genai/webhook.model';
import { TurChatWebhookService } from '@/services/genai/webhook.service';
import { queryKeys } from './keys';

/**
 * React Query hooks for the deployment-wide webhook catalog (T62).
 *
 * @since 2026.3.1
 */
const service = new TurChatWebhookService();

export function useChatWebhooks() {
  return useQuery<TurChatWebhook[]>({
    queryKey: queryKeys.chatWebhooks.list(),
    queryFn: () => service.query(),
  });
}

export function useChatWebhook(id: string | undefined) {
  return useQuery<TurChatWebhook>({
    queryKey: id ? queryKeys.chatWebhooks.detail(id) : ['chat-webhooks', 'detail', 'pending'],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id),
  });
}

function invalidate(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.chatWebhooks.all() });
}

export function useCreateChatWebhook() {
  const queryClient = useQueryClient();
  return useMutation<TurChatWebhook, Error, TurChatWebhook>({
    mutationFn: (webhook) => service.create(webhook),
    onSuccess: () => invalidate(queryClient),
  });
}

export function useUpdateChatWebhook() {
  const queryClient = useQueryClient();
  return useMutation<TurChatWebhook, Error, TurChatWebhook>({
    mutationFn: (webhook) => service.update(webhook),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.chatWebhooks.detail(saved.id), saved);
      }
      invalidate(queryClient);
    },
  });
}

export function useDeleteChatWebhook() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurChatWebhook>({
    mutationFn: (webhook) => service.delete(webhook),
    onSuccess: (_ok, webhook) => {
      if (webhook?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.chatWebhooks.detail(webhook.id) });
      }
      invalidate(queryClient);
    },
  });
}
