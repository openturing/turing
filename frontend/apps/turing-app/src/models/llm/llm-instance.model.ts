import type { TurLLMVendor } from "./llm-vendor.model.ts";

export interface TurLLMInstance {
  id: string;
  title: string;
  description: string;
  url: string;
  turLLMVendor: TurLLMVendor;
  language: string;
  enabled: number;
  /** The single default model used everywhere the platform is single-model. */
  modelName: string;
  /**
   * Comma-separated list of models this instance may serve. `modelName` is the
   * default (and must be one of these). Blank/undefined on legacy instances,
   * where only `modelName` applies.
   */
  modelNames?: string;
  /** Per-kind default models (T757 / ADR 0004): the embedding + rerank defaults
   * this instance serves. Optional — a chat-only instance leaves them blank. */
  embeddingModelName?: string;
  rerankModelName?: string;
  /** In-process ONNX embedding serving fields (T772/T773) — set only for the
   * TRANSFORMERS_LOCAL (paths) / HUGGINGFACE (repo id via embeddingModelName) vendors. */
  embeddingModelPath?: string;
  embeddingTokenizerPath?: string;
  embeddingBatchSize?: number;
  temperature: number;
  topK: number;
  topP: number;
  repeatPenalty: number;
  seed: number;
  numPredict: number;
  stop: string;
  responseFormat: string;
  supportedCapabilities: string;
  timeout: string;
  maxRetries: number;
  contextWindow: number;
  toolsEnabled: boolean;
  /**
   * T431 — allow sending raw document binaries (PDF/DOCX) to this provider's
   * native file input for document-to-slot extraction (T103) instead of Tika
   * text extraction. Only enable for providers that support document input
   * (Anthropic / Gemini / OpenAI).
   */
  fileUploadEnabled?: boolean;
  apiKey?: string;
  providerOptionsJson?: string;
  icon?: string | null;
  /** T372 — null for the shared GLOBAL BYO-infra pool (read-only when tenancy is on). */
  tenantId?: string | null;
}
