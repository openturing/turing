import axios from "axios";

/**
 * F.10 / §X.11.b — T172. Rewrites a prior assistant answer (shorter / simpler /
 * …). When the agent's LLM is OpenAI-backed and Predicted Outputs (T171) is
 * enabled, the backend sends the previous answer as the `prediction` so the
 * rewrite returns 3–5× faster; otherwise it runs as a normal completion.
 *
 * @since 2026.3.4
 */
export type TurRephraseStyle = "shorter" | "simpler" | "longer" | "formal" | "friendly";

export interface TurChatRephraseRequest {
  conversationId?: string;
  llmInstanceId?: string;
  prompt?: string;
  answer: string;
  style?: TurRephraseStyle | string;
}

export interface TurChatRephraseResult {
  success: boolean;
  error?: string;
  rephrased?: string;
  usedPrediction: boolean;
}

export class TurChatRephraseService {
  /** Rewrite an assistant answer in the requested style. */
  async rephrase(
    agentId: string,
    request: TurChatRephraseRequest,
  ): Promise<TurChatRephraseResult> {
    const response = await axios.post<TurChatRephraseResult>(
      `/ai-agent/${agentId}/chat-rephrase`,
      request,
    );
    return response.data;
  }
}
