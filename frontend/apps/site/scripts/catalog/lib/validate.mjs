/**
 * Validation + diff-report review gate (Block BD / T769). Structurally validates
 * the proposed envelope against the catalog schema's constraints (reusing the
 * same rules as emit-model-catalog.mjs) and renders a human-readable diff report
 * vs. the committed file with per-field source attribution + conflict flags.
 *
 * @since 2026.3.4 (T769)
 */
import { KINDS } from "./util.mjs";

const STATUSES = new Set(["PREVIEW", "GA", "DEPRECATED", "RETIRED"]);

/** @returns string[] of validation errors (empty = valid). */
export function validateEnvelope(env) {
  const errs = [];
  if (!Number.isInteger(env?.version) || env.version < 1) errs.push("missing/invalid integer `version`");
  if (typeof env?.lastUpdated !== "string") errs.push("missing string `lastUpdated`");
  if (typeof env?.vendors !== "object" || env.vendors === null) errs.push("missing `vendors` object");
  for (const [vendor, entries] of Object.entries(env?.vendors || {})) {
    if (!Array.isArray(entries)) { errs.push(`vendor "${vendor}" is not an array`); continue; }
    const ids = new Set();
    for (let i = 0; i < entries.length; i++) {
      const e = entries[i];
      const at = `${vendor}[${i}]`;
      if (!e?.id || typeof e.id !== "string") { errs.push(`${at} missing string id`); continue; }
      if (ids.has(e.id)) errs.push(`${at} duplicate id "${e.id}"`);
      ids.add(e.id);
      if (!e.label || typeof e.label !== "string") errs.push(`${at} (${e.id}) missing label`);
      if (!KINDS.has(e.kind)) errs.push(`${at} (${e.id}) invalid kind "${e.kind}"`);
      if (e.status !== undefined && !STATUSES.has(e.status)) errs.push(`${at} (${e.id}) invalid status "${e.status}"`);
      for (const nf of ["contextWindow", "maxOutputTokens", "embeddingDimensions"]) {
        if (e[nf] !== undefined && !(Number.isInteger(e[nf]) && e[nf] >= 1)) errs.push(`${at} (${e.id}) invalid ${nf}`);
      }
    }
  }
  return errs;
}

const CMP_FIELDS = [
  "label", "kind", "contextWindow", "maxOutputTokens", "embeddingDimensions",
  "capabilities", "modalities", "knowledgeCutoff", "releaseDate", "aliases",
  "status", "deprecated",
];

function fmt(v) {
  if (v === undefined) return "∅";
  if (Array.isArray(v)) return `[${v.join(", ")}]`;
  if (typeof v === "object") return JSON.stringify(v);
  return String(v);
}
function eq(a, b) {
  return JSON.stringify(a ?? null) === JSON.stringify(b ?? null);
}

/**
 * Render the diff report as text (never writes anything).
 * @returns { text, stats } stats = { added, removed, changed, conflicts, skipped }
 */
export function diffReport(existing, proposed, meta = {}, skipped = []) {
  const lines = [];
  const stats = { added: 0, removed: 0, changed: 0, conflicts: 0, skipped: skipped.length };
  const vendors = [...new Set([...Object.keys(existing?.vendors || {}), ...Object.keys(proposed?.vendors || {})])].sort();

  for (const vendor of vendors) {
    const oldE = new Map((existing?.vendors?.[vendor] || []).map((e) => [e.id, e]));
    const newE = new Map((proposed?.vendors?.[vendor] || []).map((e) => [e.id, e]));
    const section = [];

    for (const [id, e] of newE) {
      if (oldE.has(id)) continue;
      stats.added++;
      section.push(`  + ADD ${id} (${e.kind}) — sources: ${(e.sources || []).join(", ") || "?"}`);
    }
    for (const [id] of oldE) {
      if (!newE.has(id)) { stats.removed++; section.push(`  - REMOVE ${id}`); }
    }
    for (const [id, ne] of newE) {
      const oe = oldE.get(id);
      if (!oe) continue;
      const changes = [];
      for (const f of CMP_FIELDS) {
        if (!eq(oe[f], ne[f])) {
          const src = meta[`${vendor}::${id}`]?.fieldProvenance?.[f];
          changes.push(`      · ${f}: ${fmt(oe[f])} → ${fmt(ne[f])}${src ? ` [${src}]` : ""}`);
        }
      }
      const conflicts = meta[`${vendor}::${id}`]?.conflicts || [];
      if (changes.length || conflicts.length) {
        stats.changed++;
        section.push(`  ~ CHANGE ${id}`);
        section.push(...changes);
        for (const c of conflicts) {
          stats.conflicts++;
          const losers = c.losers.map((l) => `${fmt(l.value)}@${l.source}`).join(", ");
          section.push(`      ⚠ conflict on ${c.field}: kept ${fmt(c.winner.value)}@${c.winner.source} over ${losers}`);
        }
      }
    }

    if (section.length) {
      lines.push(`\n[${vendor}] (${newE.size} models)`);
      lines.push(...section);
    }
  }

  if (skipped.length) {
    lines.push(`\n[skipped] ${skipped.length} id(s) not admitted (anchoring rule):`);
    for (const s of skipped.slice(0, 50)) lines.push(`  · ${s.vendor}/${s.id} — ${s.reason}`);
    if (skipped.length > 50) lines.push(`  … and ${skipped.length - 50} more`);
  }

  const header =
    `Model catalog diff — proposed vs committed\n` +
    `  added: ${stats.added}  removed: ${stats.removed}  changed: ${stats.changed}` +
    `  conflicts: ${stats.conflicts}  skipped: ${stats.skipped}`;

  return { text: header + "\n" + (lines.length ? lines.join("\n") : "\n(no differences)") + "\n", stats };
}
