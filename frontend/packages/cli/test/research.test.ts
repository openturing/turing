import { test } from "node:test";
import assert from "node:assert/strict";

import type { TuringClient } from "../src/client.js";
import {
  formatReport,
  formatRollup,
  formatStudies,
  runResearch,
  type ResearchProgramRollup,
  type ResearchReport,
  type ResearchStudySummary,
} from "../src/commands/research.js";

const noop = () => {};

/* ── formatStudies ── */

test("formatStudies reports an empty list", () => {
  assert.deepEqual(formatStudies([]), ["No research studies found."]);
});

test("formatStudies renders a run and an un-run study", () => {
  const studies: ResearchStudySummary[] = [
    { id: "a", name: "Onboarding", protocol: "DYNAMIC_SCRIPT", personaCount: 3, interviewCount: 3, schedule: "MANUAL", lastRunAt: "2026-07-13T00:00:00Z" },
    { id: "b", name: "Pricing", protocol: "CONCEPT_TEST", personaCount: 4, interviewCount: 0, schedule: "DAILY", lastRunAt: null },
  ];
  const lines = formatStudies(studies);
  assert.match(lines[0]!, /Onboarding.*DYNAMIC_SCRIPT.*3 participant/);
  assert.match(lines[0]!, /run 2026-07-13/);
  assert.match(lines[1]!, /not run/);
});

/* ── formatReport ── */

test("formatReport surfaces an unavailable report", () => {
  const report: ResearchReport = { available: false, error: "run the study", themes: [], recommendations: [] };
  assert.deepEqual(formatReport(report), ["Insights unavailable: run the study"]);
});

test("formatReport renders summary, themes and recommendations", () => {
  const report: ResearchReport = {
    available: true,
    executiveSummary: "Users struggle with setup.",
    themes: [{ title: "Setup friction", summary: "Too many steps", prevalence: 2 }],
    recommendations: ["Cut the wizard to 2 steps"],
  };
  const lines = formatReport(report).join("\n");
  assert.match(lines, /Executive summary/);
  assert.match(lines, /Setup friction {2}\(2 participant/);
  assert.match(lines, /1\. Cut the wizard/);
});

/* ── formatRollup ── */

test("formatRollup highlights cross-study themes", () => {
  const rollup: ResearchProgramRollup = {
    available: true,
    studyCount: 2,
    totalParticipants: 7,
    totalInterviews: 7,
    totalDistinctThemes: 3,
    sharedThemeCount: 1,
    themes: [
      { title: "Onboarding friction", studyCount: 2 },
      { title: "Pricing clarity", studyCount: 1 },
    ],
  };
  const lines = formatRollup(rollup).join("\n");
  assert.match(lines, /2 study\(ies\), 7 participant/);
  assert.match(lines, /Onboarding friction {2}\(in 2 studies\)/);
  assert.doesNotMatch(lines, /Pricing clarity/);
});

/* ── runResearch dispatch ── */

test("runResearch list calls the list endpoint", async () => {
  const calls: string[] = [];
  const client = {
    get: async (path: string) => {
      calls.push(path);
      return [] as unknown;
    },
  } as unknown as TuringClient;
  const code = await runResearch(client, ["list"], {}, noop);
  assert.equal(code, 0);
  assert.deepEqual(calls, ["/api/research-study"]);
});

test("runResearch insights returns non-zero when unavailable (CI gate)", async () => {
  const client = {
    get: async () => ({ available: false, error: "x", themes: [], recommendations: [] }) as unknown,
  } as unknown as TuringClient;
  const code = await runResearch(client, ["insights", "s1"], {}, noop);
  assert.equal(code, 1);
});

test("runResearch rejects an unknown subcommand", async () => {
  const client = {} as unknown as TuringClient;
  await assert.rejects(() => runResearch(client, ["nope"], {}, noop), /Usage: turing research/);
});
