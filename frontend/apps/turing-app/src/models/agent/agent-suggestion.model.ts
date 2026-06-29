/**
 * T447 / §XXIII.6 — a self-tuning "suggested change" the loop opened for an agent.
 */
export type TurAgentSuggestionStatus = "PENDING" | "APPROVED" | "REJECTED";
export type TurAgentSuggestionKind = "SYSTEM_PROMPT";

export interface TurAgentSuggestion {
  id: string;
  agentId: string;
  kind: TurAgentSuggestionKind;
  currentValue: string | null;
  proposedValue: string | null;
  rationale: string | null;
  baselineScore: number;
  proposedScore: number;
  status: TurAgentSuggestionStatus;
  createdAt: number;
  decidedAt: number;
}
