import { describe, expect, it } from "vitest";

// @ts-expect-error — a zero-dependency .mjs rule set shared with the workspace
// scripts suite and with @viglet/turing-sdk's contract test; no types by design.
import { RULES, fencedCodeOnly, violations } from "../../../../scripts/search-contract-rules.mjs";

/**
 * T885 / §LXX.7 — the mistake T865–T867 were, as a rule rather than a habit.
 *
 * <p>Those three bugs were one mistake made three times, in three files, by three
 * edits that each looked locally reasonable: read `store.data`, reach into
 * `widget`, ship. T868 made *that page* catchable. It did not make the *class*
 * catchable — a new component can read `store.data?.widget?.…` today, pass every
 * suite, and be broken in the only mode that ships, because the value is
 * legitimately non-null on the legacy transport and no type distinguishes the two.
 *
 * <p>This is the frontend counterpart of `TurDomainLayerConventionsTest`: an
 * architectural rule asserted as a test. **The boundary, stated precisely:**
 *
 * <ul>
 *   <li>an <em>app</em> reads a search response through the projection
 *       (`useSearchView` / `toSearchView`) or through a derived hook
 *       (`useTuringFacets`, `useTuringPagination`, …), never off the store;</li>
 *   <li>the <em>SDK</em> reads both `data` and `structured` on purpose — it is
 *       what resolves them — so the store-boundary rules name app source, not
 *       `packages/&#42;/src`;</li>
 *   <li>the legacy fallback <em>inside</em> the projection stays allowed, and
 *       lives in `@viglet/turing-sdk`, outside those rules' surfaces entirely.</li>
 * </ul>
 *
 * <p>T939 — the same rules also run over the <em>documentation</em>.
 * `frontend/apps/**` is the right boundary for the code and the wrong one for how
 * this bug propagated: T883 found the react-sdk README teaching
 * `data.widget.facet.map(…)` as *the* way to render facets, so the first page a
 * new consumer wrote reproduced T865 before any of their code could be scanned.
 * Only fenced code and example sources are matched — the prose around a fence is
 * exempt, because a README has to be able to say "`data.widget` is null by
 * default", which is exactly what T883 made it say.
 *
 * <p>T948 / §XLV.13 — the rules and the surfaces they apply to now live together
 * in `frontend/scripts/search-contract-rules.mjs`, because pairing them by hand
 * per scan is what left the two-rail rule off the README surface it most needed.
 * This file stays the app's entry point into them, so
 * `pnpm --filter turing-app test` still fails on a violation.
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
const storeBoundary = rules.filter((rule) => rule.id !== "primary-rail-only");

describe("search-store boundary (T885, T939, T948)", () => {
  it("no app, example or README fence reads the raw search response", () => {
    expect(violations((key: string) => SELF.includes(key))).toEqual([]);
  });

  /**
   * A guard that cannot fail is not a guard. This asserts the rules actually
   * match the three shapes T865–T867 shipped.
   */
  it("catches each shape the three bugs were written in", () => {
    const samples = [
      "const groups = store.data?.widget?.facet ?? [];",
      "if (!store.data) return <SearchError />;",
      "const { data } = useSearchStore();",
      "const turing = useTuringUrlSearch(); return turing.data.widget.facet;",
    ];

    for (const sample of samples) {
      expect(storeBoundary.some((rule) => rule.pattern.test(sample)), sample).toBe(true);
    }
  });

  /** …and that it does not match the vocabulary the app legitimately uses. */
  it("leaves unrelated `data` and `widget` names alone", () => {
    const innocent = [
      "const { data: sites } = useSnSites();",
      'import { LiveMetricsWidget } from "@/app/console/dashboard/widgets/live-metrics.widget";',
      't("dashboard.widgets.snSites.title")',
      "const view = useSearchView();",
      "const { facetGroups } = useTuringFacets();",
    ];

    for (const sample of innocent) {
      expect(storeBoundary.some((rule) => rule.pattern.test(sample)), sample).toBe(false);
    }
  });

  /**
   * The point of scanning fences rather than files: the README's own explanation
   * of the trap names the broken shape in prose and in a table, and must not trip.
   */
  it("leaves the prose and tables that explain the trap alone", () => {
    const readme = [
      "So `data.widget.facet.map(…)` renders nothing by default.",
      "",
      "| `data.widget.facet` | **throws** | the facet groups |",
      "",
      "```tsx",
      "const { facetGroups } = useTuringFacets();",
      "```",
    ].join("\n");

    const code = fencedCodeOnly(readme) as string;

    for (const rule of storeBoundary) {
      expect(rule.pattern.test(code), rule.id).toBe(false);
    }
    expect(code).toContain("useTuringFacets");
  });
});
