/**
 * {@code turing research} — the Synthetic User Research developer surface
 * (Block AW / §XLVI.5, T734). Mirrors {@code turing eval}: define/run a study and
 * fetch its insights from code/CI over the same {@link TuringClient}. Subcommands:
 *
 * <ul>
 *   <li>{@code research list} — the studies on the instance.</li>
 *   <li>{@code research run <studyId> [--force]} — run the cohort interviews
 *       (blocking) and report the resulting interview count.</li>
 *   <li>{@code research insights <studyId>} — print the synthesized report
 *       (executive summary + ranked themes + recommendations).</li>
 *   <li>{@code research rollup <studyId...>} — a cross-study program rollup (T733).</li>
 * </ul>
 *
 * The orchestration is thin; each subcommand is one or two client calls. Exported
 * formatters are pure so they unit-test without a live instance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import type { TuringClient } from "../client.js";

export interface ResearchStudySummary {
  id: string;
  name: string;
  protocol: string;
  personaCount: number;
  interviewCount: number;
  schedule: string;
  lastRunAt?: string | null;
}

export interface ResearchTheme {
  title: string;
  summary: string;
  prevalence: number;
}

export interface ResearchReport {
  available: boolean;
  error?: string | null;
  executiveSummary?: string | null;
  themes: ResearchTheme[];
  recommendations: string[];
}

export interface ResearchProgramRollup {
  available: boolean;
  message?: string | null;
  studyCount: number;
  totalParticipants: number;
  totalInterviews: number;
  totalDistinctThemes: number;
  sharedThemeCount: number;
  themes: { title: string; studyCount: number }[];
}

/* ─────────────────────── pure formatters (unit-tested) ─────────────────────── */

export function formatStudies(studies: ResearchStudySummary[]): string[] {
  if (studies.length === 0) return ["No research studies found."];
  return studies.map(
    (s) =>
      `${s.id}  ${s.name}  [${s.protocol}]  ${s.personaCount} participant(s), ` +
      `${s.interviewCount} interview(s)  ${s.lastRunAt ? `run ${s.lastRunAt}` : "not run"}`,
  );
}

export function formatReport(report: ResearchReport): string[] {
  if (!report.available) {
    return [`Insights unavailable: ${report.error ?? "run the study first."}`];
  }
  const out: string[] = [];
  if (report.executiveSummary) {
    out.push("Executive summary:", `  ${report.executiveSummary}`, "");
  }
  out.push(`Themes (${report.themes.length}):`);
  for (const theme of report.themes) {
    out.push(`  • ${theme.title}  (${theme.prevalence} participant(s))`);
    if (theme.summary) out.push(`    ${theme.summary}`);
  }
  if (report.recommendations.length > 0) {
    out.push("", "Recommendations:");
    report.recommendations.forEach((rec, i) => out.push(`  ${i + 1}. ${rec}`));
  }
  return out;
}

export function formatRollup(rollup: ResearchProgramRollup): string[] {
  if (!rollup.available) {
    return [`Rollup unavailable: ${rollup.message ?? "select at least one study."}`];
  }
  const out = [
    `Program: ${rollup.studyCount} study(ies), ${rollup.totalParticipants} participant(s), ` +
      `${rollup.totalInterviews} interview(s)`,
    `Themes: ${rollup.totalDistinctThemes} distinct, ${rollup.sharedThemeCount} shared across studies`,
  ];
  const shared = rollup.themes.filter((t) => t.studyCount >= 2);
  if (shared.length > 0) {
    out.push("", "Cross-study themes:");
    for (const theme of shared) out.push(`  • ${theme.title}  (in ${theme.studyCount} studies)`);
  }
  return out;
}

/* ─────────────────────── dispatch ─────────────────────── */

/**
 * Dispatches a {@code turing research} subcommand. The client must already be
 * authenticated by the caller. Returns an exit code (non-zero on a run with no
 * interviews or an unavailable report, so CI can gate on it).
 */
export async function runResearch(
  client: TuringClient,
  positionals: string[],
  extra: Record<string, string | boolean>,
  log: (line: string) => void,
): Promise<number> {
  const sub = positionals[0];
  switch (sub) {
    case "list": {
      const studies = await client.get<ResearchStudySummary[]>("/api/research-study");
      for (const line of formatStudies(studies)) log(line);
      return 0;
    }
    case "run": {
      const id = requireStudyId(positionals[1], "run");
      const force = extra.force === true;
      log(`Running study ${id}…`);
      await client.post(`/api/research-study/${id}/run?force=${force}`, {});
      const study = await client.get<ResearchStudySummary>(`/api/research-study/${id}`);
      log(`Done — ${study.interviewCount} interview(s) across ${study.personaCount} participant(s).`);
      return study.interviewCount > 0 ? 0 : 1;
    }
    case "insights": {
      const id = requireStudyId(positionals[1], "insights");
      const report = await client.get<ResearchReport>(`/api/research-study/${id}/insights`);
      for (const line of formatReport(report)) log(line);
      return report.available ? 0 : 1;
    }
    case "rollup": {
      const ids = positionals.slice(1);
      if (ids.length === 0) throw new Error("Usage: turing research rollup <studyId> [<studyId>...]");
      const rollup = await client.post<ResearchProgramRollup>("/api/research-study/program/rollup", ids);
      for (const line of formatRollup(rollup)) log(line);
      return rollup.available ? 0 : 1;
    }
    default:
      throw new Error(
        "Usage: turing research <list|run|insights|rollup> [...]. Run `turing help`.",
      );
  }
}

function requireStudyId(id: string | undefined, sub: string): string {
  if (!id) throw new Error(`Usage: turing research ${sub} <studyId>`);
  return id;
}
