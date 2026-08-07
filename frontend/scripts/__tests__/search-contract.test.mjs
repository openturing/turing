import assert from "node:assert/strict";
import { relative, sep } from "node:path";
import test from "node:test";

import {
  ALLOWED,
  FRONTEND,
  RULES,
  SURFACE_NAMES,
  fencedCodeOnly,
  filesFor,
  violations,
} from "../search-contract-rules.mjs";

/**
 * T948 / §XLV.13 — the search-response contract, checked once over every surface
 * it applies to.
 *
 * <p>This is the workspace-wide run. The two package suites
 * (`turing-app`'s `search-store-boundary.test.ts` and `@viglet/turing-sdk`'s
 * `secondary-facet-rail.test.ts`) import the same rule set, so a developer
 * running either still sees a violation without running this file.</p>
 */

/** The scans state the forbidden shapes as literals, so they are not scanned. */
const SELF = [
  "scripts/search-contract-rules.mjs",
  "scripts/__tests__/search-contract.test.mjs",
  "apps/turing-app/src/__tests__/search-store-boundary.test.ts",
  "packages/sdk/src/__tests__/secondary-facet-rail.test.ts",
];

const skipSelf = (key) => SELF.includes(key);

test("no source or README fence breaks the search-response contract", () => {
  assert.deepEqual(violations(skipSelf), []);
});

test("every rule names at least one surface, and only known ones", () => {
  for (const rule of RULES) {
    assert.ok(rule.surfaces.length > 0, `${rule.id} names no surface`);
    for (const surface of rule.surfaces) {
      assert.ok(SURFACE_NAMES.includes(surface), `${rule.id} names unknown surface ${surface}`);
    }
  }
});

test("every rule id is unique — an id is how an exemption addresses one", () => {
  const ids = RULES.map((rule) => rule.id);
  assert.equal(new Set(ids).size, ids.length);
});

test("each rule matches the shape its bugs were written in", () => {
  const samples = {
    "widget-off-data": "const groups = store.data?.widget?.facet ?? [];",
    "data-off-store": "if (!store.data) return <SearchError />;",
    "data-destructured": "const { data } = useSearchStore();",
    "primary-rail-only": "const facets = structured?.facets ?? [];",
  };

  for (const rule of RULES) {
    const sample = samples[rule.id];
    assert.ok(sample, `no sample for ${rule.id}`);
    assert.ok(rule.pattern.test(sample), `${rule.id} misses ${sample}`);
    if (rule.requires) assert.ok(!rule.requires.test(sample), `${rule.id} discharges itself`);
  }
});

test("no rule matches the vocabulary the workspace legitimately uses", () => {
  const innocent = [
    "const { data: sites } = useSnSites();",
    'import { LiveMetricsWidget } from "@/app/console/dashboard/widgets/live-metrics.widget";',
    "{group.facets.map((facet) => (",
    't("sn.facets.title")',
    "const facets = response?.facets ?? {};",
    "const view = useSearchView();",
  ];

  for (const sample of innocent) {
    for (const rule of RULES) {
      assert.ok(!rule.pattern.test(sample), `${rule.id} matches ${sample}`);
    }
  }
});

test("a companion rule is discharged by naming what it requires", () => {
  const rule = RULES.find((r) => r.id === "primary-rail-only");
  const both = "const a = structured?.facets ?? []; const b = structured?.secondaryFacets ?? [];";

  assert.ok(rule.pattern.test(both));
  assert.ok(rule.requires.test(both));
});

test("only fenced code is read out of a README", () => {
  const readme = [
    "So `data.widget.facet.map(…)` renders nothing by default.",
    "",
    "| `data.widget.facet` | **throws** | the facet groups |",
    "",
    "```tsx",
    "const { facetGroups } = useTuringFacets();",
    "```",
  ].join("\n");

  const code = fencedCodeOnly(readme);
  assert.ok(!/data\s*\??\.\s*widget/.test(code), "prose and tables must survive the scan");
  assert.ok(code.includes("useTuringFacets"), "the fence itself must be read");
});

test("every surface resolves to files that exist", () => {
  for (const surface of SURFACE_NAMES) {
    assert.ok(filesFor(surface).length > 0, `${surface} matched nothing`);
  }
});

test("the scan reaches both SDKs, the examples, the apps and the READMEs", () => {
  const seen = new Set();
  for (const surface of SURFACE_NAMES) {
    for (const file of filesFor(surface)) {
      seen.add(relative(FRONTEND, file).split(sep).join("/"));
    }
  }

  assert.ok(seen.has("packages/sdk/src/search-view.ts"));
  assert.ok(seen.has("packages/react-sdk/src/hooks/use-turing-facets.ts"));
  assert.ok(seen.has("packages/sdk/examples/eds/blocks/turing-search/turing-search.js"));
  assert.ok(seen.has("packages/react-sdk/README.md"));
  assert.ok([...seen].some((key) => key.startsWith("apps/site/src/")));
});

test("the exemption list stays empty, and each entry names a real rule", () => {
  for (const entry of ALLOWED) {
    const [id] = entry.split(" ");
    assert.ok(
      RULES.some((rule) => rule.id === id),
      `exemption names unknown rule: ${id}`,
    );
  }
});
