import { useQuery } from "@tanstack/react-query"

import {
  TurChatSessionSlotsService,
  type TurChatSessionSlots,
} from "@/services/chat/chat-session-slots.service"

import { queryKeys } from "./keys"

const service = new TurChatSessionSlotsService()

/**
 * Slots captured during the chat session keyed by {@code conversationId}.
 * The {@code enabled} flag and {@code refetchInterval} are caller-controlled
 * so the Session Info sheet can poll while it's open and stop the moment
 * the user closes it.
 *
 * @since 2026.2.7
 */
export function useChatSessionSlots(
  conversationId: string | null,
  options?: { enabled?: boolean; refetchInterval?: number },
) {
  const enabled = Boolean(conversationId) && (options?.enabled ?? true)
  return useQuery<TurChatSessionSlots>({
    queryKey: conversationId
      ? queryKeys.chatSessions.slots(conversationId)
      : ["chat-sessions", "slots", "pending"],
    queryFn: () => service.query(conversationId as string),
    enabled,
    refetchInterval: enabled ? options?.refetchInterval ?? 3000 : false,
    // Real-time poll: skip the cache's staleTime so each tick hits the network.
    staleTime: 0,
  })
}
