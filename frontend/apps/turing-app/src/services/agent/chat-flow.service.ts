import axios from "axios";
import type { TurChatFlow } from "@/models/agent/chat-flow.model";

/**
 * Agent-scoped chat flow client. Every endpoint requires the parent agent id;
 * flows can never be addressed without their owning agent.
 *
 * @since 2026.2.5
 */
export class TurChatFlowService {
  async query(agentId: string): Promise<TurChatFlow[]> {
    const response = await axios.get<TurChatFlow[]>(`/ai-agent/${agentId}/chat-flow`);
    return response.data;
  }
  async get(agentId: string, id: string): Promise<TurChatFlow> {
    const response = await axios.get<TurChatFlow>(`/ai-agent/${agentId}/chat-flow/${id}`);
    return response.data;
  }
  async create(agentId: string, turChatFlow: TurChatFlow): Promise<TurChatFlow> {
    const response = await axios.post<TurChatFlow>(`/ai-agent/${agentId}/chat-flow`, turChatFlow);
    return response.data;
  }
  async update(agentId: string, turChatFlow: TurChatFlow): Promise<TurChatFlow> {
    const response = await axios.put<TurChatFlow>(`/ai-agent/${agentId}/chat-flow/${turChatFlow.id}`, turChatFlow);
    return response.data;
  }
  async delete(agentId: string, turChatFlow: TurChatFlow): Promise<boolean> {
    const response = await axios.delete<TurChatFlow>(`/ai-agent/${agentId}/chat-flow/${turChatFlow.id}`);
    return response.status == 200;
  }
}
