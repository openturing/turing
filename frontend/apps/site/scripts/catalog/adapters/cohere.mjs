/**
 * Cohere live-API fetcher (Block BD / T766). GET /v1/models exposes an
 * `endpoints` array per model — the authoritative kind signal (mirrors the
 * backend's Cohere endpoint→kind mapping) — plus context_length.
 *
 * @since 2026.3.4 (T766)
 */
import { compact, fetchOrReplay } from "../lib/util.mjs";

function kindFrom(endpoints) {
  const e = new Set(Array.isArray(endpoints) ? endpoints : []);
  if (e.has("rerank")) return "RERANK";
  if (e.has("embed")) return "EMBEDDING";
  if (e.has("chat") || e.has("generate")) return "CHAT";
  return "UNKNOWN";
}

export default {
  id: "cohere-api",
  vendor: "cohere",
  envKey: "COHERE_API_KEY",
  label: "Cohere /v1/models",

  async fetch(env, ctx) {
    return fetchOrReplay(this.id, "https://api.cohere.com/v1/models?page_size=1000", {
      headers: { Authorization: `Bearer ${env.COHERE_API_KEY}` },
      offline: ctx.offline,
      when: ctx.when,
    });
  },

  normalize(raw) {
    const models = Array.isArray(raw?.models) ? raw.models : [];
    return models
      .filter((m) => typeof m?.name === "string")
      .map((m) =>
        // No label: `name` is the id — leave the display label to committed/overrides.
        compact({
          vendor: "cohere",
          id: m.name,
          kind: kindFrom(m.endpoints),
          contextWindow: Number.isInteger(m.context_length) ? m.context_length : undefined,
        }),
      );
  },
};
