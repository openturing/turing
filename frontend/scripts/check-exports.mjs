#!/usr/bin/env node
/**
 * T1006 — the public export surface, checked against the built output and
 * against the version it is about to be published under.
 *
 * Run from a package directory:
 *   node ../../scripts/check-exports.mjs
 *   node ../../scripts/check-exports.mjs --update-baseline
 *   node ../../scripts/check-exports.mjs --json
 *
 * ## What went wrong, and which of the two guards catches it
 *
 * `@viglet/turing-sdk` set `version: 2026.3.7` on 2026-07-13. `postSearch` and
 * the T710 params bridges landed on 2026-08-02. So the checkout grew a public
 * surface three weeks after the version that names it was already taken, and a
 * consumer installing 2026.3.7 got a package whose `dist` genuinely did not
 * contain the symbols the source and the docs both advertised. Building a
 * faceted search against it meant hand-writing the facet toggling and the params
 * bridge that the package's own comment says must not be duplicated.
 *
 * The obvious check — "is every exported symbol in the built dist?" — would have
 * passed the whole time, because the build was never the problem. It is guard A
 * below and it is still worth having, since a build that silently drops an entry
 * point is a real failure mode; it just is not *this* one.
 *
 * Guard B is the one that catches it, and what it asserts is *not* "the version
 * must differ". This monorepo releases with changesets, so a feature commit is
 * not supposed to bump anything: it declares the bump in `.changeset/*.md` and
 * `changeset version` applies it later. T709 and T710 did exactly that — both
 * changesets exist and both say `"@viglet/turing-sdk": minor`. Nothing was
 * forgotten at authoring time; the release simply never ran, and the surface sat
 * in the checkout under a version already taken.
 *
 * So guard B asks the question that actually distinguishes healthy from broken:
 * the surface has grown past the last recorded release — is that growth *claimed*
 * by a pending changeset? If yes, the release will bump it and this is a normal
 * unreleased state, reported and allowed. If no, the growth would publish under a
 * spoken-for version, and that fails.
 *
 * The baseline is therefore the surface of the last *released* version, not of
 * the working tree, and `--update-baseline` is meant to be run when a release
 * happens rather than when a feature lands.
 *
 * package.json → "exportsSurface":
 *   {
 *     "entry":    "dist/index.d.ts",   // the built declaration to read
 *     "source":   "src/index.ts",      // the source barrel, for guard A
 *     "baseline": {                    // written with --update-baseline
 *       "version":  "2026.3.7",
 *       "recorded": "2026-08-23",
 *       "symbols":  ["applyFacetAction", "..."]
 *     }
 *   }
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const asJson = process.argv.includes("--json");
const updateBaseline = process.argv.includes("--update-baseline");

const pkgPath = resolve("package.json");
if (!existsSync(pkgPath)) {
  console.error("check-exports: no package.json here — run it from a package directory.");
  process.exit(1);
}
const pkg = JSON.parse(readFileSync(pkgPath, "utf8"));
const cfg = pkg.exportsSurface;
if (!cfg) {
  console.log(`check-exports: ${pkg.name} declares no "exportsSurface" — nothing to check.`);
  process.exit(0);
}

const entryPath = resolve(cfg.entry ?? "dist/index.d.ts");
const sourcePath = resolve(cfg.source ?? "src/index.ts");

/**
 * Every name a barrel re-exports.
 *
 * Both forms `export { A, B } from "./x"` and `export type { A } from "./x"` are
 * collected, and `A as B` records `B` — the name a consumer writes is the one
 * that matters. A bare `export * from` cannot be resolved by reading one file,
 * so it is reported rather than silently treated as nothing.
 */
function surfaceOf(file) {
  const text = readFileSync(file, "utf8");
  const stars = /^[ \t]*export[ \t]+\*[ \t]+from/m.test(text);
  const names = new Set();
  // `[^}]*` rather than a lazy `[\s\S]*?`: an export clause cannot contain a
  // closing brace, and the lazy form backtracks super-linearly on a long barrel.
  for (const match of text.matchAll(/^[ \t]*export[ \t]+(?:type[ \t]+)?\{([^}]*)\}[\s]*from[\s]*["']/gm)) {
    for (const clause of match[1].split(",")) {
      const raw = clause.trim();
      if (!raw) continue;
      const name = raw.includes(" as ") ? raw.split(" as ").pop().trim() : raw;
      if (/^[A-Za-z_$][\w$]*$/.test(name)) names.add(name);
    }
  }
  // A local `export const X` / `export function X` in the barrel itself.
  for (const match of text.matchAll(
    /^[ \t]*export[ \t]+(?:declare[ \t]+)?(?:const|let|var|function\*?|class|type|interface|enum)[ \t]+([A-Za-z_$][\w$]*)/gm,
  )) {
    names.add(match[1]);
  }
  return { names, stars };
}

if (!existsSync(entryPath)) {
  console.error(
    `check-exports: ${cfg.entry ?? "dist/index.d.ts"} is missing — build the package first ` +
      "(the declaration emit is the half that writes it).",
  );
  process.exit(1);
}

const built = surfaceOf(entryPath);
const source = existsSync(sourcePath) ? surfaceOf(sourcePath) : null;
const baseline = cfg.baseline;
const symbols = [...built.names].sort((a, b) => a.localeCompare(b));

// ── Guard A: the build carries everything the source exports ──
const missingFromBuild = source
  ? [...source.names].filter((n) => !built.names.has(n)).sort((a, b) => a.localeCompare(b))
  : [];

// ── Guard B: growth past the last release is claimed by a pending changeset ──
const baseSymbols = baseline?.symbols ?? null;
const added = baseSymbols ? symbols.filter((n) => !baseSymbols.includes(n)) : [];
const removed = baseSymbols ? baseSymbols.filter((n) => !built.names.has(n)) : [];
const surfaceChanged = added.length > 0 || removed.length > 0;
const versionUnchanged = baseline?.version === pkg.version;

/**
 * Whether a pending changeset declares a bump for this package.
 *
 * A changeset is front-matter naming packages and bump levels, so the name
 * appearing in the front-matter of any `.changeset/*.md` is the whole question.
 * Read positionally rather than parsed as YAML: this repository's scripts are
 * zero-dependency by rule, and the shape changesets writes is stable.
 */
function pendingBumpFor(name) {
  const dir = resolve(cfg.changesetDir ?? "../../.changeset");
  if (!existsSync(dir)) return null;
  for (const file of readdirSync(dir)) {
    if (!file.endsWith(".md") || file.toLowerCase() === "readme.md") continue;
    const text = readFileSync(resolve(dir, file), "utf8");
    const front = /^---\r?\n([\s\S]*?)\r?\n---/.exec(text);
    if (!front) continue;
    if (front[1].includes(`"${name}"`)) return file;
  }
  return null;
}

const pendingChangeset = surfaceChanged && versionUnchanged
  ? pendingBumpFor(pkg.name)
  : null;

if (asJson) {
  console.log(JSON.stringify({
    package: pkg.name,
    version: pkg.version,
    count: symbols.length,
    missingFromBuild,
    added,
    removed,
    versionUnchanged,
  }, null, 2));
}

if (updateBaseline) {
  if (surfaceChanged && versionUnchanged) {
    console.error(
      `\ncheck-exports: refusing to record a changed surface under ${pkg.version}, which is ` +
        `the version the current baseline was taken at.\n\n` +
        "  Bump the version first. Recording it here would describe a published version as\n" +
        "  containing symbols that version does not have — which is the defect this checks for.\n",
    );
    process.exit(1);
  }
  pkg.exportsSurface = {
    ...cfg,
    baseline: {
      version: pkg.version,
      recorded: new Date().toISOString().slice(0, 10),
      symbols,
    },
  };
  writeFileSync(pkgPath, `${JSON.stringify(pkg, null, 2)}\n`);
  console.log(
    `\ncheck-exports: baseline written for ${pkg.name}@${pkg.version} ` +
      `(${symbols.length} symbols) — commit it with the change that caused it.\n`,
  );
  process.exit(0);
}

const problems = [];
if (built.stars) {
  problems.push(
    `  ${cfg.entry ?? "dist/index.d.ts"} contains an \`export * from\`, which this check cannot ` +
      "resolve.\n    Name the symbols explicitly, or the surface below is understated.",
  );
}
for (const name of missingFromBuild) {
  problems.push(
    `  ${name} is exported by ${cfg.source ?? "src/index.ts"} and absent from the build.\n` +
      "    A consumer importing it gets a module that does not resolve.",
  );
}
const listOf = (label, xs) => (xs.length > 0 ? `\n    ${label}: ${xs.join(", ")}` : "");
if (surfaceChanged && versionUnchanged && !pendingChangeset) {
  problems.push(
    `  the surface has grown past ${baseline.version} and no pending changeset bumps ${pkg.name}.` +
      listOf("added", added) +
      listOf("removed", removed) +
      "\n\n    Write a changeset naming this package, or the next publish puts these symbols under\n" +
      "    a version that is already taken — which is what left a consumer of 2026.3.7 importing\n" +
      "    `postSearch` from a package whose dist did not contain it.",
  );
}

if (!asJson) {
  console.log(`\n  ${pkg.name}@${pkg.version} — public export surface\n`);
  console.log(`    symbols        ${symbols.length}`);
  console.log(
    `    baseline       ${baseline ? `${baseline.version} (${baseline.recorded})` : "none recorded"}`,
  );
  console.log("");
}

if (problems.length === 0) {
  if (!asJson) {
    if (pendingChangeset) {
      // Not a warning. This is the normal state between a feature landing and a
      // release running, and saying so is the point — the failure this check
      // exists for looks identical until you ask whether a changeset claims it.
      console.log(
        `  ✓ ${added.length} unreleased symbol(s) past ${baseline.version}, claimed by ` +
          `.changeset/${pendingChangeset}\n`,
      );
    } else {
      console.log("  ✓ the build carries the whole source surface, and it matches the last release\n");
    }
  }
  process.exit(0);
}

console.error("  export surface rejected:");
for (const problem of problems) console.error(problem);
console.error("");
process.exit(1);
