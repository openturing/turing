import axios from "axios"

/**
 * Slots captured during a chat session, returned as a single flat map keyed
 * by slot name. Mirrors {@code TurChatSessionSlotsDto} on the backend.
 * Slots are conversation-scoped — the API merges values across every flow
 * (active or completed) and across the whole AI agent, so callers don't
 * have to walk a per-flow tree.
 *
 * @since 2026.2.7
 */
export interface TurChatSessionSlots {
  conversationId: string
  slots: Record<string, string>
}

/**
 * Client for `/api/chat/sessions/{conversationId}/slots` — every slot value
 * captured during the conversation, flattened across flows. Used by the
 * chat-page Session Info sheet to surface slot fills in real time.
 *
 * @since 2026.2.7
 */
export class TurChatSessionSlotsService {
  async query(conversationId: string): Promise<TurChatSessionSlots> {
    const response = await axios.get<TurChatSessionSlots>(
      `/chat/sessions/${conversationId}/slots`,
    )
    return response.data
  }
}
