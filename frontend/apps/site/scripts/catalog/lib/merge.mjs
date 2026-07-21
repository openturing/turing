/**
 * Merge + reconciliation engine (Block BD / T768). Folds all normalized drafts
 * per (vendor, id) with explicit source precedence, records per-field provenance,
 * flags conflicts + confidence, and emits a deterministically-ordered *proposed*
 * envelope. It NEVER writes the canonical file — that is the T769 gate's job.
 *
 * Precedence (higher wins per field):
 *   pinned override (100) > live vendor API (50) > override (30)
 *   > committed catalog (15) > LiteLLM (10)
 *
 * Two safety rules keep a noisy/partial run from poisoning the reference:
 *
 *  - **Carry-forward (additive by default).** The committed catalog is itself a
 *    (low-priority) source, so every existing entry is preserved + enriched. A run
 *    with only some API keys never silently drops the vendors it didn't fetch.
 *  - **Positive-evidence removal.** An id is dropped ONLY when the vendor's *own
 *    live API ran and did not list it* (and it isn't override-pinned). LiteLLM /
 *    a missing key can never cause a removal.
 *
 * **Anchoring rule** — a *new* id (not already in the committed catalog) is
 * admitted only if a live vendor API or an override vouches for it. LiteLLM
 * enriches existing/vendor-confirmed ids but can never introduce a brand-new id.
 *
 * @since 2026.3.4 (T768)
 */
import { KINDS, compact } from "./util.mjs";

const LIVE_PRIORITY = 50;
const PRIORITY = {
  "openai-api": LIVE_PRIORITY,
  "anthropic-api": LIVE_PRIORITY,
  "gemini-api": LIVE_PRIORITY,
  "cohere-api": LIVE_PRIORITY,
  "mistral-api": LIVE_PRIORITY,
  overrides: 30,
  committed: 15,
  litellm: 10,
};
const PIN_PRIORITY = 100;

const KIND_ORDER = [
  "CHAT", "EMBEDDING", "RERANK", "IMAGE",
  "TRANSCRIPTION", "SPEECH", "VIDEO", "MODERATION", "UNKNOWN",
];

// Highest-priority-wins scalar fields (conflicts are detected + reported).
const SCALAR_FIELDS = [
  "label", "kind", "contextWindow", "maxOutputTokens", "embeddingDimensions",
  "knowledgeCutoff", "releaseDate", "status", "deprecated",
];
// Set-union fields (no conflict — sources complement each other).
const UNION_FIELDS = ["capabilities", "aliases"];
// Provenance fields carried forward from committed entries are recomputed, not merged.
const PROVENANCE_FIELDS = new Set(["sources", "lastVerified", "vendor", "id"]);

// Canonical field order for a written entry (matches the committed file, keeping
// applied diffs minimal). Unlisted keys keep their insertion order at the end.
const FIELD_ORDER = [
  "id", "label", "kind", "contextWindow", "maxOutputTokens", "embeddingDimensions",
  "capabilities", "modalities", "knowledgeCutoff", "releaseDate", "aliases",
  "status", "deprecated", "sources", "lastVerified",
];

function orderEntry(entry) {
  const out = {};
  for (const f of FIELD_ORDER) if (entry[f] !== undefined) out[f] = entry[f];
  for (const [k, v] of Object.entries(entry)) if (!(k in out)) out[k] = v;
  return out;
}

function priorityOf(sourceId, pinned) {
  if (pinned) return PIN_PRIORITY;
  return PRIORITY[sourceId] ?? 0;
}

function mergeModalities(ranked) {
  const input = new Set();
  const output = new Set();
  for (const { draft } of ranked) {
    for (const m of draft.modalities?.input || []) input.add(m);
    for (const m of draft.modalities?.output || []) output.add(m);
  }
  const res = {};
  if (input.size) res.input = [...input].sort();
  if (output.size) res.output = [...output].sort();
  return Object.keys(res).length ? res : undefined;
}

/** Turn the committed envelope into carried-forward drafts (a low-priority source). */
function committedDrafts(existing) {
  const drafts = [];
  for (const [vendor, entries] of Object.entries(existing?.vendors || {})) {
    for (const e of entries) {
      const draft = { vendor };
      for (const [k, v] of Object.entries(e)) {
        if (!PROVENANCE_FIELDS.has(k)) draft[k] = v;
      }
      draft.id = e.id;
      drafts.push(draft);
    }
  }
  return drafts;
}

/**
 * @param sources  [{ sourceId, drafts }] — live API + litellm drafts ({ vendor, id, ...fields })
 * @param overrides [{ vendor, id, __pin?, ...fields }] curated pins (source id "overrides")
 * @param existing  committed catalog envelope — carried forward + used for anchoring
 * @param anchoringSources Set of source ids that may introduce new ids
 * @param liveIdsByVendor Map<vendor, Set<id>> ids each live vendor API returned (removal evidence)
 * @param when      ISO date stamped as lastVerified
 * @returns { vendors, meta, skipped, removed }
 */
export function merge({ sources, overrides = [], existing, anchoringSources, liveIdsByVendor, when }) {
  const existingIds = new Set();
  for (const [vendor, entries] of Object.entries(existing?.vendors || {})) {
    for (const e of entries) existingIds.add(`${vendor}::${e.id}`);
  }
  const overrideIds = new Set(overrides.map((o) => `${o.vendor}::${o.id}`));

  // Flatten every draft into per-(vendor,id) buckets, tagging source + pin.
  const buckets = new Map(); // key -> [{ sourceId, pinned, draft }]
  const push = (sourceId, draft, pinned) => {
    if (!draft?.vendor || !draft?.id) return;
    const key = `${draft.vendor}::${draft.id}`;
    if (!buckets.has(key)) buckets.set(key, []);
    buckets.get(key).push({ sourceId, pinned: !!pinned, draft });
  };
  for (const { sourceId, drafts } of sources) {
    for (const d of drafts) push(sourceId, d, false);
  }
  for (const d of committedDrafts(existing)) push("committed", d, false);
  for (const o of overrides) {
    const { __pin, ...fields } = o;
    push("overrides", fields, __pin);
  }

  const vendors = {};
  const meta = {};
  const skipped = [];
  const removed = [];

  for (const [key, group] of buckets) {
    const [vendor, id] = key.split("::");

    const anchored =
      existingIds.has(key) || group.some((g) => anchoringSources.has(g.sourceId));
    if (!anchored) {
      skipped.push({ vendor, id, reason: "litellm-only (not anchored by a vendor API / override / committed catalog)" });
      continue;
    }

    // Positive-evidence removal: vendor's live API ran but did not list this id.
    const liveIds = liveIdsByVendor?.get(vendor);
    if (liveIds && !liveIds.has(id) && !overrideIds.has(key)) {
      removed.push({ vendor, id, reason: `absent from ${vendor} live API listing` });
      continue;
    }

    const ranked = group
      .map((g, i) => ({ ...g, prio: priorityOf(g.sourceId, g.pinned), i }))
      .sort((a, b) => b.prio - a.prio || a.i - b.i);

    const entry = { id };
    const fieldProvenance = {};
    const conflicts = [];
    const contributingSources = new Set();

    for (const field of SCALAR_FIELDS) {
      let winner = null;
      const seen = [];
      for (const r of ranked) {
        const v = r.draft[field];
        if (v === undefined || v === null) continue;
        seen.push({ value: v, source: r.sourceId });
        if (winner === null) winner = { value: v, source: r.sourceId };
      }
      if (winner === null) continue;
      entry[field] = winner.value;
      fieldProvenance[field] = winner.source;
      contributingSources.add(winner.source);
      const losers = seen.filter((s) => JSON.stringify(s.value) !== JSON.stringify(winner.value));
      if (losers.length) conflicts.push({ field, winner, losers });
    }

    // Union fields keep first-seen order with the committed values first, so an
    // enriching run appends new hints without reshuffling existing arrays (churn).
    const unionOrder = [...ranked].sort(
      (a, b) => (a.sourceId === "committed" ? 0 : 1) - (b.sourceId === "committed" ? 0 : 1),
    );
    for (const field of UNION_FIELDS) {
      const values = [];
      const seen = new Set();
      const contributors = new Set();
      for (const r of unionOrder) {
        for (const v of r.draft[field] || []) {
          if (!seen.has(v)) { seen.add(v); values.push(v); }
          contributors.add(r.sourceId);
        }
      }
      if (values.length) {
        entry[field] = values;
        fieldProvenance[field] = [...contributors].sort().join("+");
        for (const c of contributors) contributingSources.add(c);
      }
    }

    const modalities = mergeModalities(ranked);
    if (modalities) {
      entry.modalities = modalities;
      fieldProvenance.modalities = ranked.filter((r) => r.draft.modalities).map((r) => r.sourceId).join("+");
    }

    if (!entry.label) entry.label = id;
    if (!entry.kind || !KINDS.has(entry.kind)) entry.kind = "UNKNOWN";

    entry.sources = [...contributingSources].sort();
    entry.lastVerified = when;

    (vendors[vendor] ||= []).push(orderEntry(compact(entry)));
    meta[key] = {
      fieldProvenance,
      conflicts,
      confidence: conflicts.length ? "low" : contributingSources.size >= 2 ? "high" : "medium",
      pinned: ranked.some((r) => r.pinned),
    };
  }

  // Deterministic ordering: vendors alphabetically, entries by (kind, id).
  const orderedVendors = {};
  for (const vendor of Object.keys(vendors).sort()) {
    orderedVendors[vendor] = vendors[vendor].sort((a, b) => {
      const k = KIND_ORDER.indexOf(a.kind) - KIND_ORDER.indexOf(b.kind);
      return k !== 0 ? k : a.id.localeCompare(b.id);
    });
  }

  return { vendors: orderedVendors, meta, skipped, removed };
}
