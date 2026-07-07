import axios from "axios";

/**
 * F.7 / §X.8.g (T162) — pre-flight cost quote for a chat turn.
 *
 * Mirrors the backend `TurAgentPreflightCostAPI.PreflightQuote`. When the agent
 * has no per-turn soft cap configured the backend returns `enabled: false` and
 * the UI sends without any confirmation.
 */
export interface TurPreflightQuote {
  enabled: boolean;
  inputTokens: number;
  estimatedOutputTokens: number;
  estimatedCostUsd: number;
  softCapUsd: number | null;
  overSoftCap: boolean;
  exact: boolean;
}

/**
 * Quote the projected cost of the next turn. Best-effort: any error (network,
 * 404, missing price) resolves to a disabled quote so the chat never blocks on
 * the gate.
 */
export async function fetchPreflightCost(
  agentId: string,
  text: string,
): Promise<TurPreflightQuote> {
  try {
    const response = await axios.post<TurPreflightQuote>(
      `/ai-agent/${agentId}/preflight-cost`,
      { text },
    );
    return response.data;
  } catch {
    return {
      enabled: false,
      inputTokens: 0,
      estimatedOutputTokens: 0,
      estimatedCostUsd: 0,
      softCapUsd: null,
      overSoftCap: false,
      exact: false,
    };
  }
}
