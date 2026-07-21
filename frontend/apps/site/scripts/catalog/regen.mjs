/**
 * Model-catalog regeneration orchestrator (Block BD / T765 + T770).
 *
 * Chains fetch → merge → validate → report and, ONLY on explicit `--apply`,
 * writes the canonical `models-catalog.json` (bumping `lastUpdated`). Without
 * `--apply` it is read-only: it writes the *proposed* envelope + diff report into
 * scripts/catalog/out/ and prints the report — a bad upstream fetch can never
 * silently poison the public reference (the T387/T667 propose-and-review rule).
 *
 *   node scripts/catalog/regen.mjs            # dry-run: fetch, propose, report
 *   node scripts/catalog/regen.mjs --offline  # replay cached snapshots, no network
 *   node scripts/catalog/regen.mjs --apply    # write the canonical file
 *   node scripts/catalog/regen.mjs --date=2026-07-21 --offline   # deterministic
 *
 * Zero-dependency (Node built-ins + global fetch), mirroring emit-model-catalog.mjs.
 *
 * @since 2026.3.4 (T770)
 */
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { ADAPTERS } from "./adapters/index.mjs";
import { merge } from "./lib/merge.mjs";
import { validateEnvelope, diffReport } from "./lib/validate.mjs";
import {
  CANONICAL, OUT_DIR, OVERRIDES_FILE,
  fail, log, parseArgs, readJson, today, warn, writeJson,
} from "./lib/util.mjs";

const args = parseArgs(process.argv.slice(2));
const when = today(args.date);
const env = process.env;

const existing = existsSync(CANONICAL) ? readJson(CANONICAL) : { version: 1, vendors: {} };

// 1) Fetch + normalize every source (key-gated; a missing key skips gracefully).
const sources = [];
const anchoringSources = new Set(); // live vendor APIs + overrides may introduce new ids
const liveIdsByVendor = new Map(); // vendor -> Set<id> the live API listed (removal evidence)
for (const adapter of ADAPTERS) {
  if (args.only && !args.only.includes(adapter.id)) continue;
  // Offline replays cached snapshots — no auth needed, so don't gate on the key.
  if (!args.offline && adapter.envKey && !env[adapter.envKey]) {
    warn(`${adapter.id}: ${adapter.envKey} not set — skipping source`);
    continue;
  }
  const result = await adapter.fetch(env, { offline: args.offline, when });
  if (!result) continue;
  let drafts = [];
  try {
    drafts = adapter.normalize(result.raw) || [];
  } catch (e) {
    warn(`${adapter.id}: normalize failed (${e.message}) — skipping`);
    continue;
  }
  sources.push({ sourceId: adapter.id, drafts });
  if (adapter.vendor !== null) {
    anchoringSources.add(adapter.id); // vendor-scoped = live API
    // A non-empty live listing is the authority for which ids that vendor still serves.
    if (drafts.length) liveIdsByVendor.set(adapter.vendor, new Set(drafts.map((d) => d.id)));
  }
  log(`${adapter.id}: ${drafts.length} drafts${result.fromCache ? " (cache)" : ""}`);
}
anchoringSources.add("overrides");

if (!sources.length) fail("no sources produced drafts (set at least one API key, or run --offline with cached snapshots)");

// 2) Curated top-precedence pins.
const overrides = existsSync(OVERRIDES_FILE) ? flattenOverrides(readJson(OVERRIDES_FILE)) : [];
if (overrides.length) log(`overrides: ${overrides.length} pinned field-set(s)`);

// 3) Merge → proposed envelope (+ provenance/conflict meta).
const { vendors, meta, skipped, removed } = merge({ sources, overrides, existing, anchoringSources, liveIdsByVendor, when });
if (removed.length) log(`${removed.length} id(s) removed on live-API evidence`);
const proposed = {
  _comment: existing._comment,
  version: existing.version ?? 1,
  lastUpdated: when,
  schema: existing.schema,
  vendors,
};

// 4) Validate structurally against the schema's constraints.
const errs = validateEnvelope(proposed);
if (errs.length) {
  for (const e of errs) console.error(`catalog: SCHEMA ${e}`);
  fail(`proposed catalog failed validation (${errs.length} error(s)) — not writing anything`);
}

// 5) Diff report vs the committed file.
const { text, stats } = diffReport(existing, proposed, meta, skipped);
writeJson(resolve(OUT_DIR, "proposed-catalog.json"), proposed);
const { writeFileSync, mkdirSync } = await import("node:fs");
mkdirSync(OUT_DIR, { recursive: true });
writeFileSync(resolve(OUT_DIR, "diff-report.txt"), text, "utf8");
console.log("\n" + text);
log(`proposed envelope + report written to ${OUT_DIR}`);

// 6) Review gate — only --apply overwrites the canonical file.
if (args.apply) {
  writeJson(CANONICAL, proposed);
  log(`APPLIED — wrote ${CANONICAL} (${stats.added} added, ${stats.removed} removed, ${stats.changed} changed)`);
  log("Review the diff, then run: pnpm --filter turing-site emit:models  (republish public catalog)");
} else {
  log("dry-run (no --apply): canonical file untouched. Re-run with --apply to write it.");
}

/** overrides.json { vendor: [ {id, __pin?, ...fields} ] } → flat draft list. */
function flattenOverrides(obj) {
  const out = [];
  for (const [vendor, entries] of Object.entries(obj || {})) {
    if (vendor.startsWith("_") || !Array.isArray(entries)) continue;
    for (const e of entries) {
      if (e && typeof e.id === "string") out.push({ vendor, ...e });
    }
  }
  return out;
}
