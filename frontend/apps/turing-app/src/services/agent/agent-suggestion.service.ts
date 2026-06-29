import axios from "axios";
import type { TurAgentSuggestion } from "@/models/agent/agent-suggestion.model";

/**
 * T447 / §XXIII.6 — admin client for the self-tuning suggestions an agent's
 * loop opened: list, approve (applies the proposed system prompt), reject, delete.
 */
export class TurAgentSuggestionService {
  async query(agentId: string): Promise<TurAgentSuggestion[]> {
    const response = await axios.get<TurAgentSuggestion[]>(
      `/ai-agent/${agentId}/suggestion`,
    );
    return response.data;
  }
  async approve(agentId: string, id: string): Promise<TurAgentSuggestion> {
    const response = await axios.post<TurAgentSuggestion>(
      `/ai-agent/${agentId}/suggestion/${id}/approve`,
    );
    return response.data;
  }
  async reject(agentId: string, id: string): Promise<TurAgentSuggestion> {
    const response = await axios.post<TurAgentSuggestion>(
      `/ai-agent/${agentId}/suggestion/${id}/reject`,
    );
    return response.data;
  }
  async delete(agentId: string, id: string): Promise<boolean> {
    const response = await axios.delete(`/ai-agent/${agentId}/suggestion/${id}`);
    return response.status == 200;
  }
}
