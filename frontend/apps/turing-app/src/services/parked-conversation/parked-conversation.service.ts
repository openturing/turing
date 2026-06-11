import axios from "axios";
import type {
  TurParkedConversation,
  TurParkedConversationUnblockResponse,
} from "@/models/parked-conversation/parked-conversation.model";

/**
 * T122 / §IX.6.b — admin read + unblock for conversations parked on a chat-flow
 * {@code suspend} node. Paired with the Spring controller
 * {@code TurParkedConversationsAPI} at {@code /api/system/parked-conversations}.
 *
 * @since 2026.3.1
 */
export class TurParkedConversationService {
  async query(): Promise<TurParkedConversation[]> {
    const response = await axios.get<TurParkedConversation[]>("/system/parked-conversations");
    return response.data ?? [];
  }

  async resume(conversationId: string): Promise<TurParkedConversationUnblockResponse> {
    const response = await axios.post<TurParkedConversationUnblockResponse>(
      `/system/parked-conversations/${encodeURIComponent(conversationId)}/resume`,
    );
    return response.data;
  }
}
