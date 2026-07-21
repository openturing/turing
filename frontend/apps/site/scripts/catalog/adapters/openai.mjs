/**
 * OpenAI live-API fetcher (Block BD / T766). Normalizes GET /v1/models — which
 * carries only ids — into ModelEntry drafts, inferring kind heuristically since
 * the endpoint is metadata-poor. Enrichment (context window, capabilities) comes
 * from LiteLLM/overrides at merge time.
 *
 * @since 2026.3.4 (T766)
 */
import { classifyKind, compact, fetchOrReplay } from "../lib/util.mjs";

export default {
  id: "openai-api",
  vendor: "openai",
  envKey: "OPENAI_API_KEY",
  label: "OpenAI /v1/models",

  async fetch(env, ctx) {
    return fetchOrReplay(this.id, "https://api.openai.com/v1/models", {
      headers: { Authorization: `Bearer ${env.OPENAI_API_KEY}` },
      offline: ctx.offline,
      when: ctx.when,
    });
  },

  normalize(raw) {
    const data = Array.isArray(raw?.data) ? raw.data : [];
    return data
      .map((m) => m?.id)
      .filter((id) => typeof id === "string" && !id.startsWith("ft:"))
      // No label: OpenAI's listing echoes the id, which is worse than a curated
      // label — leave it to the committed catalog / overrides (falls back to id).
      .map((id) => compact({ vendor: "openai", id, kind: classifyKind(id) }));
  },
};
