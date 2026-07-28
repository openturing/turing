import { test } from "node:test";
import assert from "node:assert/strict";

import type { TuringClient } from "../src/client.js";
import {
  evaluateGate,
  formatDrift,
  formatGate,
  formatProjects,
  runMatch,
  type MatchDrift,
  type MatchMatrix,
  type MatchProjectDetail,
  type MatchProjectSummary,
} from "../src/commands/match.js";

const noop = () => {};

function project(overrides: Partial<MatchProjectDetail> = {}): MatchProjectDetail {
  return {
    id: "p1",
    name: "Docs portfolio",
    schedule: "DAILY",
    fitThreshold: null,
    effectiveFitThreshold: 75,
    sourceCount: 1,
    personaCount: 2,
    lastRunAt: "2026-07-28T03:30:00Z",
    sources: [{ id: "s1", sourceName: "Getting started" }],
    personas: [
      { id: "a1", name: "Beginner" },
      { id: "a2", name: "Expert" },
    ],
    ...overrides,
  };
}

function matrix(cells: MatchMatrix["cells"]): MatchMatrix {
  return { projectId: "p1", cells };
}

function drift(entries: MatchDrift["entries"], runCount = 2): MatchDrift {
  return {
    projectId: "p1",
    runCount,
    previousRunAt: "2026-07-27T03:30:00Z",
    currentRunAt: "2026-07-28T03:30:00Z",
    previousAvg: 80,
    currentAvg: 72,
    fitThreshold: 75,
    regressedCount: entries.filter((e) => e.delta < 0).length,
    improvedCount: entries.filter((e) => e.delta > 0).length,
    entries,
  };
}

/* ── evaluateGate ── */

test("evaluateGate passes when every cell is at or above the project bar", () => {
  const result = evaluateGate({
    project: project(),
    matrix: matrix([
      { sourceId: "s1", personaId: "a1", fitScore: 75, readability: 70, llmUsed: true },
      { sourceId: "s1", personaId: "a2", fitScore: 92, readability: 88, llmUsed: true },
    ]),
    failOnDrop: false,
  });
  assert.equal(result.passed, true);
  assert.equal(result.threshold, 75);
  assert.equal(result.thresholdOverridden, false);
  assert.equal(result.cellCount, 2);
});

test("evaluateGate fails and labels the pairs below the bar, worst first", () => {
  const result = evaluateGate({
    project: project(),
    matrix: matrix([
      { sourceId: "s1", personaId: "a1", fitScore: 61.44, readability: 55, llmUsed: true },
      { sourceId: "s1", personaId: "a2", fitScore: 40, readability: 38, llmUsed: false },
    ]),
    failOnDrop: false,
  });
  assert.equal(result.passed, false);
  assert.equal(result.belowThreshold.length, 2);
  assert.equal(result.belowThreshold[0]!.fitScore, 40); // worst first
  assert.equal(result.belowThreshold[0]!.content, "Getting started");
  assert.equal(result.belowThreshold[0]!.persona, "Expert");
  assert.equal(result.belowThreshold[1]!.fitScore, 61.4); // rounded for the log
});

test("evaluateGate honours --min-fit over the project bar", () => {
  const cells = matrix([
    { sourceId: "s1", personaId: "a1", fitScore: 70, readability: 65, llmUsed: true },
  ]);
  const lenient = evaluateGate({ project: project(), matrix: cells, minFit: 60, failOnDrop: false });
  assert.equal(lenient.passed, true);
  assert.equal(lenient.threshold, 60);
  assert.equal(lenient.thresholdOverridden, true);

  const strict = evaluateGate({ project: project(), matrix: cells, minFit: 90, failOnDrop: false });
  assert.equal(strict.passed, false);
});

test("evaluateGate ignores regressions unless --fail-on-drop is set", () => {
  const cells = matrix([
    { sourceId: "s1", personaId: "a1", fitScore: 88, readability: 80, llmUsed: true, previousFitScore: 95 },
  ]);
  const entries = [
    { sourceId: "s1", personaId: "a1", previousFit: 95, currentFit: 88, delta: -7, crossedBelowFit: false, crossedAboveFit: false, contentChanged: true },
  ];
  const off = evaluateGate({ project: project(), matrix: cells, drift: drift(entries), failOnDrop: false });
  assert.equal(off.passed, true);
  assert.equal(off.regressed.length, 0);

  const on = evaluateGate({ project: project(), matrix: cells, drift: drift(entries), failOnDrop: true });
  assert.equal(on.passed, false);
  assert.equal(on.regressed[0]!.delta, -7);
  assert.equal(on.regressed[0]!.content, "Getting started");
});

test("evaluateGate sorts regressions sharpest-drop first and skips improvements", () => {
  const entries = [
    { sourceId: "s1", personaId: "a1", previousFit: 90, currentFit: 88, delta: -2, crossedBelowFit: false, crossedAboveFit: false, contentChanged: false },
    { sourceId: "s1", personaId: "a2", previousFit: 90, currentFit: 70, delta: -20, crossedBelowFit: true, crossedAboveFit: false, contentChanged: false },
    { sourceId: "s1", personaId: "a2", previousFit: 70, currentFit: 80, delta: 10, crossedBelowFit: false, crossedAboveFit: true, contentChanged: false },
  ];
  const result = evaluateGate({
    project: project(),
    matrix: matrix([]),
    drift: drift(entries),
    failOnDrop: true,
  });
  assert.equal(result.regressed.length, 2);
  assert.equal(result.regressed[0]!.delta, -20);
});

test("evaluateGate falls back to raw ids when a label is missing", () => {
  const result = evaluateGate({
    project: project({ sources: [], personas: [] }),
    matrix: matrix([
      { sourceId: "s9", personaId: "a9", fitScore: 10, readability: 10, llmUsed: false },
    ]),
    failOnDrop: false,
  });
  assert.equal(result.belowThreshold[0]!.content, "s9");
  assert.equal(result.belowThreshold[0]!.persona, "a9");
});

/* ── formatters ── */

test("formatProjects reports an empty list and marks an inherited bar", () => {
  assert.deepEqual(formatProjects([]), ["No Persona Match projects found."]);
  const projects: MatchProjectSummary[] = [
    { id: "p1", name: "Docs", schedule: "DAILY", fitThreshold: null, effectiveFitThreshold: 75, sourceCount: 2, personaCount: 3, lastRunAt: null },
    { id: "p2", name: "Marketing", schedule: "MANUAL", fitThreshold: 60, effectiveFitThreshold: 60, sourceCount: 1, personaCount: 1, lastRunAt: "2026-07-28" },
  ];
  const lines = formatProjects(projects);
  assert.match(lines[0]!, /fit bar 75% \(default\).*not run/);
  assert.match(lines[1]!, /fit bar 60% {2}run 2026-07-28/);
});

test("formatGate prints both failure sections and the verdict", () => {
  const lines = formatGate({
    passed: false,
    threshold: 75,
    thresholdOverridden: true,
    cellCount: 4,
    belowThreshold: [{ content: "Page A", persona: "Beginner", fitScore: 61 }],
    regressed: [{ content: "Page B", persona: "Expert", fitScore: 70, previousFitScore: 90, delta: -20 }],
  }).join("\n");
  assert.match(lines, /4 cell\(s\) against a 75% \(--min-fit\) fit bar/);
  assert.match(lines, /Below the fit bar \(1\)/);
  assert.match(lines, /61% {2}Page A {2}× {2}Beginner/);
  assert.match(lines, /90% → 70% {2}\(-20\)/);
  assert.match(lines, /Content fit gate failed/);
});

test("formatGate is quiet and positive when the gate passes", () => {
  const lines = formatGate({
    passed: true,
    threshold: 75,
    thresholdOverridden: false,
    cellCount: 2,
    belowThreshold: [],
    regressed: [],
  }).join("\n");
  assert.match(lines, /Content fit gate passed/);
  assert.doesNotMatch(lines, /Below the fit bar/);
});

test("formatDrift explains a project with a single run", () => {
  assert.match(formatDrift(drift([], 1))[0]!, /needs two runs; this project has 1/);
});

test("formatDrift lists regressions and flags a crossing", () => {
  const lines = formatDrift(
    drift([
      { sourceId: "s1", personaId: "a1", previousFit: 90, currentFit: 70, delta: -20, crossedBelowFit: true, crossedAboveFit: false, contentChanged: true },
    ]),
  ).join("\n");
  assert.match(lines, /Average 80% → 72% \(-8\)/);
  assert.match(lines, /fell below the fit bar/);
  assert.match(lines, /content changed/);
});

/* ── runMatch dispatch ── */

test("runMatch list calls the project endpoint", async () => {
  const calls: string[] = [];
  const client = {
    get: async (path: string) => {
      calls.push(path);
      return [] as unknown;
    },
  } as unknown as TuringClient;
  assert.equal(await runMatch(client, ["list"], {}, noop), 0);
  assert.deepEqual(calls, ["/api/persona-match"]);
});

test("runMatch run resolves a project by name and gates on the result", async () => {
  const posted: string[] = [];
  const detail = project();
  const client = {
    get: async (path: string) => {
      if (path === "/api/persona-match") return [detail as MatchProjectSummary] as unknown;
      if (path.endsWith("/matrix")) {
        return matrix([
          { sourceId: "s1", personaId: "a1", fitScore: 30, readability: 20, llmUsed: true },
        ]) as unknown;
      }
      return detail as unknown;
    },
    post: async (path: string) => {
      posted.push(path);
      return {} as unknown;
    },
    streamChat: async (path: string) => {
      posted.push(path);
    },
  } as unknown as TuringClient;

  const code = await runMatch(client, ["run", "docs portfolio"], {}, noop);
  assert.equal(code, 1); // 30% is below the 75% project bar
  assert.match(posted[0]!, /\/api\/persona-match\/p1\/run\/stream\?force=false/);
});

test("runMatch run refuses an empty matrix instead of passing vacuously", async () => {
  const detail = project({ sourceCount: 0, personaCount: 0 });
  const client = {
    get: async (path: string) =>
      (path === "/api/persona-match" ? [detail] : detail) as unknown,
  } as unknown as TuringClient;
  assert.equal(await runMatch(client, ["run", "p1"], {}, noop), 1);
});

test("runMatch run rejects an unknown project and a bad --min-fit", async () => {
  const client = {
    get: async () => [project()] as unknown,
  } as unknown as TuringClient;
  await assert.rejects(
    () => runMatch(client, ["run", "nope"], {}, noop),
    /Unknown Persona Match project/,
  );
  await assert.rejects(
    () => runMatch(client, ["run", "p1"], { "min-fit": "abc" }, noop),
    /--min-fit must be a number/,
  );
});

test("runMatch rejects an unknown subcommand", async () => {
  await assert.rejects(
    () => runMatch({} as unknown as TuringClient, ["nope"], {}, noop),
    /Usage: turing match/,
  );
});
