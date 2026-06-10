#!/usr/bin/env node
/**
 * Shared, zero-dependency bundle-size budget for the publishable frontend
 * packages (T303 packaging hardening, generalized).
 *
 * One script, per-package config. Run from a package directory:
 *   node ../../scripts/check-size.mjs
 * It reads a `sizeBudget` block from that package's package.json, gzip-compresses
 * the matching `dist` entries, and fails if any entry (or the package total)
 * exceeds budget. The point is to make size regressions a conscious decision,
 * not a surprise — bump a budget intentionally when a package legitimately grows.
 *
 * package.json → "sizeBudget":
 *   {
 *     "dir":        "dist",            // scanned directory (default "dist")
 *     "extensions": [".js"],           // file suffixes to weigh (default [".js"])
 *     "exclude":    ["stories/"],      // posix substrings to skip (default [])
 *     "entries":    { "index.js": 800 }, // per-file gzip budgets, bytes
 *     "total":      14000,             // gzip ceiling over all matched files
 *     "strict":     true              // every matched file MUST have an entry budget
 *   }
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
import { readdirSync, readFileSync, statSync } from "node:fs";
import { gzipSync } from "node:zlib";
import { join, relative, sep } from "node:path";

const pkgDir = process.cwd();

function fail(msg) {
  console.error(`\n  size budget: ${msg}\n`);
  process.exit(1);
}

let pkg;
try {
  pkg = JSON.parse(readFileSync(join(pkgDir, "package.json"), "utf8"));
} catch {
  fail(`no package.json in ${pkgDir}`);
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
const rows = [];
const violations = [];
let totalGzip = 0;

for (const f of matched.sort()) {
  const rel = toPosix(relative(dir, f));
  const gzip = gzipSync(readFileSync(f), { level: 9 }).length;
  totalGzip += gzip;

  const budget = entries[rel];
  if (budget === undefined) {
    if (strict) violations.push(`new entry "${rel}" has no budget — add one to "sizeBudget".entries`);
  } else if (gzip > budget) {
    violations.push(`${rel} is ${kb(gzip)} gzip, over its ${kb(budget)} budget`);
  }
  rows.push({ rel, raw: statSync(f).size, gzip, budget });
}

// A budget for a file that no longer exists is dead config — surface it.
const matchedRel = new Set(rows.map((r) => r.rel));
for (const name of Object.keys(entries)) {
  if (!matchedRel.has(name)) violations.push(`budget for "${name}" but no such entry — remove the stale budget`);
}

console.log(`\n  ${label} — bundle size (gzip)\n`);
const pad = Math.max(...rows.map((r) => r.rel.length), 5);
for (const r of rows) {
  const b = r.budget === undefined ? "—" : kb(r.budget);
  const flag = r.budget !== undefined && r.gzip > r.budget ? "  ✗" : "";
  console.log(`    ${r.rel.padEnd(pad)}  ${kb(r.gzip).padStart(9)}  / ${b.padStart(9)}${flag}`);
}
const totalBudget = total === undefined ? "—" : kb(total);
console.log(`    ${"TOTAL".padEnd(pad)}  ${kb(totalGzip).padStart(9)}  / ${totalBudget.padStart(9)}`);

if (total !== undefined && totalGzip > total) {
  violations.push(`package total is ${kb(totalGzip)} gzip, over its ${kb(total)} budget`);
}

if (violations.length > 0) {
  console.error("\n  size budget exceeded:");
  for (const v of violations) console.error(`    - ${v}`);
  console.error("");
  process.exit(1);
}

console.log("\n  ✓ all entries within budget\n");
