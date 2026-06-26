/**
 * {@code turing eval} — discovers, loads, runs, and reports {@code *.eval.yaml}
 * suites against a running agent. The orchestration here is thin; the surface
 * parsing lives in {@code eval/load.ts} and the scoring in {@code eval/runner.ts}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import { existsSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

import { loadSuiteFile } from "../eval/load.js";
import { describeAssertion, runSuite, type EvalBackend } from "../eval/runner.js";
import type { SuiteResult } from "../eval/types.js";

/** Resolves a target (file, directory, or default `evals/`) to suite files. */
export function discoverSuiteFiles(cwd: string, target?: string): string[] {
  const base = target ? join(cwd, target) : join(cwd, "evals");
  if (existsSync(base) && statSync(base).isFile()) return [base];
  if (!existsSync(base)) return [];
  return readdirSync(base)
    .filter((f) => f.endsWith(".eval.yaml") || f.endsWith(".eval.yml") || f.endsWith(".eval.json"))
    .sort()
    .map((f) => join(base, f));
}

/**
 * Runs every suite file and reports results. Returns true when all fixtures in
 * all suites passed.
 */
export async function runEvalFiles(
  files: string[],
  backend: EvalBackend,
  agentId: string,
  log: (line: string) => void,
): Promise<boolean> {
  if (files.length === 0) {
    log("No *.eval.yaml suites found (looked in ./evals).");
    return true;
  }
  let allPassed = true;
  let totalFixtures = 0;
  let passedFixtures = 0;
  for (const file of files) {
    const suite = loadSuiteFile(file);
    log(`\n${file}`);
    const result = await runSuite(suite, backend, { agentId, onLog: (l) => log("  " + l) });
    for (const line of formatSuiteDetail(result)) log(line);
    allPassed = allPassed && result.passed;
    totalFixtures += result.fixtures.length;
    passedFixtures += result.fixtures.filter((f) => f.passed).length;
  }
  log(`\n${passedFixtures}/${totalFixtures} fixture(s) passed across ${files.length} suite(s).`);
  return allPassed;
}

/** Per-fixture failure detail lines (passing fixtures stay quiet beyond their ✓). */
export function formatSuiteDetail(result: SuiteResult): string[] {
  const out: string[] = [];
  for (const fixture of result.fixtures) {
    if (fixture.error) {
      out.push(`    ⚠ ${fixture.fixture.id}: ${fixture.error}`);
      continue;
    }
    if (fixture.passed) continue;
    for (const a of fixture.assertions) {
      if (!a.passed) out.push(`    ✗ ${describeAssertion(a.assertion)} — ${a.detail}`);
    }
  }
  return out;
}
