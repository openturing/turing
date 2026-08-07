#!/usr/bin/env node
/**
 * Asserts every `pnpm.overrides` entry actually constrains the tree (T933).
 *
 * Run from the frontend root, after an install:
 *   node scripts/check-overrides.mjs
 *
 * An override is a claim: "no copy of this package below X reaches anyone." pnpm
 * does not verify the claim. T927 wrote two js-yaml overrides that installed
 * without a word while the vulnerable 5.2.1 stayed in the tree — caught only
 * because `pnpm audit` happened to be watching that package. Most overrides here
 * have no audit watching: `@codemirror/view` is pinned so two copies cannot
 * silently break syntax highlighting, `@babel/core` and `undici` are capped so a
 * major jump cannot happen again (T913). If one of those quietly stopped
 * applying, the symptom would be the original bug returning with the config still
 * claiming the fix.
 *
 * Two failures are reported:
 *   1. An override whose target the lockfile never resolves — it constrains
 *      nothing, and is either dead config or a name that no longer matches.
 *   2. A resolved version that does not satisfy its own override — the override
 *      is declared and not in force, which is the silent case.
 *
 * Zero-dependency by house rule, so the YAML reading is deliberately narrow: the
 * `overrides:` block is flat `"key": "value"` pairs, and the lockfile's package
 * keys are `name@version`. Both are checked for shape, and anything unrecognised
 * is reported rather than skipped — a parser that silently drops an entry is the
 * defect this script exists to catch.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();

function fail(msg) {
  console.error(`\n  overrides: ${msg}\n`);
  process.exit(1);
}

// ── Read the declared overrides ──────────────────────────────────────────────

let workspace;
try {
  workspace = readFileSync(join(root, "pnpm-workspace.yaml"), "utf8");
} catch {
  fail(`no readable pnpm-workspace.yaml in ${root} — run from the frontend root.`);
}

/** The `overrides:` block, as declared: [{key, value, line}]. */
function readOverrides(source) {
  const lines = source.split(/\r?\n/); // CRLF in this checkout
  const start = lines.indexOf("overrides:");
  if (start === -1) return [];
  const out = [];
  for (let i = start + 1; i < lines.length; i++) {
    const line = lines[i];
    if (/^\S/.test(line)) break; // dedented out of the block
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const m = /^"([^"]+)"\s*:\s*"([^"]+)"\s*$/.exec(trimmed);
    if (!m) {
      out.push({ malformed: trimmed, line: i + 1 });
      continue;
    }
    out.push({ key: m[1], value: m[2], line: i + 1 });
  }
  return out;
}

const overrides = readOverrides(workspace);
if (overrides.length === 0) fail("no `overrides:` block found — nothing to check.");

// ── Read what the lockfile resolved ──────────────────────────────────────────

let lock;
try {
  lock = readFileSync(join(root, "pnpm-lock.yaml"), "utf8");
} catch {
  fail("no readable pnpm-lock.yaml — run `pnpm install` first.");
}

/** name -> Set(version), from the lockfile's `packages:` keys. */
function readResolved(source) {
  const byName = new Map();
  const lines = source.split(/\r?\n/); // CRLF in this checkout
  let inPackages = false;
  for (const line of lines) {
    if (/^packages:\s*$/.test(line)) { inPackages = true; continue; }
    if (inPackages && /^\S/.test(line)) break;
    if (!inPackages) continue;
    // `  name@version:`, or `  'name@version':` — pnpm quotes any key starting
    // with `@`, which is every scoped package. The name may itself be scoped.
    const m = /^ {2}'?((?:@[^/@\s]+\/)?[^@\s]+)@([^:'\s]+)'?:$/.exec(line);
    if (!m) continue;
    const [, name, version] = m;
    if (!byName.has(name)) byName.set(name, new Set());
    byName.get(name).add(version);
  }
  return byName;
}

const resolved = readResolved(lock);
if (resolved.size === 0) fail("parsed no packages out of pnpm-lock.yaml — the format may have changed.");

// ── A comparator for the range shapes this project uses ──────────────────────

const parse = (v) => String(v).split("-")[0].split(".").map(Number);

function cmp(a, b) {
  const [x, y] = [parse(a), parse(b)];
  for (let i = 0; i < 3; i++) {
    const d = (x[i] ?? 0) - (y[i] ?? 0);
    if (d !== 0) return d < 0 ? -1 : 1;
  }
  return 0;
}

/**
 * Whether `version` satisfies `range`. Supports an exact version and a
 * space-separated conjunction of >=, >, <=, < — which is every shape the
 * overrides here use. Anything else returns null, and the caller reports it as
 * un-checkable rather than assuming it passes.
 */
function satisfies(version, range) {
  const parts = range.trim().split(/\s+/);
  if (parts.length === 1 && /^\d+\.\d+\.\d+$/.test(parts[0])) {
    return cmp(version, parts[0]) === 0;
  }
  for (const part of parts) {
    const m = /^(>=|<=|>|<)(\d+(?:\.\d+){0,2})$/.exec(part);
    if (!m) return null;
    const c = cmp(version, m[2]);
    const ok = { ">=": c >= 0, ">": c > 0, "<=": c <= 0, "<": c < 0 }[m[1]];
    if (!ok) return false;
  }
  return true;
}

/** The package an override key targets: strips `a>b>pkg` scoping and a `@range`. */
function targetOf(key) {
  const last = key.split(">").pop();
  const at = last.lastIndexOf("@");
  return at > 0 ? last.slice(0, at) : last;
}

/** The version selector on a key, if it carries one (`js-yaml@3` -> `3`). */
function selectorOf(key) {
  const last = key.split(">").pop();
  const at = last.lastIndexOf("@");
  return at > 0 ? last.slice(at + 1) : null;
}

// ── Check ────────────────────────────────────────────────────────────────────

const violations = [];
const rows = [];

for (const entry of overrides) {
  if (entry.malformed !== undefined) {
    violations.push(`pnpm-workspace.yaml:${entry.line}: not a quoted "key": "value" pair — ${entry.malformed}`);
    continue;
  }
  const { key, value } = entry;
  const name = targetOf(key);
  const selector = selectorOf(key);
  const versions = [...(resolved.get(name) ?? [])].sort(cmp);

  if (versions.length === 0) {
    violations.push(`"${key}" targets ${name}, which the lockfile never resolves — dead config or a renamed package`);
    rows.push({ key, value, got: "—", state: "unresolved" });
    continue;
  }

  // A path-scoped key (`read-yaml-file>js-yaml`) constrains only the copy under
  // that parent. `packages:` is a flat list with no parent, so which copy the
  // override governs cannot be read here — report it rather than compare against
  // every copy and call an unrelated one a violation.
  if (key.includes(">")) {
    rows.push({ key, value, got: versions.join(", "), state: "path-scoped, not verifiable here" });
    continue;
  }

  // A key with a selector only claims to constrain the copies it selects.
  const scoped = selector
    ? versions.filter((v) => v.split(".")[0] === selector.replace(/^[\^~]/, "").split(".")[0])
    : versions;

  if (selector && scoped.length === 0) {
    rows.push({ key, value, got: versions.join(", "), state: "selects nothing" });
    continue; // not a violation: nothing in the tree matches the selector
  }

  const bad = [];
  let uncheckable = false;
  for (const v of scoped) {
    const ok = satisfies(v, value);
    if (ok === null) { uncheckable = true; break; }
    if (!ok) bad.push(v);
  }

  if (uncheckable) {
    rows.push({ key, value, got: scoped.join(", "), state: "range not understood" });
    violations.push(`"${key}": cannot check the range "${value}" — extend satisfies() or simplify the range`);
  } else if (bad.length > 0) {
    rows.push({ key, value, got: scoped.join(", "), state: "NOT IN FORCE" });
    violations.push(
      `"${key}" declares "${value}" but the lockfile resolves ${bad.join(", ")} — the override is not in force`,
    );
  } else {
    rows.push({ key, value, got: scoped.join(", "), state: "ok" });
  }
}

// ── Report ───────────────────────────────────────────────────────────────────

const pad = Math.max(...rows.map((r) => r.key.length), 3);
const vpad = Math.max(...rows.map((r) => r.value.length), 8);
console.log(`\n  pnpm overrides — ${rows.length} declared\n`);
for (const r of rows) {
  const flag = r.state === "ok" ? "" : `  ${r.state}`;
  console.log(`    ${r.key.padEnd(pad)}  ${r.value.padEnd(vpad)}  -> ${r.got}${flag}`);
}

if (violations.length > 0) {
  console.error("\n  overrides rejected:");
  for (const v of violations) console.error(`    - ${v}`);
  console.error("");
  process.exit(1);
}

console.log("\n  ✓ every override resolves and is in force\n");
