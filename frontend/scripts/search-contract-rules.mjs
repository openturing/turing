/**
 * The search-response contract, as one set of named rules (T948).
 *
 * <p>Two guards grew separately. T885/T939 walked app source and then README
 * fences for the shapes T865–T867 shipped — a `widget` read off `data`, a
 * `store.data` read, a `data` destructured out of the store. T947 walked package
 * and app source for the two-rail read that T884, T936 and T946 each fixed once.
 * Each was right about its own surface, and the two never crossed: the react-sdk
 * README kept teaching `structured.facets` alone — the read T947 fails a build
 * over, in the place T939 exists because a reader copies.</p>
 *
 * <p>Neither guard was wrong. The rule and the surface it applies to were paired
 * by hand, in whichever file the regex happened to be typed into, so a new rule
 * covers whatever half its author remembered. Here the pairing is a field.
 * Adding a rule is a list entry; adding a surface to an existing rule is one
 * more string.</p>
 *
 * <p>The surfaces genuinely differ and must stay explicit. The SDK is exempt
 * from {@link STORE_BOUNDARY} because resolving both response shapes is its job,
 * and is <em>not</em> exempt from {@link TWO_RAIL}, because it got that one wrong
 * twice. Encoding that per rule is the point; inferring it would be a guess.</p>
 *
 * Zero-dependency by house rule — `node --test` runs it directly, and the two
 * package suites import it so a developer running either still sees a violation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.7
 */
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

/** `frontend/` — this file lives in `frontend/scripts`. */
export const FRONTEND = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const SKIP_DIRECTORIES = new Set([
  "node_modules",
  "dist",
  "build",
  ".turbo",
  "coverage",
]);

/**
 * A place a rule is checked. `roots` are `<workspace>/<package>/<root>` segments
 * expanded over every package in the workspace; `kind` says how a file is read.
 *
 * - `source` — the file is code, and comments are stripped before matching, so a
 *   file may explain a rule without tripping it (several do, citing bug numbers).
 * - `fences` — the file is markdown and only its fenced code is read. The prose
 *   around a fence is exempt: a README has to be able to say "`data.widget` is
 *   null by default", which is exactly what T883 made it say.
 */
const SURFACES = {
  appSource: { workspace: "apps", roots: ["src"], kind: "source" },
  packageSource: { workspace: "packages", roots: ["src"], kind: "source" },
  examples: { workspace: "packages", roots: ["examples"], kind: "source" },
  packageReadmes: { workspace: "packages", roots: ["."], kind: "fences" },
};

export const SURFACE_NAMES = Object.keys(SURFACES);

const SOURCE_EXTENSIONS = [".ts", ".tsx", ".js", ".jsx", ".mjs"];
const FENCE_EXTENSIONS = [".md"];

/**
 * The rules. Each states a `pattern` that must not appear — or, with `requires`,
 * a pattern that must appear alongside it — the `fix` a violator is told, and the
 * `surfaces` it is checked on.
 */
export const RULES = [
  {
    id: "widget-off-data",
    name: "reads `widget` off a search response",
    // `widget` is legacy-only: the link-free response has no such property, so
    // any `data.widget` / `data?.widget` is a read that returns undefined in the
    // default mode. Anchored on the `data` receiver so the app's own dashboard
    // widgets (an unrelated `widget` vocabulary) are not matched.
    pattern: /\bdata\s*\??\.\s*widget\b/,
    fix: "read the projection (`useSearchView()`) — the link-free response has no `widget`",
    surfaces: ["appSource", "examples", "packageReadmes"],
  },
  {
    id: "data-off-store",
    name: "reads `data` off a search store",
    // The conventional receiver names for the store in this codebase. A
    // react-query `data` is destructured, never reached through one of these.
    pattern: /\b(?:searchStore|store|turing)\s*\??\.\s*data\b/,
    fix: "read the projection (`useSearchView()`) — `store.data` is null POST-native",
    surfaces: ["appSource", "examples", "packageReadmes"],
  },
  {
    id: "data-destructured",
    name: "destructures `data` out of the search store",
    pattern: /\{[^}]*\bdata\b[^}]*\}\s*=\s*(?:useSearchStore|useTuringUrlSearch)\s*\(/,
    fix: "call `useSearchView()` instead of destructuring the store",
    surfaces: ["appSource", "examples", "packageReadmes"],
  },
  {
    id: "primary-rail-only",
    name: "reads the primary facet rail and never names `secondaryFacets`",
    // Anchored on the `structured` receiver, because `.facets` alone is a shared
    // vocabulary: a group's own items are `group.facets`, the admin has
    // `sn.facets.*` i18n keys, and the ANN page has an unrelated
    // `response.facets`.
    pattern: /\bstructured\s*\??\.\s*facets\b/,
    // Naming the secondary rail discharges the rule — rendering it is the point,
    // and folding it into a single capped list is a legitimate way to do that.
    requires: /\bsecondaryFacets\b/,
    fix: "render `secondaryFacets` as its own list, or fold it into the single list this surface shows",
    // Wider than the store-boundary rules: those exempt the SDK because
    // resolving both shapes is its job, and this one does not.
    surfaces: ["appSource", "packageSource", "examples", "packageReadmes"],
  },
];

/**
 * Paths (relative to `frontend`, `/`-separated) exempt from a given rule, as
 * `"<rule id> <path>"`.
 *
 * <p>Empty on purpose. An entry is a claim that a file legitimately needs the
 * shape its rule forbids, and each rule has so far been dischargeable in code —
 * the marketing demo, the one surface that shows a single facet rail, folds both
 * into its capped list rather than sitting here.</p>
 */
export const ALLOWED = [];

/** Strips block and line comments, preserving line numbers. */
function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, (block) => "\n".repeat((block.match(/\n/g) ?? []).length))
    .replace(/\/\/[^\n]*/g, " ");
}

/**
 * Blanks everything outside a fence, line for line, and the delimiters with it:
 * an info string is not code.
 */
export function fencedCodeOnly(markdown) {
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

function collectFiles(dir, extensions, found, recurse) {
  for (const entry of readdirSync(dir)) {
    if (SKIP_DIRECTORIES.has(entry)) continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      if (recurse) collectFiles(full, extensions, found, recurse);
    } else if (extensions.some((ext) => entry.endsWith(ext))) {
      found.push(full);
    }
  }
  return found;
}

/** Every file a surface covers, as absolute paths. */
export function filesFor(surfaceName) {
  const surface = SURFACES[surfaceName];
  if (!surface) throw new Error(`unknown surface: ${surfaceName}`);
  const found = [];
  const workspaceDir = join(FRONTEND, surface.workspace);

  for (const pkg of readdirSync(workspaceDir)) {
    if (SKIP_DIRECTORIES.has(pkg)) continue;
    for (const root of surface.roots) {
      const dir = root === "." ? join(workspaceDir, pkg) : join(workspaceDir, pkg, root);
      if (!existsSync(dir) || !statSync(dir).isDirectory()) continue;
      // A README is one file at the package root, not a tree to walk — recursing
      // there would pull in every markdown file the package ships.
      collectFiles(
        dir,
        surface.kind === "fences" ? FENCE_EXTENSIONS : SOURCE_EXTENSIONS,
        found,
        root !== ".",
      );
    }
  }
  return found;
}

function readForRule(file, kind) {
  const raw = readFileSync(file, "utf8");
  return stripComments(kind === "fences" ? fencedCodeOnly(raw) : raw);
}

/**
 * Every violation of every rule, as reportable strings.
 *
 * @param {(path: string) => boolean} [skip] files to leave out — the caller's own
 *   test file states the forbidden shapes as literals to prove the rules match.
 */
export function violations(skip = () => false) {
  const found = [];
  for (const rule of RULES) {
    for (const surfaceName of rule.surfaces) {
      const kind = SURFACES[surfaceName].kind;
      for (const file of filesFor(surfaceName)) {
        const key = relative(FRONTEND, file).split(sep).join("/");
        if (skip(key) || ALLOWED.includes(`${rule.id} ${key}`)) continue;

        const source = readForRule(file, kind);
        if (rule.requires) {
          // A companion rule: the shape is fine, alone it is not.
          if (rule.pattern.test(source) && !rule.requires.test(source)) {
            found.push(`${key} ${rule.name} — ${rule.fix}`);
          }
          continue;
        }
        source.split("\n").forEach((line, i) => {
          if (rule.pattern.test(line)) {
            found.push(`${key}:${i + 1} ${rule.name} — ${rule.fix}`);
          }
        });
      }
    }
  }
  return found;
}
