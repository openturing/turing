/**
 * Gemini live-API fetcher (Block BD / T766). models.list is the richest vendor
 * source: supportedGenerationMethods drives kind (authoritative, mirroring the
 * backend's TurLlmModelListingSupport), and inputTokenLimit/outputTokenLimit give
 * exact context/output windows.
 *
 * @since 2026.3.4 (T766)
 */
import { compact, fetchOrReplay } from "../lib/util.mjs";

function kindFrom(methods, id) {
  const m = new Set(Array.isArray(methods) ? methods : []);
  if (m.has("embedContent") || m.has("batchEmbedContents")) return "EMBEDDING";
  if (m.has("generateContent") || m.has("streamGenerateContent")) return "CHAT";
  if (m.has("predict") || m.has("predictLongRunning")) {
    if (/veo/i.test(id)) return "VIDEO";
    if (/imagen|image/i.test(id)) return "IMAGE";
  }
  return "UNKNOWN";
}

export default {
  id: "gemini-api",
  vendor: "gemini",
  envKey: "GEMINI_API_KEY",
  label: "Gemini models.list",

  async fetch(env, ctx) {
    const key = env.GEMINI_API_KEY;
    return fetchOrReplay(
      this.id,
      `https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000&key=${key}`,
      { offline: ctx.offline, when: ctx.when },
    );
  },

  normalize(raw) {
    const models = Array.isArray(raw?.models) ? raw.models : [];
    return models
      .filter((m) => typeof m?.name === "string")
      .map((m) => {
        const id = m.name.replace(/^models\//, "");
        return compact({
          vendor: "gemini",
          id,
          label: m.displayName || id,
          kind: kindFrom(m.supportedGenerationMethods, id),
          contextWindow: Number.isInteger(m.inputTokenLimit) ? m.inputTokenLimit : undefined,
          maxOutputTokens: Number.isInteger(m.outputTokenLimit) ? m.outputTokenLimit : undefined,
        });
      });
  },
};
