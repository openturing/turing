/**
 * Shared helpers for the model-catalog regeneration pipeline (Block BD / T765).
 * Zero-dependency — Node built-ins + global fetch only, mirroring
 * emit-model-catalog.mjs. Paths resolve to the canonical source of truth in
 * turing-app so the pipeline reads/writes the same file the product bundles.
 *
 * @since 2026.3.4 (T765)
 */
import { mkdirSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

/** scripts/catalog — the pipeline root. */
export const CATALOG_ROOT = resolve(here, "..");
/** Raw per-source snapshot cache (offline-replayable). */
export const SOURCES_DIR = resolve(CATALOG_ROOT, "sources");
/** Curated top-precedence pins. */
export const OVERRIDES_FILE = resolve(CATALOG_ROOT, "overrides.json");
/** Proposed envelope + human-readable report land here (never the canonical file). */
export const OUT_DIR = resolve(CATALOG_ROOT, "out");
/** The single source of truth — bundled by turing-app AND published (T759/T760). */
export const CANONICAL = resolve(
  CATALOG_ROOT,
  "../../../../../turing-app/src/main/resources/llm/models-catalog.json",
);
export const SCHEMA_FILE = resolve(
  CATALOG_ROOT,
  "../../../../../turing-app/src/main/resources/llm/models-catalog.schema.json",
);

/** Model kinds — the TurLlmModelKind taxonomy (Block BB / T750). */
export const KINDS = new Set([
  "CHAT", "EMBEDDING", "RERANK", "IMAGE",
  "TRANSCRIPTION", "SPEECH", "VIDEO", "MODERATION", "UNKNOWN",
]);

export function log(msg) {
  console.log(`catalog: ${msg}`);
}
export function warn(msg) {
  console.warn(`catalog: WARN ${msg}`);
}
export function fail(msg) {
  console.error(`catalog: ERROR ${msg}`);
  process.exit(1);
}

/** Parse `--flag` / `--key=value` argv into { apply, offline, date, verbose, only }. */
export function parseArgs(argv) {
  const args = { apply: false, offline: false, verbose: false, date: null, only: null };
  for (const a of argv) {
    if (a === "--apply") args.apply = true;
    else if (a === "--offline") args.offline = true;
    else if (a === "--verbose" || a === "-v") args.verbose = true;
    else if (a.startsWith("--date=")) args.date = a.slice("--date=".length);
    else if (a.startsWith("--only=")) args.only = a.slice("--only=".length).split(",").map((s) => s.trim()).filter(Boolean);
  }
  return args;
}

/** ISO date (YYYY-MM-DD). `--date=` overrides for deterministic runs/tests. */
export function today(override) {
  if (override && /^\d{4}-\d{2}-\d{2}$/.test(override)) return override;
  return new Date().toISOString().slice(0, 10);
}

export function readJson(path) {
  return JSON.parse(readFileSync(path, "utf8"));
}
export function writeJson(path, obj) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, JSON.stringify(obj, null, 2) + "\n", "utf8");
}

/** Path of a source's raw snapshot in the offline cache. */
export function snapshotPath(sourceId) {
  return resolve(SOURCES_DIR, `${sourceId}.json`);
}

/**
 * Load a cached snapshot's raw payload, or null when absent. Snapshots wrap the
 * raw response with fetch provenance: { sourceId, fetchedAt, url, raw }.
 */
export function loadSnapshot(sourceId) {
  const p = snapshotPath(sourceId);
  if (!existsSync(p)) return null;
  return readJson(p);
}

/** Persist a raw response + fetch provenance so runs replay offline. */
export function saveSnapshot(sourceId, url, raw, when) {
  writeJson(snapshotPath(sourceId), { sourceId, fetchedAt: when, url, raw });
}

/** Heuristic kind from an id when the source carries no kind signal (e.g. OpenAI). */
export function classifyKind(id) {
  const s = String(id || "").toLowerCase();
  if (/embed/.test(s)) return "EMBEDDING";
  if (/rerank/.test(s)) return "RERANK";
  if (/whisper|transcrib|speech-to-text|stt/.test(s)) return "TRANSCRIPTION";
  if (/\btts\b|text-to-speech|-audio-|realtime/.test(s)) return "SPEECH";
  if (/dall-e|gpt-image|imagen|image-1|-image/.test(s)) return "IMAGE";
  if (/moderation/.test(s)) return "MODERATION";
  if (/\bsora\b|veo|video/.test(s)) return "VIDEO";
  return "CHAT";
}

/** Drop null/undefined/empty-array fields so entries stay lean (no guessed values). */
export function compact(obj) {
  const out = {};
  for (const [k, v] of Object.entries(obj)) {
    if (v === undefined || v === null) continue;
    if (Array.isArray(v) && v.length === 0) continue;
    if (typeof v === "object" && !Array.isArray(v) && Object.keys(v).length === 0) continue;
    out[k] = v;
  }
  return out;
}

/**
 * Fetch JSON over the network, or replay the cached snapshot when `offline`.
 * Returns { raw, fetchedAt, fromCache } or null when nothing is available.
 * Never throws on a soft failure — callers decide whether to skip.
 */
export async function fetchOrReplay(sourceId, url, { headers = {}, offline = false, when } = {}) {
  if (offline) {
    const snap = loadSnapshot(sourceId);
    if (!snap) {
      warn(`${sourceId}: --offline but no cached snapshot at ${snapshotPath(sourceId)}; skipping`);
      return null;
    }
    return { raw: snap.raw, fetchedAt: snap.fetchedAt, fromCache: true };
  }
  try {
    const res = await fetch(url, { headers });
    if (!res.ok) {
      warn(`${sourceId}: ${url} -> HTTP ${res.status}; skipping`);
      return null;
    }
    const raw = await res.json();
    saveSnapshot(sourceId, url, raw, when);
    return { raw, fetchedAt: when, fromCache: false };
  } catch (e) {
    warn(`${sourceId}: fetch failed (${e.message}); trying cached snapshot`);
    const snap = loadSnapshot(sourceId);
    if (snap) return { raw: snap.raw, fetchedAt: snap.fetchedAt, fromCache: true };
    warn(`${sourceId}: no cached snapshot; skipping`);
    return null;
  }
}
