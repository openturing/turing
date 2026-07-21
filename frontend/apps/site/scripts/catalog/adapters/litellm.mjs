/**
 * LiteLLM community-registry cross-source (Block BD / T767). Ingests
 * `model_prices_and_context_window.json` as a vendor-agnostic enrichment layer
 * for the numeric/capability fields the live vendor APIs leave blank
 * (contextWindow, maxOutputTokens, modalities, capability hints). No key needed.
 *
 * **Strictly non-pricing** (STRATEGY §X.5): every `*cost*` / `*price*` key is
 * dropped — this catalog is an identity+kind reference, never a price list.
 *
 * @since 2026.3.4 (T767)
 */
import { compact, fetchOrReplay } from "../lib/util.mjs";

const URL =
  "https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json";

/** litellm_provider → our catalog vendor key. Unmapped providers are dropped. */
const PROVIDER_TO_VENDOR = {
  openai: "openai",
  text_completion_openai: "openai",
  anthropic: "anthropic",
  gemini: "gemini",
  cohere: "cohere",
  cohere_chat: "cohere",
  mistral: "mistral",
  bedrock: "bedrock",
  bedrock_converse: "bedrock",
  vertex_ai: "vertex-ai",
  "vertex_ai-language-models": "vertex-ai",
  "vertex_ai-chat-models": "vertex-ai",
  "vertex_ai-embedding-models": "vertex-ai",
  voyage: "voyage",
  ollama: "ollama",
};

const MODE_TO_KIND = {
  chat: "CHAT",
  completion: "CHAT",
  responses: "CHAT",
  embedding: "EMBEDDING",
  rerank: "RERANK",
  image_generation: "IMAGE",
  audio_transcription: "TRANSCRIPTION",
  audio_speech: "SPEECH",
  moderation: "MODERATION",
  moderations: "MODERATION",
};

function modalitiesFrom(spec) {
  const input = new Set(["text"]);
  const output = new Set();
  if (spec.supports_vision || (spec.supported_modalities || []).includes?.("image")) input.add("image");
  if (spec.supports_pdf_input) input.add("pdf");
  if (spec.supports_audio_input) input.add("audio");
  for (const m of spec.supported_modalities || []) input.add(m);
  for (const m of spec.supported_output_modalities || []) output.add(m);
  if (spec.mode === "embedding") output.add("embedding");
  const res = {};
  if (input.size) res.input = [...input];
  if (output.size) res.output = [...output];
  return Object.keys(res).length ? res : undefined;
}

export default {
  id: "litellm",
  vendor: null, // multi-vendor enrichment source
  envKey: null, // public, no auth
  label: "LiteLLM registry (non-pricing)",

  async fetch(_env, ctx) {
    return fetchOrReplay(this.id, URL, { offline: ctx.offline, when: ctx.when });
  },

  normalize(raw) {
    if (!raw || typeof raw !== "object") return [];
    const drafts = [];
    for (const [key, spec] of Object.entries(raw)) {
      if (key === "sample_spec" || !spec || typeof spec !== "object") continue;
      if (key.includes("*")) continue; // wildcard rate entries, not real ids
      const vendor = PROVIDER_TO_VENDOR[spec.litellm_provider];
      if (!vendor) continue; // provider we don't track
      // Bare model id sent to the vendor (drop any "provider/" prefix).
      const id = key.includes("/") ? key.slice(key.indexOf("/") + 1) : key;
      const capabilities = [];
      if (spec.supports_function_calling) capabilities.push("tools");
      if (spec.supports_vision) capabilities.push("vision");
      if (spec.supports_reasoning) capabilities.push("reasoning");
      drafts.push(
        compact({
          vendor,
          id,
          // No label from litellm — it is keyed by id; leave label to vendor/overrides.
          kind: MODE_TO_KIND[spec.mode] || undefined,
          contextWindow: Number.isInteger(spec.max_input_tokens)
            ? spec.max_input_tokens
            : Number.isInteger(spec.max_tokens)
              ? spec.max_tokens
              : undefined,
          maxOutputTokens: Number.isInteger(spec.max_output_tokens) ? spec.max_output_tokens : undefined,
          embeddingDimensions: Number.isInteger(spec.output_vector_size) ? spec.output_vector_size : undefined,
          capabilities,
          modalities: modalitiesFrom(spec),
        }),
      );
    }
    return drafts;
  },
};
