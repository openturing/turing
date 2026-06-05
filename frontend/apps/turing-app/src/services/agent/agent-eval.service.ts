import axios from "axios";
import type {
  TurAgentEvalGate,
  TurAgentEvalReport,
  TurAgentEvalSet,
} from "@/models/agent/agent-eval.model";

/**
 * T285–T288 / §XV — client for the Agent-CI eval endpoints (golden-set CRUD,
 * gate status, run, latest report).
 *
 * @since 2026.3.1
 */
export class TurAgentEvalService {
  /** CRUD: list the agent's golden sets. */
  async listSets(agentId: string): Promise<TurAgentEvalSet[]> {
    const response = await axios.get<TurAgentEvalSet[]>(
      `/ai-agent/${agentId}/eval-set`,
    );
    return response.data;
  }

  async createSet(agentId: string, set: TurAgentEvalSet): Promise<TurAgentEvalSet> {
    const response = await axios.post<TurAgentEvalSet>(
      `/ai-agent/${agentId}/eval-set`,
      set,
    );
    return response.data;
  }

  async updateSet(
    agentId: string,
    id: string,
    set: TurAgentEvalSet,
  ): Promise<TurAgentEvalSet> {
    const response = await axios.put<TurAgentEvalSet>(
      `/ai-agent/${agentId}/eval-set/${id}`,
      set,
    );
    return response.data;
  }

  async deleteSet(agentId: string, id: string): Promise<boolean> {
    const response = await axios.delete<boolean>(
      `/ai-agent/${agentId}/eval-set/${id}`,
    );
    return response.data;
  }

  /** Pre-publish gate status surfaced in the flow editor Lint panel. */
  async gate(agentId: string): Promise<TurAgentEvalGate> {
    const response = await axios.get<TurAgentEvalGate>(
      `/ai-agent/${agentId}/eval/gate`,
    );
    return response.data;
  }

  /** Run the gate (replays every enabled golden set) and return the report. */
  async run(agentId: string): Promise<TurAgentEvalReport> {
    const response = await axios.post<TurAgentEvalReport>(
      `/ai-agent/${agentId}/eval/run`,
    );
    return response.data;
  }

  /** Whether the gate can run (has an enabled set + a usable LLM). */
  async available(agentId: string): Promise<boolean> {
    const response = await axios.get<boolean>(
      `/ai-agent/${agentId}/eval/available`,
    );
    return response.data;
  }
}
