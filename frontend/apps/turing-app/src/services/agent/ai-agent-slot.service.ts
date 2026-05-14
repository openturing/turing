import axios from "axios";
import type { TurAIAgentSlot } from "@/models/agent/ai-agent-slot.model";

/**
 * Agent-scoped slots client. Every endpoint requires the parent agent id;
 * slots can never be addressed without their owning agent.
 *
 * @since 2026.2.7
 */
export class TurAIAgentSlotService {
  async query(agentId: string): Promise<TurAIAgentSlot[]> {
    const response = await axios.get<TurAIAgentSlot[]>(`/ai-agent/${agentId}/slot`);
    return response.data;
  }
  async get(agentId: string, id: string): Promise<TurAIAgentSlot> {
    const response = await axios.get<TurAIAgentSlot>(`/ai-agent/${agentId}/slot/${id}`);
    return response.data;
  }
  async create(agentId: string, slot: TurAIAgentSlot): Promise<TurAIAgentSlot> {
    const response = await axios.post<TurAIAgentSlot>(`/ai-agent/${agentId}/slot`, slot);
    return response.data;
  }
  async update(agentId: string, slot: TurAIAgentSlot): Promise<TurAIAgentSlot> {
    const response = await axios.put<TurAIAgentSlot>(
      `/ai-agent/${agentId}/slot/${slot.id}`,
      slot,
    );
    return response.data;
  }
  async delete(agentId: string, slot: TurAIAgentSlot): Promise<boolean> {
    const response = await axios.delete<TurAIAgentSlot>(`/ai-agent/${agentId}/slot/${slot.id}`);
    return response.status === 200;
  }
}
