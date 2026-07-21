/**
 * Unit tests for the model-catalog regeneration pipeline (Block BD). Zero-dep,
 * offline — exercises the pure merge/validate/normalize units with inline
 * fixtures (no network, no filesystem). Run: `node --test scripts/catalog`.
 *
 * @since 2026.3.4 (T770)
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { merge } from "./lib/merge.mjs";
import { validateEnvelope, diffReport } from "./lib/validate.mjs";
import litellm from "./adapters/litellm.mjs";

const WHEN = "2026-07-21";
const ANCHORS = new Set(["openai-api", "anthropic-api", "gemini-api", "cohere-api", "mistral-api", "overrides"]);

function baseMerge(over = {}) {
  return merge({
    sources: [],
    overrides: [],
    existing: { version: 1, vendors: {} },
    anchoringSources: ANCHORS,
    liveIdsByVendor: new Map(),
    when: WHEN,
    ...over,
  });
}

test("litellm normalize: maps mode→kind, strips prefix, drops pricing + wildcards", () => {
  const drafts = litellm.normalize({
    sample_spec: { note: "ignore" },
    "gpt-4.1": { litellm_provider: "openai", mode: "chat", max_input_tokens: 1000, max_output_tokens: 200, supports_vision: true, input_cost_per_token: 0.01 },
    "anthropic/claude-x": { litellm_provider: "anthropic", mode: "chat", max_input_tokens: 200000 },
    "embed-y": { litellm_provider: "cohere", mode: "embedding", output_vector_size: 1024 },
    "openai/gpt-4o-*": { litellm_provider: "openai", mode: "chat" },
    "unknown-vendor-model": { litellm_provider: "someone-else", mode: "chat" },
  });
  const byId = Object.fromEntries(drafts.map((d) => [`${d.vendor}/${d.id}`, d]));
  assert.equal(drafts.length, 3, "sample_spec + wildcard + untracked vendor dropped");
  assert.equal(byId["openai/gpt-4.1"].kind, "CHAT");
  assert.equal(byId["openai/gpt-4.1"].maxOutputTokens, 200);
  assert.ok(byId["openai/gpt-4.1"].capabilities.includes("vision"));
  assert.equal(byId["openai/gpt-4.1"].input_cost_per_token, undefined, "pricing dropped");
  assert.equal(byId["anthropic/claude-x"].id, "claude-x", "provider/ prefix stripped");
  assert.equal(byId["cohere/embed-y"].kind, "EMBEDDING");
  assert.equal(byId["cohere/embed-y"].embeddingDimensions, 1024);
});

test("merge carry-forward: committed entries survive a run that never fetched them", () => {
  const existing = { version: 1, vendors: { anthropic: [{ id: "claude-x", label: "Claude X", kind: "CHAT", contextWindow: 200000 }] } };
  const { vendors } = baseMerge({ existing });
  assert.equal(vendors.anthropic.length, 1);
  assert.deepEqual(vendors.anthropic[0].label, "Claude X");
  assert.deepEqual(vendors.anthropic[0].sources, ["committed"]);
});

test("merge anchoring: a litellm-only new id is skipped, not admitted", () => {
  const { vendors, skipped } = baseMerge({
    sources: [{ sourceId: "litellm", drafts: [{ vendor: "openai", id: "ghost-model", kind: "CHAT" }] }],
  });
  assert.equal(vendors.openai, undefined);
  assert.equal(skipped.length, 1);
  assert.equal(skipped[0].id, "ghost-model");
});

test("merge enrichment: litellm fills a field the committed entry lacks", () => {
  const existing = { version: 1, vendors: { openai: [{ id: "gpt-4.1", label: "GPT-4.1", kind: "CHAT" }] } };
  const { vendors, meta } = baseMerge({
    existing,
    sources: [{ sourceId: "litellm", drafts: [{ vendor: "openai", id: "gpt-4.1", kind: "CHAT", contextWindow: 1000000, maxOutputTokens: 32768 }] }],
  });
  const e = vendors.openai[0];
  assert.equal(e.label, "GPT-4.1", "curated label preserved");
  assert.equal(e.contextWindow, 1000000);
  assert.equal(e.maxOutputTokens, 32768);
  assert.equal(meta["openai::gpt-4.1"].fieldProvenance.maxOutputTokens, "litellm");
});

test("merge precedence: live vendor API beats litellm; conflict is flagged", () => {
  const existing = { version: 1, vendors: { gemini: [{ id: "g", label: "G", kind: "CHAT" }] } };
  const { vendors, meta } = baseMerge({
    existing,
    sources: [
      { sourceId: "gemini-api", drafts: [{ vendor: "gemini", id: "g", kind: "CHAT", contextWindow: 2000000 }] },
      { sourceId: "litellm", drafts: [{ vendor: "gemini", id: "g", kind: "CHAT", contextWindow: 1000000 }] },
    ],
  });
  assert.equal(vendors.gemini[0].contextWindow, 2000000, "live API wins");
  const conflicts = meta["gemini::g"].conflicts;
  assert.equal(conflicts.length, 1);
  assert.equal(conflicts[0].field, "contextWindow");
  assert.equal(conflicts[0].winner.source, "gemini-api");
});

test("merge pin: __pin override beats even the live vendor API", () => {
  const existing = { version: 1, vendors: { openai: [{ id: "e", label: "E", kind: "EMBEDDING" }] } };
  const { vendors } = baseMerge({
    existing,
    sources: [{ sourceId: "openai-api", drafts: [{ vendor: "openai", id: "e", kind: "EMBEDDING", embeddingDimensions: 999 }] }],
    overrides: [{ vendor: "openai", id: "e", embeddingDimensions: 1536, __pin: true }],
  });
  assert.equal(vendors.openai[0].embeddingDimensions, 1536);
});

test("merge positive-evidence removal: live API omitting an id drops it (unless pinned)", () => {
  const existing = { version: 1, vendors: { openai: [
    { id: "kept", label: "Kept", kind: "CHAT" },
    { id: "gone", label: "Gone", kind: "CHAT" },
    { id: "pinned", label: "Pinned", kind: "CHAT" },
  ] } };
  const { vendors, removed } = baseMerge({
    existing,
    sources: [{ sourceId: "openai-api", drafts: [{ vendor: "openai", id: "kept", kind: "CHAT" }] }],
    overrides: [{ vendor: "openai", id: "pinned", kind: "CHAT" }],
    liveIdsByVendor: new Map([["openai", new Set(["kept"])]]),
  });
  const ids = vendors.openai.map((e) => e.id).sort();
  assert.deepEqual(ids, ["kept", "pinned"], "kept + override-protected survive; gone removed");
  assert.equal(removed.length, 1);
  assert.equal(removed[0].id, "gone");
});

test("validateEnvelope: flags bad kind, duplicate id, invalid numeric", () => {
  const errs = validateEnvelope({ version: 1, lastUpdated: WHEN, vendors: { openai: [
    { id: "a", label: "A", kind: "NOPE" },
    { id: "b", label: "B", kind: "CHAT", contextWindow: 0 },
    { id: "b", label: "B2", kind: "CHAT" },
  ] } });
  assert.ok(errs.some((e) => /invalid kind/.test(e)));
  assert.ok(errs.some((e) => /invalid contextWindow/.test(e)));
  assert.ok(errs.some((e) => /duplicate id/.test(e)));
});

test("validateEnvelope: a clean envelope passes", () => {
  const errs = validateEnvelope({ version: 1, lastUpdated: WHEN, vendors: { openai: [{ id: "a", label: "A", kind: "CHAT", status: "GA" }] } });
  assert.deepEqual(errs, []);
});

test("diffReport: counts add/remove/change", () => {
  const existing = { vendors: { openai: [{ id: "old", label: "Old", kind: "CHAT" }, { id: "chg", label: "Chg", kind: "CHAT" }] } };
  const proposed = { vendors: { openai: [{ id: "chg", label: "Chg", kind: "CHAT", contextWindow: 100, sources: ["litellm"] }, { id: "new", label: "New", kind: "CHAT", sources: ["openai-api"] }] } };
  const { stats } = diffReport(existing, proposed, {});
  assert.equal(stats.added, 1);
  assert.equal(stats.removed, 1);
  assert.equal(stats.changed, 1);
});
