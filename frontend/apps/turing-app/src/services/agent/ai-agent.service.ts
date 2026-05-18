import axios from "axios";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";

export class TurAIAgentService {
  async query(): Promise<TurAIAgent[]> {
    const response = await axios.get<TurAIAgent[]>("/ai-agent");
    return response.data;
  }
  async get(id: string): Promise<TurAIAgent> {
    const response = await axios.get<TurAIAgent>(`/ai-agent/${id}`);
    return response.data;
  }
  async create(turAIAgent: TurAIAgent): Promise<TurAIAgent> {
    const response = await axios.post<TurAIAgent>("/ai-agent", turAIAgent);
    return response.data;
  }
  async update(turAIAgent: TurAIAgent): Promise<TurAIAgent> {
    const response = await axios.put<TurAIAgent>(
      `/ai-agent/${turAIAgent.id.toString()}`,
      turAIAgent
    );
    return response.data;
  }
  async delete(turAIAgent: TurAIAgent): Promise<boolean> {
    const response = await axios.delete<TurAIAgent>(
      `/ai-agent/${turAIAgent.id.toString()}`
    );
    return response.status == 200;
  }

  /**
   * Downloads the agent as a ZIP bundle:
   * - `export.json` — TurExchange envelope (same shape as SN site export, so a
   *   future combined ZIP can carry sites + agents together).
   * - `chat-flows/{slug}.chat-flow.json` — one per chat flow.
   * - `tools/{slug}.groovy` — one per custom tool.
   * API keys / credentials are excluded by design; the operator must re-enter
   * them after import.
   *
   * @since 2026.2.8
   */
  async export(id: string): Promise<Blob | null> {
    const response = await axios
      .get<Blob>(`/ai-agent/${id}/export`, { responseType: "blob" })
      .then((res) => res.data)
      .catch((error) => {
        console.error("Failed to export AI Agent", error);
        return null;
      });
    return response;
  }
}
