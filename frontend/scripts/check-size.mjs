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
 * ## How old the baseline is, and why that is printed (T1010)
 *
 * The delta guard is only as good as the date of the number it compares against.
 * Nothing runs this between releases — every workflow here is `workflow_dispatch`
 * on purpose (T928/T929) — so a baseline goes stale as the normal course of
 * things, and the growth it reports stops being about the change in front of you.
 * That happened: T1005 tripped the gate by 0.93 kB, of which 0.66 kB predated it,
 * and the output gave the author no way to tell. Establishing the split took
 * stashing the work and rebuilding.
 *
 * So the age is printed next to the baseline date, a passing run says when its
 * headroom was measured against a release-era number, and a failing run over a
 * stale baseline names the stash-and-rebuild procedure instead of leaving one
 * change to carry the argument for several. `staleAfterDays` (default 14) is the
 * threshold, per package.
 *
 * ## The middle ground: per-directory baselines (T878)
 *
 * A non-strict package baselines only its `total`, because per-file numbers over
 * ~100 `tsc` outputs are a manifest nobody reviews. That guard can say *"the
 * package grew 4 kB"* and never *"which file"*. `"groupBy": "dir"` adds a third
 * granularity between the two: one baseline per top-level `dist` directory —
 * roughly a dozen numbers, reviewable, and enough to point at the change. Files
 * sitting directly in `dist` are grouped under `(root)`.
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
 *     "groupBy":    "dir",             // also baseline each top-level dist directory
 *     "delta":      { "entry": 512, "dir": 1024, "total": 1024 }, // growth allowances
 *     "staleAfterDays": 14,            // when the baseline stops being evidence (T1010)
 *     "baseline":   {                  // measured — write with --update-baseline
 *       "measured": "2026-08-02",
 *       "total":    33168,
 *       "entries":  { "index.js": 780 },
 *       "dirs":     { "core/": 21000, "(root)": 2080 }
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
const DEFAULT_DELTA = { entry: 512, dir: 1024, total: 1024 };

/** T878 — the group a file directly under `dist` belongs to. */
const ROOT_GROUP = "(root)";

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
const groupByDir = cfg.groupBy === "dir";
const baseline = cfg.baseline;
const baseEntries = baseline?.entries ?? {};
const baseDirs = baseline?.dirs ?? {};
const delta = { ...DEFAULT_DELTA, ...cfg.delta };

/** T878 — the top-level `dist` directory a relative path belongs to. */
const groupOf = (rel) => (rel.includes("/") ? `${rel.slice(0, rel.indexOf("/"))}/` : ROOT_GROUP);

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
/** T878 — gzip totals per top-level `dist` directory, in first-seen order. */
const dirGzip = new Map();
let totalGzip = 0;

for (const f of matched.sort()) {
  const rel = toPosix(relative(dir, f));
  const gzip = gzipSync(readFileSync(f), { level: 9 }).length;
  totalGzip += gzip;
  const group = groupOf(rel);
  dirGzip.set(group, (dirGzip.get(group) ?? 0) + gzip);

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

// T878 — the middle guard: which directory grew, for packages too large to
// baseline file by file. Only ever checked where the package opted in, so a
// package on `total` alone behaves exactly as before.
const dirRows = groupByDir
  ? [...dirGzip.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([name, gzip]) => ({
      name,
      gzip,
      base: baseDirs[name],
    }))
  : [];

if (groupByDir) {
  for (const row of dirRows) {
    if (row.base === undefined) {
      violations.push(
        `directory "${row.name}" has no baseline` +
          (baseline ? " — re-run with --update-baseline" : ' — run --update-baseline to record one'),
      );
    } else if (row.gzip > row.base + delta.dir) {
      violations.push(
        `${row.name} grew ${signed(row.gzip - row.base)} over its ${kb(row.base)} baseline,` +
          ` past the ${kb(delta.dir)} allowance — justify it and re-run with --update-baseline`,
      );
    }
  }
  for (const name of Object.keys(baseDirs)) {
    if (!dirGzip.has(name)) {
      violations.push(`baseline for directory "${name}" but no such directory — re-run with --update-baseline`);
    }
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
/**
 * How many days old the committed baseline is, or `null` when it has no date.
 *
 * T1010 — the delta guard reports growth over a committed number, and says
 * nothing about how old that number is. When T1005 tripped this gate, 0.66 kB of
 * the 0.93 kB predated the change: the author inherited three weeks of drift to
 * justify and had no way to see that from the output. Every workflow here is
 * workflow_dispatch on purpose (T928/T929), so nothing runs this between
 * releases and a stale baseline is the normal case rather than the exception.
 */
function baselineAgeDays(measured) {
  if (!measured) return null;
  const then = Date.parse(`${measured}T00:00:00Z`);
  if (Number.isNaN(then)) return null;
  return Math.floor((Date.now() - then) / 86_400_000);
}

/** Days after which a baseline is old enough that a delta may not be the author's. */
const STALE_AFTER_DAYS = cfg.staleAfterDays ?? 14;

const ageDays = baselineAgeDays(baseline?.measured);
const baselineIsStale = ageDays !== null && ageDays > STALE_AFTER_DAYS;
const measuredNote = baseline?.measured
  ? ` (baseline measured ${baseline.measured}${ageDays === null ? "" : `, ${ageDays}d ago`})`
  : "";
console.log(`\n  ${label} — bundle size (gzip)${measuredNote}\n`);
const pad = Math.max(...rows.map((r) => r.rel.length), 5);
console.log(`    ${"".padEnd(pad)}  ${"gzip".padStart(9)}  / ${"ceiling".padStart(9)}  ${"Δ base".padStart(10)}`);
for (const r of rows) {
  const b = r.budget === undefined ? "—" : kb(r.budget);
  const d = r.base === undefined ? "—" : signed(r.gzip - r.base);
  const flag = (r.budget !== undefined && r.gzip > r.budget) || (r.base !== undefined && r.gzip > r.base + delta.entry) ? "  ✗" : "";
  console.log(`    ${r.rel.padEnd(pad)}  ${kb(r.gzip).padStart(9)}  / ${b.padStart(9)}  ${d.padStart(10)}${flag}`);
}
if (dirRows.length > 0) {
  console.log(`    ${"".padEnd(pad, "─")}`);
  for (const r of dirRows) {
    const d = r.base === undefined ? "—" : signed(r.gzip - r.base);
    const flag = r.base === undefined || r.gzip > r.base + delta.dir ? "  ✗" : "";
    console.log(`    ${r.name.padEnd(pad)}  ${kb(r.gzip).padStart(9)}  / ${"—".padStart(9)}  ${d.padStart(10)}${flag}`);
  }
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
    // T878 — a dozen directory numbers, for the packages that cannot carry a
    // hundred file ones. Sorted so a diff shows the directory that moved.
    ...(groupByDir
      ? { dirs: Object.fromEntries([...dirGzip.entries()].sort(([a], [b]) => a.localeCompare(b))) }
      : {}),
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
  // T1010 — a delta over an old baseline is not evidence about this change. Say
  // so, and name the procedure that splits it, because otherwise the person who
  // tripped the gate either justifies growth that is not theirs or re-baselines
  // without knowing what they blessed.
  if (baselineIsStale) {
    console.error(
      `\n  the baseline above is ${ageDays} days old, so some of that growth may predate this\n` +
        "  change. To find out how much, measure the tree without it:\n\n" +
        "      git stash push -u -- <this package>\n" +
        "      <build> && node ../../scripts/check-size.mjs\n" +
        "      git stash pop\n\n" +
        "  The delta it reports is inherited; the rest is yours. Re-baseline either way —\n" +
        "  what should not happen is one change carrying the argument for several.",
    );
  }
  console.error("");
  process.exit(1);
}

if (!baseline) {
  console.log(`\n  ✓ all entries within budget (no baseline committed — run \`--update-baseline\` to add the delta guard)\n`);
} else if (baselineIsStale) {
  // Passing, and still worth saying: the number this passed against is old, so
  // the headroom shown above is not the headroom the next author will find.
  console.log(
    `\n  ✓ all entries within budget — but the baseline is ${ageDays} days old, so the deltas\n` +
      "    above are measured against a release-era number rather than a recent one. Re-run\n" +
      "    with `--update-baseline` at the next release to keep the guard meaningful.\n",
  );
} else {
  console.log("\n  ✓ all entries within budget\n");
}
