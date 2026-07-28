/**
 * {@code turing match} — content-fit as a CI gate (Block BN / §LXI.4, T828).
 *
 * <p>Turing already gates <em>agent</em> quality in CI through {@code turing eval}
 * (T599/T602). Persona Match content-fit has the same shape — a score over
 * versioned artifacts — and had none of the enforcement: a docs repo that merged a
 * page rewritten for the wrong reading level only found out at the next scheduled
 * run, in a UI, days later. Subcommands:
 *
 * <ul>
 *   <li>{@code match list} — the Persona Match projects on the instance.</li>
 *   <li>{@code match run <project> [--min-fit N] [--fail-on-drop] [--force]} — run
 *       the N×N analysis (streaming progress), then <b>exit non-zero</b> when a
 *       content lands below the fit bar or regressed versus the previous run,
 *       printing the offending {@code (content × persona)} pairs.</li>
 *   <li>{@code match drift <project>} — the T704 drift report, no run triggered.</li>
 * </ul>
 *
 * <p>{@code <project>} accepts an id or a (case-insensitive) name. The gate's bar
 * is {@code --min-fit} when given, else the project's own T826
 * {@code effectiveFitThreshold} — so CI and the studio agree by default.
 *
 * <p><b>Honest caveat:</b> an LLM-scored gate is not bit-reproducible. That is why
 * the <em>threshold</em> is the gate rather than an exact score match (the same
 * reasoning that keeps T385's NL→facet eval scorer LLM-free). Expect a point or two
 * of run-to-run jitter and set {@code --min-fit} with a little headroom.
 *
 * <p>No new backend surface: this reuses the existing run/matrix/drift endpoints
 * over the shared {@link TuringClient}. Exported formatters and the gate evaluator
 * are pure so they unit-test without a live instance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

import type { TuringClient } from "../client.js";

export interface MatchProjectSummary {
  id: string;
  name: string;
  schedule: string;
  fitThreshold?: number | null;
  effectiveFitThreshold: number;
  sourceCount: number;
  personaCount: number;
  lastRunAt?: string | null;
}

export interface MatchProjectDetail extends MatchProjectSummary {
  sources: { id: string; sourceName?: string | null; ref?: string | null }[];
  personas: { id: string; name: string }[];
}

export interface MatchCell {
  sourceId: string;
  personaId: string;
  fitScore: number;
  readability: number;
  llmUsed: boolean;
  previousFitScore?: number | null;
}

export interface MatchMatrix {
  projectId: string;
  cells: MatchCell[];
}

export interface MatchDriftEntry {
  sourceId: string;
  personaId: string;
  previousFit: number;
  currentFit: number;
  delta: number;
  crossedBelowFit: boolean;
  crossedAboveFit: boolean;
  contentChanged: boolean;
}

export interface MatchDrift {
  projectId: string;
  runCount: number;
  previousRunAt?: string | null;
  currentRunAt?: string | null;
  previousAvg: number;
  currentAvg: number;
  fitThreshold: number;
  regressedCount: number;
  improvedCount: number;
  entries: MatchDriftEntry[];
}

/** One SSE progress event from the streaming runner. */
interface MatchRunEvent {
  type: "STARTED" | "CELL" | "DONE" | "ERROR";
  total: number;
  completed: number;
  error?: string | null;
}

/* ─────────────────────── gate (pure) ─────────────────────── */

/** A pair the gate objected to, already labelled for a CI log. */
export interface MatchOffender {
  content: string;
  persona: string;
  fitScore: number;
  previousFitScore?: number | null;
  delta?: number | null;
}

export interface MatchGateResult {
  passed: boolean;
  threshold: number;
  /** Bar came from `--min-fit` rather than the project's own setting. */
  thresholdOverridden: boolean;
  cellCount: number;
  belowThreshold: MatchOffender[];
  regressed: MatchOffender[];
}

export interface MatchGateInput {
  project: MatchProjectDetail;
  matrix: MatchMatrix;
  drift?: MatchDrift | null;
  /** `--min-fit`; falls back to the project's effective threshold. */
  minFit?: number;
  /** `--fail-on-drop`: any regression versus the previous run fails the gate. */
  failOnDrop: boolean;
}

/**
 * Decides whether a finished run passes. Two independent rules: <b>below the
 * bar</b> (always applied) and <b>regressed since the previous run</b> (only with
 * {@code --fail-on-drop}, read off the T704 drift so it means the same thing the
 * studio and the T827 alert mean).
 */
export function evaluateGate(input: MatchGateInput): MatchGateResult {
  const { project, matrix, drift, minFit, failOnDrop } = input;
  const overridden = typeof minFit === "number" && Number.isFinite(minFit);
  const threshold = overridden ? (minFit as number) : project.effectiveFitThreshold;
  const label = labeller(project);

  const belowThreshold = matrix.cells
    .filter((c) => c.fitScore < threshold)
    .sort((a, b) => a.fitScore - b.fitScore)
    .map((c) => ({
      ...label(c.sourceId, c.personaId),
      fitScore: round(c.fitScore),
      previousFitScore: c.previousFitScore ?? null,
    }));

  const regressed: MatchOffender[] = [];
  if (failOnDrop) {
    for (const entry of (drift?.entries ?? []).filter((e) => e.delta < 0)) {
      regressed.push({
        ...label(entry.sourceId, entry.personaId),
        fitScore: round(entry.currentFit),
        previousFitScore: round(entry.previousFit),
        delta: round(entry.delta),
      });
    }
    regressed.sort((a, b) => (a.delta ?? 0) - (b.delta ?? 0));
  }

  return {
    passed: belowThreshold.length === 0 && regressed.length === 0,
    threshold,
    thresholdOverridden: overridden,
    cellCount: matrix.cells.length,
    belowThreshold,
    regressed,
  };
}

/** Resolves cell ids to human names using the project's own collections. */
function labeller(project: MatchProjectDetail) {
  const sources = new Map(
    (project.sources ?? []).map((s) => [s.id, s.sourceName || s.ref || s.id] as const),
  );
  const personas = new Map((project.personas ?? []).map((p) => [p.id, p.name || p.id] as const));
  return (sourceId: string, personaId: string) => ({
    content: sources.get(sourceId) ?? sourceId,
    persona: personas.get(personaId) ?? personaId,
  });
}

function round(value: number): number {
  return Math.round(value * 10) / 10;
}

/* ─────────────────────── formatters (pure) ─────────────────────── */

export function formatProjects(projects: MatchProjectSummary[]): string[] {
  if (projects.length === 0) return ["No Persona Match projects found."];
  return projects.map(
    (p) =>
      `${p.id}  ${p.name}  [${p.schedule}]  ${p.sourceCount} content(s) × ` +
      `${p.personaCount} persona(s)  fit bar ${p.effectiveFitThreshold}%` +
      `${p.fitThreshold === null || p.fitThreshold === undefined ? " (default)" : ""}  ` +
      `${p.lastRunAt ? `run ${p.lastRunAt}` : "not run"}`,
  );
}

export function formatGate(result: MatchGateResult): string[] {
  const out: string[] = [];
  const bar = `${result.threshold}%${result.thresholdOverridden ? " (--min-fit)" : " (project)"}`;
  out.push(`Gate: ${result.cellCount} cell(s) against a ${bar} fit bar.`);
  if (result.belowThreshold.length > 0) {
    out.push("", `✗ Below the fit bar (${result.belowThreshold.length}):`);
    for (const o of result.belowThreshold) {
      out.push(`    ${o.fitScore}%  ${o.content}  ×  ${o.persona}`);
    }
  }
  if (result.regressed.length > 0) {
    out.push("", `✗ Regressed since the previous run (${result.regressed.length}):`);
    for (const o of result.regressed) {
      out.push(
        `    ${o.previousFitScore}% → ${o.fitScore}%  (${signed(o.delta ?? 0)})  ` +
          `${o.content}  ×  ${o.persona}`,
      );
    }
  }
  out.push("", result.passed ? "✓ Content fit gate passed." : "✗ Content fit gate failed.");
  return out;
}

export function formatDrift(drift: MatchDrift): string[] {
  if (drift.runCount < 2) {
    return [`Drift needs two runs; this project has ${drift.runCount} on record.`];
  }
  const out = [
    `Drift ${drift.previousRunAt} → ${drift.currentRunAt} (fit bar ${drift.fitThreshold}%)`,
    `Average ${drift.previousAvg}% → ${drift.currentAvg}% (${signed(
      round(drift.currentAvg - drift.previousAvg),
    )})  ${drift.regressedCount} regressed, ${drift.improvedCount} improved`,
  ];
  const notable = drift.entries.filter((e) => e.delta < 0 || e.crossedBelowFit);
  if (notable.length > 0) {
    out.push("", "Regressions:");
    for (const e of notable) {
      out.push(
        `  ${signed(e.delta)}  ${e.previousFit}% → ${e.currentFit}%  ${e.sourceId} × ${e.personaId}` +
          `${e.crossedBelowFit ? "  [fell below the fit bar]" : ""}` +
          `${e.contentChanged ? "  [content changed]" : ""}`,
      );
    }
  }
  return out;
}

function signed(delta: number): string {
  return `${delta > 0 ? "+" : ""}${delta}`;
}

/* ─────────────────────── dispatch ─────────────────────── */

/**
 * Dispatches a {@code turing match} subcommand. The client must already be
 * authenticated. Returns the process exit code — non-zero when the gate fails, so
 * a CI step can simply run the command.
 */
export async function runMatch(
  client: TuringClient,
  positionals: string[],
  extra: Record<string, string | boolean>,
  log: (line: string) => void,
): Promise<number> {
  const sub = positionals[0];
  switch (sub) {
    case "list": {
      const projects = await client.get<MatchProjectSummary[]>("/api/persona-match");
      for (const line of formatProjects(projects)) log(line);
      return 0;
    }
    case "run":
      return runGate(client, positionals[1], extra, log);
    case "drift": {
      const project = await resolveProject(client, positionals[1], "drift");
      const drift = await client.get<MatchDrift>(`/api/persona-match/${project.id}/drift`);
      for (const line of formatDrift(drift)) log(line);
      return 0;
    }
    default:
      throw new Error("Usage: turing match <list|run|drift> [...]. Run `turing help`.");
  }
}

async function runGate(
  client: TuringClient,
  ref: string | undefined,
  extra: Record<string, string | boolean>,
  log: (line: string) => void,
): Promise<number> {
  const project = await resolveProject(client, ref, "run");
  const minFit = parseMinFit(extra["min-fit"]);
  const failOnDrop = extra["fail-on-drop"] === true;
  const force = extra.force === true;

  const expected = project.sourceCount * project.personaCount;
  if (expected === 0) {
    log(`✗ Project "${project.name}" has no cells to evaluate (add a content and a persona).`);
    return 1;
  }

  log(`Running ${project.name} — ${project.sourceCount} content(s) × ${project.personaCount} persona(s)…`);
  await streamRun(client, project.id, force, log);

  const [matrix, drift, refreshed] = await Promise.all([
    client.get<MatchMatrix>(`/api/persona-match/${project.id}/matrix`),
    failOnDrop
      ? client.get<MatchDrift>(`/api/persona-match/${project.id}/drift`)
      : Promise.resolve(null),
    client.get<MatchProjectDetail>(`/api/persona-match/${project.id}`),
  ]);

  const result = evaluateGate({ project: refreshed, matrix, drift, minFit, failOnDrop });
  for (const line of formatGate(result)) log(line);
  return result.passed ? 0 : 1;
}

/**
 * Streams the run over SSE, reporting progress at ~10% steps so a large matrix
 * doesn't flood a CI log. Falls back to the blocking endpoint when the stream
 * cannot be opened (an old instance, or a proxy that buffers SSE).
 */
async function streamRun(
  client: TuringClient,
  projectId: string,
  force: boolean,
  log: (line: string) => void,
): Promise<void> {
  const path = `/api/persona-match/${projectId}/run/stream?force=${force}`;
  let lastReported = 0;
  let failure: string | null = null;
  try {
    await client.streamChat(path, {}, (data) => {
      const event = parseEvent(data);
      if (!event) return;
      if (event.type === "ERROR") {
        failure = event.error ?? "the run reported an error";
        return;
      }
      if (event.total <= 0) return;
      const percent = Math.floor((event.completed / event.total) * 100);
      if (event.type === "DONE" || percent >= lastReported + 10) {
        lastReported = percent - (percent % 10);
        log(`  … ${event.completed}/${event.total} cell(s) (${percent}%)`);
      }
    });
  } catch (err) {
    log(`  (progress stream unavailable: ${(err as Error).message}) — running blocking…`);
    await client.post(`/api/persona-match/${projectId}/run?force=${force}`, {});
    return;
  }
  if (failure) throw new Error(`Analysis failed: ${failure}`);
}

function parseEvent(data: string): MatchRunEvent | null {
  try {
    return JSON.parse(data) as MatchRunEvent;
  } catch {
    return null; // heartbeat / non-JSON frame
  }
}

/** Resolves a project by id or (case-insensitive) name. */
async function resolveProject(
  client: TuringClient,
  ref: string | undefined,
  sub: string,
): Promise<MatchProjectDetail> {
  if (!ref) throw new Error(`Usage: turing match ${sub} <projectIdOrName> [flags]`);
  const projects = await client.get<MatchProjectSummary[]>("/api/persona-match");
  const needle = ref.trim().toLowerCase();
  const hit =
    projects.find((p) => p.id === ref) ??
    projects.find((p) => (p.name ?? "").trim().toLowerCase() === needle);
  if (!hit) {
    throw new Error(
      `Unknown Persona Match project: ${ref}. Run \`turing match list\` to see them.`,
    );
  }
  return client.get<MatchProjectDetail>(`/api/persona-match/${hit.id}`);
}

function parseMinFit(raw: string | boolean | undefined): number | undefined {
  if (raw === undefined || raw === true) return undefined;
  const value = Number(raw);
  if (!Number.isFinite(value) || value <= 0 || value > 100) {
    throw new Error("--min-fit must be a number between 1 and 100.");
  }
  return value;
}
