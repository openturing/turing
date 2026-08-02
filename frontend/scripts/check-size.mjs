#!/usr/bin/env node
/**
 * Shared, zero-dependency bundle-size budget for the publishable frontend
 * packages (T303 packaging hardening, generalized; T863 delta guard).
 *
 * One script, per-package config. Run from a package directory:
 *   node ../../scripts/check-size.mjs
 *   node ../../scripts/check-size.mjs --update-baseline
 * It reads a `sizeBudget` block from that package's package.json, gzip-compresses
 * the matching `dist` entries, and fails if a file grew more than the allowed
 * delta over its committed baseline (or, where configured, exceeded an absolute
 * ceiling). The point is to make size regressions a conscious decision, not a
 * surprise.
 *
 * ## Two guards, and why there are two (T863)
 *
 * An **absolute ceiling** has to be re-tuned by hand every time the package
 * legitimately grows, and each re-tune is indistinguishable from silencing the
 * guard — worse, it rots silently in the other direction too: a bundle can sit
 * *over* a stale ceiling for months and only be discovered by whoever next tries
 * to publish. So the primary guard is the **delta**: commit a measured baseline
 * and fail when a change adds more than a threshold on top of it. Legitimate
 * growth updates the baseline (`--update-baseline`) in the same PR that causes
 * it, where a reviewer sees the cost next to the feature.
 *
 * The absolute `entries` / `total` numbers remain supported as a coarse backstop
 * for packages that have not adopted a baseline yet.
 *
 * package.json → "sizeBudget":
 *   {
 *     "dir":        "dist",            // scanned directory (default "dist")
 *     "extensions": [".js"],           // file suffixes to weigh (default [".js"])
 *     "exclude":    ["stories/"],      // posix substrings to skip (default [])
 *     "entries":    { "index.js": 800 }, // absolute per-file gzip ceilings, bytes
 *     "total":      14000,             // absolute gzip ceiling over all matched files
 *     "strict":     true,              // every matched file MUST be covered by a
 *                                      // budget or a baseline
 *     "delta":      { "entry": 512, "total": 1024 }, // allowed growth over baseline
 *     "baseline":   {                  // measured — write with --update-baseline
 *       "measured": "2026-08-02",
 *       "total":    33168,
 *       "entries":  { "index.js": 780 }
 *     }
 *   }
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { gzipSync } from "node:zlib";
import { join, relative, sep } from "node:path";

const pkgDir = process.cwd();
const updateBaseline = process.argv.includes("--update-baseline");

/** Growth tolerated over a committed baseline when the package sets none. */
const DEFAULT_DELTA = { entry: 512, total: 1024 };

function fail(msg) {
  console.error(`\n  size budget: ${msg}\n`);
  process.exit(1);
}

const pkgPath = join(pkgDir, "package.json");
let pkgRaw;
let pkg;
try {
  pkgRaw = readFileSync(pkgPath, "utf8");
  pkg = JSON.parse(pkgRaw);
} catch {
  fail(`no readable package.json in ${pkgDir}`);
}

const cfg = pkg.sizeBudget;
if (!cfg) fail(`package "${pkg.name}" has no "sizeBudget" block in package.json`);

const label = cfg.label ?? pkg.name;
const dir = join(pkgDir, cfg.dir ?? "dist");
const extensions = cfg.extensions ?? [".js"];
const exclude = cfg.exclude ?? [];
const entries = cfg.entries ?? {};
const total = cfg.total;
const strict = cfg.strict ?? false;
const baseline = cfg.baseline;
const baseEntries = baseline?.entries ?? {};
const delta = { ...DEFAULT_DELTA, ...cfg.delta };

function walk(d) {
  const out = [];
  let names;
  try {
    names = readdirSync(d, { withFileTypes: true });
  } catch {
    fail(`directory not found: ${d} — run \`npm run build\` first.`);
  }
  for (const e of names) {
    const full = join(d, e.name);
    if (e.isDirectory()) out.push(...walk(full));
    else out.push(full);
  }
  return out;
}

const toPosix = (p) => p.split(sep).join("/");

const matched = walk(dir).filter((f) => {
  if (f.endsWith(".map")) return false;
  if (!extensions.some((ext) => f.endsWith(ext))) return false;
  const rel = toPosix(relative(dir, f));
  return !exclude.some((x) => rel.includes(x));
});

if (matched.length === 0) fail(`no entries matched in ${dir} — did the build run?`);

const kb = (n) => `${(n / 1024).toFixed(2)} kB`;
const signed = (n) => `${n >= 0 ? "+" : "−"}${kb(Math.abs(n))}`;
const rows = [];
const violations = [];
let totalGzip = 0;

for (const f of matched.sort()) {
  const rel = toPosix(relative(dir, f));
  const gzip = gzipSync(readFileSync(f), { level: 9 }).length;
  totalGzip += gzip;

  const budget = entries[rel];
  const base = baseEntries[rel];

  // Primary guard: growth over the committed baseline.
  if (base !== undefined && gzip > base + delta.entry) {
    violations.push(
      `${rel} grew ${signed(gzip - base)} over its ${kb(base)} baseline, past the ${kb(delta.entry)} allowance` +
        ` — justify it and re-run with --update-baseline`,
    );
  }

  // Backstop: an absolute ceiling, where one is still configured.
  if (budget !== undefined && gzip > budget) {
    violations.push(`${rel} is ${kb(gzip)} gzip, over its ${kb(budget)} ceiling`);
  }

  if (strict && budget === undefined && base === undefined) {
    violations.push(
      `new entry "${rel}" has neither a budget nor a baseline` +
        (baseline ? " — re-run with --update-baseline" : ' — add one to "sizeBudget".entries'),
    );
  }

  rows.push({ rel, raw: statSync(f).size, gzip, budget, base });
}

// A budget or baseline for a file that no longer exists is dead config.
const matchedRel = new Set(rows.map((r) => r.rel));
for (const name of Object.keys(entries)) {
  if (!matchedRel.has(name)) violations.push(`budget for "${name}" but no such entry — remove the stale budget`);
}
for (const name of Object.keys(baseEntries)) {
  if (!matchedRel.has(name)) {
    violations.push(`baseline for "${name}" but no such entry — re-run with --update-baseline`);
  }
}

if (baseline?.total !== undefined && totalGzip > baseline.total + delta.total) {
  violations.push(
    `package total grew ${signed(totalGzip - baseline.total)} over its ${kb(baseline.total)} baseline,` +
      ` past the ${kb(delta.total)} allowance — justify it and re-run with --update-baseline`,
  );
}
if (total !== undefined && totalGzip > total) {
  violations.push(`package total is ${kb(totalGzip)} gzip, over its ${kb(total)} ceiling`);
}

// --------------------------------------------------------------------------
// Report
// --------------------------------------------------------------------------
const measuredNote = baseline?.measured ? ` (baseline measured ${baseline.measured})` : "";
console.log(`\n  ${label} — bundle size (gzip)${measuredNote}\n`);
const pad = Math.max(...rows.map((r) => r.rel.length), 5);
console.log(`    ${"".padEnd(pad)}  ${"gzip".padStart(9)}  / ${"ceiling".padStart(9)}  ${"Δ base".padStart(10)}`);
for (const r of rows) {
  const b = r.budget === undefined ? "—" : kb(r.budget);
  const d = r.base === undefined ? "—" : signed(r.gzip - r.base);
  const flag = (r.budget !== undefined && r.gzip > r.budget) || (r.base !== undefined && r.gzip > r.base + delta.entry) ? "  ✗" : "";
  console.log(`    ${r.rel.padEnd(pad)}  ${kb(r.gzip).padStart(9)}  / ${b.padStart(9)}  ${d.padStart(10)}${flag}`);
}
const totalBudget = total === undefined ? "—" : kb(total);
const totalDelta = baseline?.total === undefined ? "—" : signed(totalGzip - baseline.total);
console.log(
  `    ${"TOTAL".padEnd(pad)}  ${kb(totalGzip).padStart(9)}  / ${totalBudget.padStart(9)}  ${totalDelta.padStart(10)}`,
);

// --------------------------------------------------------------------------
// --update-baseline: rewrite the committed numbers from this measurement.
// --------------------------------------------------------------------------
if (updateBaseline) {
  // Per-entry baselines only where the package already tracks files one by one.
  // A non-strict package (react-sdk emits ~100 `tsc` files) would otherwise turn
  // its package.json into a size manifest nobody can review.
  const next = {
    measured: new Date().toISOString().slice(0, 10),
    total: totalGzip,
    ...(strict ? { entries: Object.fromEntries(rows.map((r) => [r.rel, r.gzip])) } : {}),
  };
  pkg.sizeBudget = { ...cfg, baseline: next };
  const indent = /\n([ \t]+)"name"/.exec(pkgRaw)?.[1]?.length ?? 2;
  writeFileSync(pkgPath, `${JSON.stringify(pkg, null, indent)}\n`, "utf8");
  console.log(`\n  ✓ baseline written to package.json — commit it with the change that caused it\n`);
  process.exit(0);
}

if (violations.length > 0) {
  console.error("\n  size budget exceeded:");
  for (const v of violations) console.error(`    - ${v}`);
  console.error("");
  process.exit(1);
}

if (!baseline) {
  console.log(`\n  ✓ all entries within budget (no baseline committed — run \`--update-baseline\` to add the delta guard)\n`);
} else {
  console.log("\n  ✓ all entries within budget\n");
}
