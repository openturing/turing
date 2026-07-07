import axios from "axios";
import type {
  TurAgentEvalGate,
  TurAgentEvalReport,
  TurAgentEvalSet,
} from "@/models/agent/agent-eval.model";

/**
 * F.9 / §X.10.c — result of a distill request (T169): the started job, or a
 * reason it didn't start (tier off, non-OpenAI LLM, too few stored completions).
 */
export interface TurAgentDistillResult {
  started: boolean;
  reason?: string;
  jobId?: string;
  fineTuneJobId?: string;
  exampleCount?: number;
  baseModel?: string;
}

/**
 * T285–T288 / §XV — client for the Agent-CI eval endpoints (golden-set CRUD,
 * gate status, run, latest report). F.9 / T169 adds the distill trigger.
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

  /**
   * F.9 / T169 — distill the agent: export its Stored Completions, submit a
   * fine-tuning job, and (once it finishes) swap the model if the eval passes.
   */
  async distill(agentId: string): Promise<TurAgentDistillResult> {
    const response = await axios.post<TurAgentDistillResult>(
      `/ai-agent/${agentId}/eval/distill`,
    );
    return response.data;
  }
}
