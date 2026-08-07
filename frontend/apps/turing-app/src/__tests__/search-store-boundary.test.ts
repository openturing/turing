import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

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
 *       what resolves them — so this scans `frontend/apps/**` only, not
 *       `frontend/packages/**`;</li>
 *   <li>the legacy fallback <em>inside</em> the projection stays allowed, and now
 *       lives in `@viglet/turing-sdk`, outside this scan entirely.</li>
 * </ul>
 *
 * <p>Comments are stripped before matching, so a file may still explain the rule
 * (several do, citing the bugs by number) without tripping it.
 */

const HERE = dirname(fileURLToPath(import.meta.url));
/** `frontend/apps` — every app, not just this one: the rule is about consumers. */
const APPS_DIR = resolve(HERE, "..", "..", "..");

const SOURCE_EXTENSIONS = [".ts", ".tsx"];
const SKIP_DIRECTORIES = new Set(["node_modules", "dist", "build", ".turbo", "coverage"]);

/**
 * Paths (relative to `frontend/apps`, `/`-separated) exempt from the rule.
 *
 * <p>Empty on purpose. Adding an entry is a claim that a file legitimately needs
 * the raw legacy response — which, since T881 moved the projection into the SDK,
 * nothing in an app does. Prefer extending `SearchView` over adding a line here.
 */
const ALLOWED: readonly string[] = [];

/**
 * This file states the forbidden shapes as literal samples in order to prove the
 * rules match them, so it is the one file that must not be scanned.
 */
const SELF = relative(APPS_DIR, fileURLToPath(import.meta.url)).split(sep).join("/");

interface Rule {
  readonly name: string;
  readonly pattern: RegExp;
  readonly fix: string;
}

const RULES: readonly Rule[] = [
  {
    name: "reads `widget` off a search response",
    // `widget` is legacy-only: the link-free response has no such property, so
    // any `data.widget` / `data?.widget` is a read that returns undefined in the
    // default mode. Anchored on the `data` receiver so the app's own dashboard
    // widgets (an unrelated `widget` vocabulary) are not matched.
    pattern: /\bdata\s*\??\.\s*widget\b/,
    fix: "read the projection (`useSearchView()`) — the link-free response has no `widget`",
  },
  {
    name: "reads `data` off a search store",
    // The conventional receiver names for the store in this codebase. A
    // react-query `data` is destructured, never reached through one of these.
    pattern: /\b(?:searchStore|store|turing)\s*\??\.\s*data\b/,
    fix: "read the projection (`useSearchView()`) — `store.data` is null POST-native",
  },
  {
    name: "destructures `data` out of the search store",
    pattern: /\{[^}]*\bdata\b[^}]*\}\s*=\s*(?:useSearchStore|useTuringUrlSearch)\s*\(/,
    fix: "call `useSearchView()` instead of destructuring the store",
  },
];

/**
 * Strips block and line comments so prose about the rule cannot violate it. A
 * block comment is replaced by its own newlines rather than by a space, so the
 * line numbers this test reports still point at the real source line.
 */
function stripComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, (block) => "\n".repeat((block.match(/\n/g) ?? []).length))
    .replace(/\/\/[^\n]*/g, " ");
}

function collectSources(dir: string, found: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    if (SKIP_DIRECTORIES.has(entry)) continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      collectSources(full, found);
    } else if (SOURCE_EXTENSIONS.some((ext) => entry.endsWith(ext))) {
      found.push(full);
    }
  }
  return found;
}

function violations(): string[] {
  const found: string[] = [];
  for (const file of collectSources(APPS_DIR)) {
    const key = relative(APPS_DIR, file).split(sep).join("/");
    if (key === SELF || ALLOWED.includes(key)) continue;

    const lines = stripComments(readFileSync(file, "utf8")).split("\n");
    lines.forEach((line, i) => {
      for (const rule of RULES) {
        if (rule.pattern.test(line)) {
          found.push(`${key}:${i + 1} ${rule.name} — ${rule.fix}`);
        }
      }
    });
  }
  return found;
}

describe("search-store boundary (T885)", () => {
  it("no app reads the raw search response outside the projection", () => {
    expect(violations()).toEqual([]);
  });

  /**
   * A guard that cannot fail is not a guard. This asserts the rules actually
   * match the three shapes T865–T867 shipped.
   */
  it("catches each shape the three bugs were written in", () => {
    const samples = [
      'const groups = store.data?.widget?.facet ?? [];',
      'if (!store.data) return <SearchError />;',
      'const { data } = useSearchStore();',
      'const turing = useTuringUrlSearch(); return turing.data.widget.facet;',
    ];

    for (const sample of samples) {
      expect(RULES.some((rule) => rule.pattern.test(sample)), sample).toBe(true);
    }
  });

  /** …and that it does not match the vocabulary the app legitimately uses. */
  it("leaves unrelated `data` and `widget` names alone", () => {
    const innocent = [
      'const { data: sites } = useSnSites();',
      'import { LiveMetricsWidget } from "@/app/console/dashboard/widgets/live-metrics.widget";',
      't("dashboard.widgets.snSites.title")',
      'const view = useSearchView();',
      'const { facetGroups } = useTuringFacets();',
    ];

    for (const sample of innocent) {
      expect(RULES.some((rule) => rule.pattern.test(sample)), sample).toBe(false);
    }
  });
});
