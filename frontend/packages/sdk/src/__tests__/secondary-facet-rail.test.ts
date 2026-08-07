import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

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
 * <p>That is the count `search-store-boundary.test.ts` was written at for
 * `store.data`, and this is its sibling. The rule:
 *
 * <ul>
 *   <li>a file that reads <code>structured.facets</code> must also name
 *       <code>secondaryFacets</code> — either to render it, or to fold it into
 *       whatever single list that surface shows;</li>
 *   <li>the anchor is the <code>structured</code> receiver, because
 *       <code>.facets</code> alone is a shared vocabulary: a group's own items
 *       are <code>group.facets</code>, the admin has <code>sn.facets.*</code>
 *       i18n keys, and the ANN page has an unrelated <code>response.facets</code>;</li>
 *   <li>the scan spans <code>packages/&#42;/src</code>, <code>packages/&#42;/examples</code>
 *       and <code>apps/&#42;/src</code> — every consumer, in both SDKs. This is
 *       wider than the store-boundary rule on purpose: that one is about what an
 *       *app* may read, and the SDK is exempt because resolving both shapes is
 *       its job. Here the SDK is not exempt, because it got this wrong twice.</li>
 * </ul>
 *
 * <p>Comments are stripped before matching, so a file may explain the rule
 * without satisfying it — naming `secondaryFacets` in prose is not rendering it.
 */

const HERE = dirname(fileURLToPath(import.meta.url));
/** `frontend/` — the workspace root, three levels up from `packages/sdk/src`. */
const FRONTEND = resolve(HERE, "..", "..", "..", "..");

const SOURCE_EXTENSIONS = [".ts", ".tsx", ".js", ".jsx", ".mjs"];
const SKIP_DIRECTORIES = new Set([
  "node_modules",
  "dist",
  "build",
  ".turbo",
  "coverage",
  "__tests__",
]);

/**
 * The roots that hold consumer code. `packages/&#42;/src` is in scope because both
 * SDKs read the rail themselves; `examples` is in scope because an example is
 * copied verbatim, which is how T946 happened.
 */
const SCAN_ROOTS = ["src", "examples"];

/**
 * A read of the primary rail off a structured search response. Anchored on the
 * `structured` receiver — `state.structured?.facets`, `store.structured.facets`,
 * or a bare `structured?.facets` inside a function that took one.
 */
const PRIMARY_READ = /\bstructured\s*\??\.\s*facets\b/;

/** Naming the secondary rail at all is the discharge — reading it is the point. */
const SECONDARY_READ = /\bsecondaryFacets\b/;

/**
 * Paths (relative to `frontend`, `/`-separated) exempt from the rule.
 *
 * <p>Empty on purpose, and it stayed empty when the rule was written: the one
 * surface that legitimately shows a single rail — `apps/site`'s marketing demo,
 * which caps at three groups in a horizontal bar — discharges it in code by
 * folding both rails into that capped list, rather than declaring an exception.
 * A file here would be a claim that a consumer may silently drop a rail its site
 * configured, which is the bug this rule exists to stop.
 */
const ALLOWED: readonly string[] = [];

/**
 * This file states the forbidden shape as a literal in order to prove the rule
 * matches it, so it is the one file that must not be scanned.
 */
const SELF = relative(FRONTEND, fileURLToPath(import.meta.url)).split(sep).join("/");

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

/** Every consumer source file under `frontend/packages/*` and `frontend/apps/*`. */
function consumerSources(): string[] {
  const found: string[] = [];
  for (const workspace of ["packages", "apps"]) {
    const workspaceDir = join(FRONTEND, workspace);
    for (const pkg of readdirSync(workspaceDir)) {
      if (SKIP_DIRECTORIES.has(pkg)) continue;
      for (const root of SCAN_ROOTS) {
        const dir = join(workspaceDir, pkg, root);
        try {
          if (statSync(dir).isDirectory()) collectSources(dir, found);
        } catch {
          // The root is optional: most packages have no `examples/`.
        }
      }
    }
  }
  return found;
}

function violations(): string[] {
  const found: string[] = [];
  for (const file of consumerSources()) {
    const key = relative(FRONTEND, file).split(sep).join("/");
    if (key === SELF || ALLOWED.includes(key)) continue;

    const source = stripComments(readFileSync(file, "utf8"));
    if (!PRIMARY_READ.test(source)) continue;
    if (SECONDARY_READ.test(source)) continue;

    found.push(
      `${key} reads the primary facet rail and never names \`secondaryFacets\` — ` +
        "render it as its own list, or fold it into the single list this surface shows",
    );
  }
  return found;
}

describe("secondary facet rail (T947)", () => {
  it("no consumer reads the primary rail without the secondary one", () => {
    expect(violations()).toEqual([]);
  });

  /** A guard that cannot fail is not a guard. */
  it("catches each shape the three bugs were written in", () => {
    const samples = [
      "const groups = store.structured?.facets ?? [];",
      "const facets = structured?.facets ?? [];",
      "const items = facetItemIndex(structured.facets);",
      "setFacets(s.structured?.facets ?? []);",
    ];

    for (const sample of samples) {
      expect(PRIMARY_READ.test(sample), sample).toBe(true);
      expect(SECONDARY_READ.test(sample), sample).toBe(false);
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
      expect(PRIMARY_READ.test(sample), sample).toBe(false);
    }
  });

  /** The rule is worthless if it reaches neither SDK nor the apps. */
  it("reaches both SDKs, the examples and the apps", () => {
    const scanned = consumerSources().map((file) =>
      relative(FRONTEND, file).split(sep).join("/"),
    );

    expect(scanned).toContain("packages/sdk/src/search-view.ts");
    expect(scanned).toContain("packages/react-sdk/src/hooks/use-turing-facets.ts");
    expect(scanned).toContain("packages/sdk/examples/eds/blocks/turing-search/turing-search.js");
    expect(scanned.some((key) => key.startsWith("apps/site/src/"))).toBe(true);
  });
});
