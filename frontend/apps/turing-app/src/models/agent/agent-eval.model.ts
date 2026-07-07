/**
 * T285–T288 / §XV (Block K) — Agent-CI eval types, mirroring the backend
 * DTOs (`TurAgentEvalGateDto`, `TurAgentEvalReportDto`,
 * `TurAgentEvalCaseResultDto`) and the `TurAgentEvalSet` aggregate.
 *
 * @since 2026.3.1
 */

export type TurAgentEvalExpectedOutcome =
  | "CAPTURED"
  | "ABANDONED"
  | "HANDOFF"
  | "ANY";

/** One golden-set case (a scripted conversation + expectations). */
export interface TurAgentEvalCase {
  id?: string;
  name: string;
  description?: string;
  /** JSON array of the scripted user turns. */
  seedTurnsJson?: string;
  /** JSON map of slotName -> expected value. */
  expectedSlotsJson?: string;
  expectedOutcome?: TurAgentEvalExpectedOutcome;
  expectedNodeId?: string;
  rubric?: string;
  sortOrder?: number;
}

/** A per-agent golden set of regression cases. */
export interface TurAgentEvalSet {
  id?: string;
  name: string;
  description?: string;
  /** 1 = runs in the gate, 0 = parked. */
  enabled: number;
  /** 1 = a red gate hard-blocks publish; 0 = warn only. */
  blocking: number;
  cases: TurAgentEvalCase[];
}

export interface TurAgentEvalSlotDiff {
  slot: string;
  expected: string;
  actual: string | null;
  match: boolean;
}

export interface TurAgentEvalCaseResult {
  caseId: string;
  caseName: string;
  passed: boolean;
  score: number;
  expectedOutcome: string | null;
  actualOutcome: string | null;
  finalNodeId: string | null;
  slotDiffs: TurAgentEvalSlotDiff[];
  rubricVerdict: string;
  rubricRationale: string | null;
  error: string | null;
}

export interface TurAgentEvalReport {
  reportId: string | null;
  createdAt: string | null;
  passed: boolean;
  score: number;
  caseCount: number;
  passedCount: number;
  baseline: boolean;
  regressed: boolean;
  /** T592 — true when any case deferred to a human grader. */
  pendingReview?: boolean;
  results: TurAgentEvalCaseResult[];
  error: string | null;
}

export interface TurAgentEvalGateFinding {
  severity: "ERROR" | "WARNING" | "INFO";
  code: string;
  message: string;
  hint: string;
}

export type TurAgentEvalGateStatus =
  | "NOT_CONFIGURED"
  | "NEVER_RUN"
  | "GREEN"
  | "RED"
  | "REGRESSED";

export interface TurAgentEvalGate {
  status: TurAgentEvalGateStatus;
  blocking: boolean;
  lastReport: TurAgentEvalReport | null;
  findings: TurAgentEvalGateFinding[];
}
