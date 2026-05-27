import axios from "axios";
import type {
  TurSystemPromptIssue,
  TurSystemPromptPreview,
} from "@/models/agent/system-prompt.model";

/**
 * Client for the AI Agent system-prompt insight endpoints
 * (`/api/ai-agent/{id}/system-prompt/*`): Live Preview, fast heuristic
 * validation, and the on-demand LLM-powered deep conflict audit.
 *
 * @since 2026.3.1
 */
export class TurSystemPromptService {
  /**
   * Assembled prompt broken into labeled segments + the tool list. `flowId`
   * picks which chat flow governs the previewed turn ("__none__" = no flow,
   * omitted = first enabled flow).
   */
  async preview(agentId: string, flowId?: string): Promise<TurSystemPromptPreview> {
    const response = await axios.get<TurSystemPromptPreview>(
      `/ai-agent/${agentId}/system-prompt/preview`,
      { params: flowId ? { flowId } : {} },
    );
    return response.data;
  }

  /** Fast, deterministic heuristic conflict checks. */
  async validate(agentId: string): Promise<TurSystemPromptIssue[]> {
    const response = await axios.get<TurSystemPromptIssue[]>(
      `/ai-agent/${agentId}/system-prompt/validate`,
    );
    return response.data;
  }

  /** Deep LLM-powered conflict audit (costs a model call). */
  async deepCheck(agentId: string): Promise<TurSystemPromptIssue[]> {
    const response = await axios.post<TurSystemPromptIssue[]>(
      `/ai-agent/${agentId}/system-prompt/validate/deep`,
    );
    return response.data;
  }
}
