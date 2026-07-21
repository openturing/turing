/**
 * SourceAdapter registry (Block BD / T765). Each adapter implements the contract
 *   { id, vendor|null, envKey|null, label, fetch(env, ctx), normalize(raw) -> draft[] }
 * where a draft is a partial ModelEntry carrying its target `vendor` + `id`.
 * Order here is display-only; precedence lives in lib/merge.mjs.
 *
 * Extended/self-hosted sources (Ollama, Bedrock ListFoundationModels,
 * HuggingFace) are deliberately deferred to T771 behind this same contract.
 *
 * @since 2026.3.4 (T765)
 */
import openai from "./openai.mjs";
import anthropic from "./anthropic.mjs";
import gemini from "./gemini.mjs";
import cohere from "./cohere.mjs";
import mistral from "./mistral.mjs";
import litellm from "./litellm.mjs";

export const ADAPTERS = [openai, anthropic, gemini, cohere, mistral, litellm];
