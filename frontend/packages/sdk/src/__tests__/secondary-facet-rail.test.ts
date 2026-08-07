import { describe, expect, it } from "vitest";

// @ts-expect-error — a zero-dependency .mjs rule set shared with the workspace
// scripts suite and with turing-app's boundary test; it ships no types by design.
import { RULES, violations } from "../../../../scripts/search-contract-rules.mjs";

/**
 * T947 / §XLV.12 — the two-rail read, as a rule rather than a habit.
 *
 * <p>A search response carries facet groups on **two** properties: `facets` and
 * `secondaryFacets`. Three tasks have each fixed one place that enumerated the
 * first and stopped — T884 the co-browse lookup, T936 the React facet hook, T946
 * the EDS reference block — and each looked locally reasonable, because the
 * secondary rail is empty on most sites and a consumer that drops it looks
 * correct until someone configures one. Nothing failed; the rail was simply not
 * there.
 *
 * <p>T948 / §XLV.13 — the rule itself now lives in
 * `frontend/scripts/search-contract-rules.mjs`, next to the store-boundary rules
 * that used to be a separate scan with a separate surface list. This file is the
 * SDK's entry point into it: the whole contract is checked here, so
 * `pnpm --filter @viglet/turing-sdk test` fails on a violation without anyone
 * running the workspace suite. The surfaces stay per rule — the SDK is exempt
 * from the store-boundary rules because resolving both shapes is its job, and is
 * not exempt from this one, because it got this one wrong twice.
 */

/** The rule set states the forbidden shapes as literals, so it is not scanned. */
const SELF = [
  "scripts/search-contract-rules.mjs",
  "scripts/__tests__/search-contract.test.mjs",
  "apps/turing-app/src/__tests__/search-store-boundary.test.ts",
  "packages/sdk/src/__tests__/secondary-facet-rail.test.ts",
];

interface Rule {
  readonly id: string;
  readonly pattern: RegExp;
  readonly requires?: RegExp;
}

const rules = RULES as Rule[];
const twoRail = rules.find((rule) => rule.id === "primary-rail-only")!;

describe("search-response contract (T947, T948)", () => {
  it("no consumer, example or README fence breaks it", () => {
    expect(violations((key: string) => SELF.includes(key))).toEqual([]);
  });

  /** A guard that cannot fail is not a guard. */
  it("catches each shape the three two-rail bugs were written in", () => {
    const samples = [
      "const groups = store.structured?.facets ?? [];",
      "const facets = structured?.facets ?? [];",
      "const items = facetItemIndex(structured.facets);",
      "setFacets(s.structured?.facets ?? []);",
    ];

    for (const sample of samples) {
      expect(twoRail.pattern.test(sample), sample).toBe(true);
      expect(twoRail.requires!.test(sample), sample).toBe(false);
    }
  });

  /** …and that it leaves the `facets` vocabulary the workspace legitimately uses. */
  it("leaves the unrelated `facets` names alone", () => {
    const innocent = [
      "{group.facets.map((facet) => (",
      't("sn.facets.title")',
      "const facets = response?.facets ?? {};",
      "(widget?.facetToRemove?.facets ?? []).map((facet) => ({",
    ];

    for (const sample of innocent) {
      expect(twoRail.pattern.test(sample), sample).toBe(false);
    }
  });

  /** Naming the secondary rail discharges the rule — folding it into one list counts. */
  it("is discharged by naming the secondary rail", () => {
    const both =
      "const a = structured?.facets ?? []; const b = structured?.secondaryFacets ?? [];";

    expect(twoRail.pattern.test(both)).toBe(true);
    expect(twoRail.requires!.test(both)).toBe(true);
  });
});
