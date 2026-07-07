import axios from "axios";

/**
 * F.9 / §X.10.d — T170. Records operator thumb-up/down feedback on an assistant
 * turn (the DPO preference signal) and exports the agent's DPO dataset.
 *
 * @since 2026.3.4
 */
export type TurChatFeedbackRating = "UP" | "DOWN";

export interface TurChatFeedbackRequest {
  conversationId?: string;
  prompt: string;
  answer: string;
  rating: TurChatFeedbackRating;
}

export interface TurDpoDataset {
  pairCount: number;
  feedbackCount: number;
  jsonl: string;
}

export class TurChatFeedbackService {
  /** Record one thumb-up/down on an assistant turn. */
  async record(
    agentId: string,
    request: TurChatFeedbackRequest,
  ): Promise<{ recorded: boolean; id?: string }> {
    const response = await axios.post(`/ai-agent/${agentId}/chat-feedback`, request);
    return response.data;
  }

  /** Build the agent's incremental DPO dataset from recorded feedback. */
  async dpoDataset(agentId: string): Promise<TurDpoDataset> {
    const response = await axios.get<TurDpoDataset>(`/ai-agent/${agentId}/dpo-dataset`);
    return response.data;
  }
}
