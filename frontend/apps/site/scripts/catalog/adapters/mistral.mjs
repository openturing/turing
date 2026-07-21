/**
 * Mistral live-API fetcher (Block BD / T766). GET /v1/models carries a
 * `capabilities` object (completion_chat, function_calling, vision) and
 * max_context_length; kind is EMBEDDING for embed ids, else CHAT.
 *
 * @since 2026.3.4 (T766)
 */
import { compact, fetchOrReplay } from "../lib/util.mjs";

export default {
  id: "mistral-api",
  vendor: "mistral",
  envKey: "MISTRAL_API_KEY",
  label: "Mistral /v1/models",

  async fetch(env, ctx) {
    return fetchOrReplay(this.id, "https://api.mistral.ai/v1/models", {
      headers: { Authorization: `Bearer ${env.MISTRAL_API_KEY}` },
      offline: ctx.offline,
      when: ctx.when,
    });
  },

  normalize(raw) {
    const data = Array.isArray(raw?.data) ? raw.data : [];
    return data
      .filter((m) => typeof m?.id === "string")
      .map((m) => {
        const caps = m.capabilities || {};
        const capabilities = [];
        if (caps.function_calling) capabilities.push("tools");
        if (caps.vision) capabilities.push("vision");
        // No label: Mistral's `name` is often the id or null — leave the display
        // label to the committed catalog / overrides (falls back to id).
        return compact({
          vendor: "mistral",
          id: m.id,
          kind: /embed/i.test(m.id) ? "EMBEDDING" : "CHAT",
          contextWindow: Number.isInteger(m.max_context_length) ? m.max_context_length : undefined,
          capabilities,
        });
      });
  },
};
