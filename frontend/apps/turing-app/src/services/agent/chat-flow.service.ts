import axios from "axios";
import type { ChatFlowGeneration, TurChatFlow } from "@/models/agent/chat-flow.model";
import type { TurChatFlowSubmission } from "@/models/agent/chat-flow-submission.model";
import type { AiAuthoringRequest, AiAuthoringResponse } from "@/models/ai-authoring/ai-authoring.model";

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

  /**
   * Submissions: every flow run that reached an end node, newest first.
   * Used by the AI Agent History page.
   */
  async submissions(agentId: string, flowId: string): Promise<TurChatFlowSubmission[]> {
    const response = await axios.get<TurChatFlowSubmission[]>(
      `/ai-agent/${agentId}/chat-flow/${flowId}/submissions`,
    );
    return response.data;
  }

  /**
   * AI Authoring chat — first turn invokes the planning skill on the
   * backend, subsequent turns do incremental edits. Always returns a
   * conversational reply + the FULL updated state.
   */
  async aiChat(
    agentId: string,
    request: AiAuthoringRequest<ChatFlowGeneration>,
  ): Promise<AiAuthoringResponse<ChatFlowGeneration>> {
    const response = await axios.post<AiAuthoringResponse<ChatFlowGeneration>>(
      `/ai-agent/${agentId}/chat-flow/chat`,
      request,
    );
    return response.data;
  }
}
