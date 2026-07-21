/**
 * Anthropic live-API fetcher (Block BD / T766). GET /v1/models returns id +
 * display_name + created_at; every entry is CHAT. Context window / capabilities
 * are filled from LiteLLM/overrides at merge time (the API omits them).
 *
 * @since 2026.3.4 (T766)
 */
import { compact, fetchOrReplay } from "../lib/util.mjs";

function toDate(iso) {
  if (typeof iso !== "string") return undefined;
  const d = iso.slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/.test(d) ? d : undefined;
}

export default {
  id: "anthropic-api",
  vendor: "anthropic",
  envKey: "ANTHROPIC_API_KEY",
  label: "Anthropic /v1/models",

  async fetch(env, ctx) {
    return fetchOrReplay(this.id, "https://api.anthropic.com/v1/models?limit=100", {
      headers: {
        "x-api-key": env.ANTHROPIC_API_KEY,
        "anthropic-version": "2023-06-01",
      },
      offline: ctx.offline,
      when: ctx.when,
    });
  },

  normalize(raw) {
    const data = Array.isArray(raw?.data) ? raw.data : [];
    return data
      .filter((m) => typeof m?.id === "string")
      .map((m) =>
        compact({
          vendor: "anthropic",
          id: m.id,
          label: m.display_name || m.id,
          kind: "CHAT",
          releaseDate: toDate(m.created_at),
        }),
      );
  },
};
