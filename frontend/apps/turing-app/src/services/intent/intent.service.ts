import axios from "axios";
import type { TurIntent } from "@/models/intent/intent.model";

/**
 * Agent-scoped intent service. Every endpoint is nested under
 * `/ai-agent/{agentId}/intent` — there is no longer a global intent listing.
 */
export class TurIntentService {
  async query(agentId: string): Promise<TurIntent[]> {
    const response = await axios.get<TurIntent[]>(`/ai-agent/${agentId}/intent`);
    return response.data;
  }
  async queryEnabled(agentId: string): Promise<TurIntent[]> {
    const response = await axios.get<TurIntent[]>(`/ai-agent/${agentId}/intent/enabled`);
    return response.data;
  }
  async get(agentId: string, id: string): Promise<TurIntent> {
    const response = await axios.get<TurIntent>(`/ai-agent/${agentId}/intent/${id}`);
    return response.data;
  }
  async create(agentId: string, intent: TurIntent): Promise<TurIntent> {
    const response = await axios.post<TurIntent>(`/ai-agent/${agentId}/intent`, intent);
    return response.data;
  }
  async update(agentId: string, intent: TurIntent): Promise<TurIntent> {
    const response = await axios.put<TurIntent>(
      `/ai-agent/${agentId}/intent/${intent.id}`,
      intent
    );
    return response.data;
  }
  async delete(agentId: string, intent: TurIntent): Promise<boolean> {
    const response = await axios.delete(`/ai-agent/${agentId}/intent/${intent.id}`);
    return response.status === 200;
  }
}
