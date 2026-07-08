import { test } from "node:test";
import assert from "node:assert/strict";

import type { TuringClient } from "../src/client.js";
import { formatRunResult, runEval, type EvalRunResult } from "../src/eval/remote.js";

/** Minimal TuringClient stub capturing the POST body and returning a canned result. */
function stubClient(result: EvalRunResult): { client: TuringClient; calls: { path: string; body: any }[] } {
  const calls: { path: string; body: any }[] = [];
  const client = {
    async post(path: string, body: unknown) {
      calls.push({ path, body });
      return result;
    },
  } as unknown as TuringClient;
  return { client, calls };
}

function greenResult(): EvalRunResult {
  return {
    gatePassed: true,
    minScore: null,
    datasetId: "ds-1",
    datasetName: "Golden Leads",
    graderStackId: null,
    report: {
      reportId: "rep-1",
      passed: true,
      score: 0.95,
      caseCount: 2,
      passedCount: 2,
      pendingReview: false,
      datasetId: "ds-1",
      datasetVersion: 1,
      results: [],
      error: null,
    },
  };
}

test("runEval POSTs the resolved payload to /api/eval/run", async () => {
  const { client, calls } = stubClient(greenResult());
  const result = await runEval(client, { agentId: "a1", dataset: "Golden Leads", graderStack: "Strict", minScore: 0.8 });
  assert.equal(calls.length, 1);
  assert.equal(calls[0]!.path, "/api/eval/run");
  assert.deepEqual(calls[0]!.body, {
    agentId: "a1",
    dataset: "Golden Leads",
    graderStack: "Strict",
    minScore: 0.8,
  });
  assert.equal(result.gatePassed, true);
});

test("runEval defaults graderStack and minScore to null", async () => {
  const { client, calls } = stubClient(greenResult());
  await runEval(client, { agentId: "a1", dataset: "ds-1" });
  assert.deepEqual(calls[0]!.body, { agentId: "a1", dataset: "ds-1", graderStack: null, minScore: null });
});

test("runEval validates required fields", async () => {
  const { client } = stubClient(greenResult());
  await assert.rejects(() => runEval(client, { agentId: "", dataset: "ds-1" }), /agentId is required/);
  await assert.rejects(() => runEval(client, { agentId: "a1", dataset: "" }), /dataset .* is required/);
});

test("formatRunResult summarizes a green run", () => {
  const lines = formatRunResult(greenResult());
  assert.match(lines[0]!, /Golden Leads \(ds-1\) × default stack — 2\/2 case\(s\) passed, score 0\.950/);
  assert.equal(lines.at(-1), "✓ gate passed");
});

test("formatRunResult lists failed cases and a min-score miss", () => {
  const result: EvalRunResult = {
    gatePassed: false,
    minScore: 0.9,
    datasetId: "ds-1",
    datasetName: "Leads",
    graderStackId: "stack-1",
    report: {
      reportId: "rep-2",
      passed: false,
      score: 0.5,
      caseCount: 3,
      passedCount: 1,
      pendingReview: true,
      datasetId: "ds-1",
      datasetVersion: 2,
      error: null,
      results: [
        { caseId: "c1", caseName: "ok", passed: true, score: 1 },
        {
          caseId: "c2",
          caseName: "bad-rubric",
          passed: false,
          score: 0,
          rubricVerdict: "fail",
          rubricRationale: "hallucinated a price",
        },
        { caseId: "c3", caseName: "needs-human", passed: false, score: 0, pendingReview: true },
      ],
    },
  };
  const lines = formatRunResult(result);
  assert.ok(lines.some((l) => l.includes("bad-rubric") && l.includes("hallucinated a price")));
  assert.ok(lines.some((l) => l.includes("needs-human") && l.includes("awaiting human review")));
  assert.ok(lines.some((l) => l.includes("score 0.500 < min-score 0.9")));
  assert.equal(lines.at(-1), "✗ gate failed");
});
