import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
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
 *
 * <p>T939 / §XLV.9 — the same rules also run over the <em>documentation</em>.
 * `frontend/apps/**` is the right boundary for the code and the wrong one for how
 * this bug propagated: T883 found the react-sdk README teaching
 * `data.widget.facet.map(…)` as *the* way to render facets, so the first page a
 * new consumer wrote reproduced T865 before any of their code could be scanned.
 * Only fenced code and example sources are matched — the prose around a fence is
 * exempt, because a README has to be able to say "`data.widget` is null by
 * default", which is exactly what T883 made it say.
 */

const HERE = dirname(fileURLToPath(import.meta.url));
/** `frontend/apps` — every app, not just this one: the rule is about consumers. */
const APPS_DIR = resolve(HERE, "..", "..", "..");
/** `frontend/packages` — where the READMEs and the example blocks live (T939). */
const PACKAGES_DIR = resolve(APPS_DIR, "..", "packages");

const SOURCE_EXTENSIONS = [".ts", ".tsx"];
/** T939 — an example block may be plain JS (the EDS ones are) or an HTML page. */
const DOC_EXTENSIONS = [".ts", ".tsx", ".js", ".jsx", ".mjs", ".html", ".md"];
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
 * T939 — the same escape hatch for the documentation scan, relative to
 * `frontend/packages`. Empty on purpose: a fence showing the legacy read is a
 * fence a reader will copy. Say it in prose, which this scan never sees.
 */
const ALLOWED_DOCS: readonly string[] = [];

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

function collectSources(
  dir: string,
  found: string[] = [],
  extensions: readonly string[] = SOURCE_EXTENSIONS,
): string[] {
  for (const entry of readdirSync(dir)) {
    if (SKIP_DIRECTORIES.has(entry)) continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      collectSources(full, found, extensions);
    } else if (extensions.some((ext) => entry.endsWith(ext))) {
      found.push(full);
    }
  }
  return found;
}

/**
 * T939 — everything outside a fence is blanked, line for line, so a README keeps
 * its freedom to *describe* the broken shape while the code it hands a reader is
 * held to the same rule the app source is. The fence's own delimiters go too: an
 * info string is not code.
 */
function fencedCodeOnly(markdown: string): string {
  let insideFence = false;
  return markdown
    .split("\n")
    .map((line) => {
      if (/^\s*(?:```|~~~)/.test(line)) {
        insideFence = !insideFence;
        return "";
      }
      return insideFence ? line : "";
    })
    .join("\n");
}

/**
 * T939 — a package's README plus every source file under its `examples/`. The
 * EDS blocks are plain `.js`, so the extension list is wider than the app scan's.
 */
function collectDocs(): string[] {
  const found: string[] = [];
  for (const pkg of readdirSync(PACKAGES_DIR)) {
    if (SKIP_DIRECTORIES.has(pkg)) continue;
    const readme = join(PACKAGES_DIR, pkg, "README.md");
    if (existsSync(readme)) found.push(readme);

    const examples = join(PACKAGES_DIR, pkg, "examples");
    if (existsSync(examples) && statSync(examples).isDirectory()) {
      collectSources(examples, found, DOC_EXTENSIONS);
    }
  }
  return found;
}

function scan(source: string, key: string, found: string[]): void {
  source.split("\n").forEach((line, i) => {
    for (const rule of RULES) {
      if (rule.pattern.test(line)) {
        found.push(`${key}:${i + 1} ${rule.name} — ${rule.fix}`);
      }
    }
  });
}

function violations(): string[] {
  const found: string[] = [];
  for (const file of collectSources(APPS_DIR)) {
    const key = relative(APPS_DIR, file).split(sep).join("/");
    if (key === SELF || ALLOWED.includes(key)) continue;

    scan(stripComments(readFileSync(file, "utf8")), key, found);
  }
  return found;
}

/** T939 — the same rules, over what the documentation hands a reader. */
function docViolations(): string[] {
  const found: string[] = [];
  for (const file of collectDocs()) {
    const key = relative(PACKAGES_DIR, file).split(sep).join("/");
    if (ALLOWED_DOCS.includes(key)) continue;

    const raw = readFileSync(file, "utf8");
    const code = file.endsWith(".md") ? fencedCodeOnly(raw) : raw;
    scan(stripComments(code), key, found);
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

/**
 * T939 / §XLV.9 — the boundary the code scan could not reach: T883's bug arrived
 * by README, so a fence teaching the legacy read is a bug shipped to every
 * consumer who copies it.
 */
describe("search-store boundary in the documentation (T939)", () => {
  it("no README fence or example teaches the raw search response", () => {
    expect(docViolations()).toEqual([]);
  });

  /** The scan is worthless if it finds nothing to scan. */
  it("actually reaches the READMEs and the example blocks", () => {
    const scanned = collectDocs().map((file) => relative(PACKAGES_DIR, file).split(sep).join("/"));

    expect(scanned).toContain("react-sdk/README.md");
    expect(scanned).toContain("sdk/README.md");
    expect(scanned.some((key) => key.startsWith("sdk/examples/"))).toBe(true);
  });

  it("matches the shape T883 found in the README", () => {
    const readme = ["Some prose.", "", "```tsx", "data.widget.facet.map((g) => g.name)", "```"];

    expect(scanText(fencedCodeOnly(readme.join("\n")))).toHaveLength(1);
  });

  /**
   * The point of scanning fences rather than files: the README's own explanation
   * of the trap names the broken shape in prose and in a table, and must not trip.
   */
  it("leaves the prose and tables that explain the trap alone", () => {
    const readme = [
      "So `data.widget.facet.map(…)` renders nothing by default.",
      "",
      "| `data.widget.facet` | **`null`** | the facet groups |",
      "",
      "```tsx",
      "const { facetGroups } = useTuringFacets();",
      "```",
    ];

    expect(scanText(fencedCodeOnly(readme.join("\n")))).toEqual([]);
  });
});

/** Test-local helper: the rules over one string, without touching the disk. */
function scanText(source: string): string[] {
  const found: string[] = [];
  scan(stripComments(source), "sample.md", found);
  return found;
}
