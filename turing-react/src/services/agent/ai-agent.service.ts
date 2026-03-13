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
}
