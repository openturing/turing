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
