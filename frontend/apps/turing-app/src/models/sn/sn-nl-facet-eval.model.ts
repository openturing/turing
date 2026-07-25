/**
 * Types for the SN NL→facet eval-pack endpoint (T385): `POST /api/sn/nl-facet-eval`.
 *
 * An eval pack is a declared field schema plus a set of prose queries with their
 * golden structured outputs. Running the pack through the configured LLM parser
 * and scoring each case is how NL-parsing regressions surface — the runtime analog
 * of the agent CI gate, applied to the faceted-search parser. The onboarding
 * wizard (T391) uses it as the "validate the schema is queryable" step.
 */

import type { TurCopilotPlanningStrategy } from "@/models/sn/sn-site-genai.model.ts";

/** A facet/term equality the parser must emit. */
export type TurNLFacetExpectedFilter = {
  field: string;
  value: string;
};

/**
 * A numeric range the parser must emit on `field`. Only the non-null bounds are
 * checked; an upper bound matches `lte` or `lt`, a lower bound matches `gte` or `gt`.
 */
export type TurNLFacetExpectedRange = {
  field: string;
  gte?: number | null;
  gt?: number | null;
  lte?: number | null;
  lt?: number | null;
};

/** Expected primary sort: a field and an order (`asc` / `desc`). */
export type TurNLFacetExpectedSort = {
  field: string;
  order: string;
};

/** The golden expected structured output for one case. */
export type TurNLFacetExpectation = {
  filters?: TurNLFacetExpectedFilter[];
  ranges?: TurNLFacetExpectedRange[];
  sort?: TurNLFacetExpectedSort | null;
};

/** One NL→facet eval case: a prose query plus the golden output it must parse into. */
export type TurNLFacetEvalCase = {
  name: string;
  query: string;
  expect: TurNLFacetExpectation;
};

/** A lean field projection the parser maps onto — `type` is a `TurSEFieldType` enum name. */
export type TurNLFacetField = {
  name: string;
  type: string;
  facet: boolean;
  description?: string | null;
};

/** Request body for `POST /api/sn/nl-facet-eval`. Empty `fields` ⇒ resolve from the live site. */
export type TurNLFacetEvalPack = {
  name: string;
  index: string;
  locale?: string | null;
  fields?: TurNLFacetField[];
  cases: TurNLFacetEvalCase[];
};

/** The score for one case in the report. */
export type TurNLFacetEvalCaseResult = {
  caseName: string;
  passed: boolean;
  score: number;
  findings: string[];
  ungroundedFields: string[];
  error?: string | null;
};

/** Aggregate pass/fail plus the per-case breakdown returned by the eval endpoint. */
export type TurNLFacetEvalReport = {
  packName: string;
  passed: boolean;
  caseCount: number;
  passedCount: number;
  score: number;
  results: TurNLFacetEvalCaseResult[];
  error?: string | null;
};

/* ── T821 / §LIX.4 — comparative copilot planning-strategy eval ── */

/** Request body for `POST /api/sn/nl-facet-eval/planning-comparison`. */
export type TurCopilotPlanningComparisonRequest = {
  pack: TurNLFacetEvalPack;
  /** Empty ⇒ compare every strategy. */
  strategies?: TurCopilotPlanningStrategy[];
  /** Analysis depth for the LLM strategies; null ⇒ the configured default. */
  maxPasses?: number | null;
};

/** How one planning strategy scored on the pack: quality, cost and latency. */
export type TurCopilotPlanningStrategyOutcome = {
  strategy: TurCopilotPlanningStrategy;
  passed: boolean;
  passedCount: number;
  /** Mean per-case score (0..1) — the quality axis. */
  score: number;
  /** Total LLM calls across every case — the cost axis. */
  llmPasses: number;
  /** Wall-clock to plan every case — the latency axis. */
  elapsedMillis: number;
  /** How to read this row (depth used, HYBRID's plan-only equivalence…). */
  note: string;
  results: TurNLFacetEvalCaseResult[];
};

/**
 * The side-by-side planning-strategy comparison. `caveats` is not decoration —
 * the run is plan-only, so `HYBRID` scores identically to `DETERMINISTIC` by
 * construction (its escalation needs a live retrieval result). Always render the
 * caveats next to the numbers.
 */
export type TurCopilotPlanningComparison = {
  packName: string;
  caseCount: number;
  maxPasses: number;
  strategies: TurCopilotPlanningStrategyOutcome[];
  caveats: string[];
  error?: string | null;
};
