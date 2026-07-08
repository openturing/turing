/**
 * T595–T600 / Block AJ — Eval Studio client models: reusable datasets and
 * named grader stacks (the agent-decoupled eval assets surfaced at /bento/eval).
 *
 * @since 2026.3.4
 */

export interface TurEvalDatasetRow {
  id: string;
  name?: string | null;
  seedTurnsJson?: string | null;
  expectedSlotsJson?: string | null;
  expectedOutcome?: string | null;
  expectedNodeId?: string | null;
  rubric?: string | null;
  referenceAnswer?: string | null;
  tags?: string | null;
  metadataJson?: string | null;
  sortOrder: number;
}

/**
 * T597 — a candidate row synthesized/paraphrased by the LLM but NOT yet
 * persisted (draft). Same shape as a saved row minus the required id (the
 * backend returns {@code id: null}); the review form edits these before saving.
 */
export type TurEvalDatasetRowDraft = Omit<TurEvalDatasetRow, "id"> & {
  id?: string | null;
};

export interface TurEvalDataset {
  id: string;
  name: string;
  description?: string | null;
  version: number;
  rowCount: number;
  createdAt?: string | null;
  updatedAt?: string | null;
  rows?: TurEvalDatasetRow[];
}

export interface TurEvalGraderConfigEntry {
  graderId: string;
  name?: string | null;
  kind?: string | null;
  configJson?: string | null;
  weight: number;
  threshold: number;
  blocking: number;
  enabled: number;
  sortOrder: number;
}

export interface TurEvalGraderStack {
  id: string;
  name: string;
  description?: string | null;
  configs: TurEvalGraderConfigEntry[];
}

/** T594 — one reviewer's individual verdict on a review task. */
export interface TurEvalReviewVerdict {
  id: string;
  reviewer?: string | null;
  pass: boolean;
  score: number;
  notes?: string | null;
  reviewedAt?: string | null;
}

/**
 * T593 / §XXXIII.8 — a parked human-review task: the reviewer reads the
 * replayed transcript + expected summary, submits a verdict, and can promote
 * the row into a golden dataset. T594 adds the calibration fields: the task
 * type (DEFERRED vs AUDIT), N-reviewer progress, the model's original verdict
 * (AUDIT tasks), and the individual verdicts (detail view).
 */
export interface TurEvalReviewTask {
  id: string;
  agentId: string;
  caseId?: string | null;
  caseName?: string | null;
  graderId?: string | null;
  transcript?: string | null;
  expectedSummary?: string | null;
  status: string;
  createdAt?: string | null;
  reviewedPass?: boolean | null;
  reviewedScore?: number | null;
  reviewerNotes?: string | null;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  taskType?: string | null;
  requiredReviewers?: number;
  verdictCount?: number;
  modelPass?: boolean | null;
  modelScore?: number | null;
  verdicts?: TurEvalReviewVerdict[] | null;
}

/** A reviewer's verdict on a human-review task. */
export interface TurEvalReviewRequest {
  pass: boolean;
  score?: number | null;
  notes?: string | null;
}

/**
 * T594 — inter-annotator agreement (Fleiss' kappa) over an agent's
 * multi-reviewer tasks.
 */
export interface TurEvalAgreement {
  items: number;
  minRaters: number;
  maxRaters: number;
  percentAgreement?: number | null;
  kappa?: number | null;
  interpretation: string;
}

/**
 * T594 — MODEL-vs-human calibration over reviewed audit tasks (Cohen's kappa +
 * a plain-language judge-prompt tuning hint).
 */
export interface TurEvalCalibration {
  audited: number;
  agreementRate?: number | null;
  kappa?: number | null;
  interpretation: string;
  modelPassHumanPass: number;
  modelPassHumanFail: number;
  modelFailHumanPass: number;
  modelFailHumanFail: number;
  suggestion: string;
}

/**
 * T603 — one rolling continuous / online-eval snapshot: the quality of a window
 * of an agent's live production traffic, graded in the background. {@code -1}
 * rates mean "not measured" (no grader stack bound, or citation-drift off).
 * {@code note} is set only on a non-persisted "unavailable" result.
 */
export interface TurOnlineEvalSnapshot {
  id?: string | null;
  agentId: string;
  createdAt: string;
  windowStart: string;
  windowEnd: string;
  sampledSessions: number;
  gradedSessions: number;
  graderStackId?: string | null;
  meanScore: number;
  passRate: number;
  negativeSentimentRate: number;
  staleCitationRate: number;
  failingSessionRate: number;
  baseline: boolean;
  driftDetected: boolean;
  driftReason?: string | null;
  note?: string | null;
}
