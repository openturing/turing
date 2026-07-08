/**
 * T602 — the remote eval path: run a named {@code dataset × grader stack}
 * server-side via {@code POST /api/eval/run} and return the CI-gate verdict.
 *
 * <p>This is the programmatic {@code runEval} SDK surface (re-exported from the
 * package root) behind {@code turing eval --dataset <id|name>}. Unlike the local
 * {@code *.eval.yaml} runner, no fixtures live in the repo — the dataset and
 * grader stack are stored in the Turing instance and referenced by id or name,
 * so the same reusable dataset gates any number of pipelines.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import type { TuringClient } from "../client.js";

/** One case's result inside a remote run report (mirrors {@code TurAgentEvalCaseResultDto}). */
export interface EvalCaseResult {
  caseId: string;
  caseName: string;
  passed: boolean;
  score: number;
  expectedOutcome?: string | null;
  actualOutcome?: string | null;
  rubricVerdict?: string | null;
  rubricRationale?: string | null;
  error?: string | null;
  pendingReview?: boolean;
}

/** The persisted report (mirrors {@code TurAgentEvalReportDto}). */
export interface EvalReport {
  reportId: string | null;
  passed: boolean;
  score: number;
  caseCount: number;
  passedCount: number;
  pendingReview: boolean;
  datasetId: string | null;
  datasetVersion: number;
  results: EvalCaseResult[];
  error: string | null;
}

/** The full run result (mirrors {@code TurEvalRunResultDto}). */
export interface EvalRunResult {
  gatePassed: boolean;
  minScore: number | null;
  datasetId: string;
  datasetName: string;
  graderStackId: string | null;
  report: EvalReport;
}

/** Options for {@link runEval}. */
export interface RunEvalOptions {
  /** Agent runtime to replay the dataset against. */
  agentId: string;
  /** Dataset id or human name (required). */
  dataset: string;
  /** Grader-stack id or name; omit for the default stack. */
  graderStack?: string;
  /** Optional score gate: the run only passes if {@code score >= minScore}. */
  minScore?: number;
}

/**
 * Runs a named {@code dataset × grader stack} against an agent and returns the
 * gate verdict. The caller must have already {@code authenticate()}d the client
 * (the POST is CSRF-protected). Throws {@link HttpError} on transport / auth
 * failures; a run that cannot start (missing agent, no LLM, empty dataset) comes
 * back as a 422 with the reason in the message.
 */
export async function runEval(client: TuringClient, options: RunEvalOptions): Promise<EvalRunResult> {
  if (!options.agentId) throw new Error("runEval: agentId is required.");
  if (!options.dataset) throw new Error("runEval: dataset (id or name) is required.");
  return client.post<EvalRunResult>("/api/eval/run", {
    agentId: options.agentId,
    dataset: options.dataset,
    graderStack: options.graderStack ?? null,
    minScore: options.minScore ?? null,
  });
}

/** Human-readable report lines for the CLI (summary + per-failed-case detail). */
export function formatRunResult(result: EvalRunResult): string[] {
  const out: string[] = [];
  const r = result.report;
  const stack = result.graderStackId ?? "default stack";
  out.push(
    `${result.datasetName} (${result.datasetId}) × ${stack} — ` +
      `${r.passedCount}/${r.caseCount} case(s) passed, score ${r.score.toFixed(3)}`,
  );
  for (const c of r.results) {
    if (c.passed) continue;
    if (c.error) {
      out.push(`  ⚠ ${c.caseName}: ${c.error}`);
    } else if (c.pendingReview) {
      out.push(`  ⏸ ${c.caseName}: awaiting human review`);
    } else {
      out.push(`  ✗ ${c.caseName}: ${caseFailureDetail(c)}`);
    }
  }
  if (result.minScore != null && r.score < result.minScore) {
    out.push(`  ✗ score ${r.score.toFixed(3)} < min-score ${result.minScore}`);
  }
  out.push(result.gatePassed ? "✓ gate passed" : "✗ gate failed");
  return out;
}

function caseFailureDetail(c: EvalCaseResult): string {
  if (c.rubricVerdict === "fail" && c.rubricRationale) return `rubric — ${c.rubricRationale}`;
  if (c.expectedOutcome && c.expectedOutcome !== c.actualOutcome) {
    return `expected outcome ${c.expectedOutcome} but got ${c.actualOutcome ?? "(none)"}`;
  }
  return `score ${c.score.toFixed(3)}`;
}
