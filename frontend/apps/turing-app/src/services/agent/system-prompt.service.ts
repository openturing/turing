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
  async preview(
    agentId: string,
    flowId?: string,
    nodeId?: string,
    vars?: string,
  ): Promise<TurSystemPromptPreview> {
    const params: Record<string, string> = {};
    if (flowId) params.flowId = flowId;
    if (nodeId) params.nodeId = nodeId;
    if (vars) params.vars = vars;
    const response = await axios.get<TurSystemPromptPreview>(
      `/ai-agent/${agentId}/system-prompt/preview`,
      { params },
    );
    return response.data;
  }

  /**
   * T612/T618 — replay the assembled prompt from a real conversation. Without
   * `turnIndex` (T612) the backend rebuilds the system message from the
   * conversation's current persisted state through the same runtime assembler.
   * With `turnIndex` (T618) it loads that captured past turn verbatim. Either
   * way `preview.replay` carries the tool-call trace and the available captured
   * turns for the picker.
   */
  async previewReplay(
    agentId: string,
    conversationId: string,
    turnIndex?: number,
  ): Promise<TurSystemPromptPreview> {
    const params: Record<string, string | number> = { conversationId };
    if (turnIndex != null) params.turnIndex = turnIndex;
    const response = await axios.get<TurSystemPromptPreview>(
      `/ai-agent/${agentId}/system-prompt/preview/replay`,
      { params },
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
