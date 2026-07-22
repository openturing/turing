/**
 * The kind of task a model performs (T750), used to badge and filter the
 * picker. Mirrors the backend `TurLlmModelKind` enum.
 */
export type TurLlmModelKind =
  | "CHAT"
  | "EMBEDDING"
  | "RERANK"
  | "IMAGE"
  | "TRANSCRIPTION"
  | "SPEECH"
  | "VIDEO"
  | "MODERATION"
  | "UNKNOWN";

/** Per-1M-token indicative pricing carried from the model catalog (T776). */
export interface TurLlmModelPricing {
  inputPer1M?: number | null;
  outputPer1M?: number | null;
  currency?: string | null;
  indicative?: boolean | null;
  source?: string | null;
  lastVerified?: string | null;
}

/** Quality benchmark signals carried from the model catalog (T776). */
export interface TurLlmModelBenchmarks {
  intelligenceIndex?: number | null;
  arenaElo?: number | null;
}

/** Runtime performance signals carried from the model catalog (T776). */
export interface TurLlmModelPerformance {
  throughputTps?: number | null;
  latencyTtftSec?: number | null;
}

/** Supported input/output modalities carried from the model catalog (T776). */
export interface TurLlmModelModalities {
  input?: string[] | null;
  output?: string[] | null;
}

/**
 * Rich, catalog-sourced metadata carried alongside a model option (T776).
 * Every field is optional — a model discovered by a live vendor listing (no
 * catalog entry) has no metadata, and a catalog entry may omit any field.
 * Treat absence as "unknown", never as a default (a missing price is not `$0`).
 */
export interface TurLlmModelMetadata {
  contextWindow?: number | null;
  maxOutputTokens?: number | null;
  embeddingDimensions?: number | null;
  capabilities?: string[] | null;
  modalities?: TurLlmModelModalities | null;
  pricing?: TurLlmModelPricing | null;
  benchmarks?: TurLlmModelBenchmarks | null;
  performance?: TurLlmModelPerformance | null;
  knowledgeCutoff?: string | null;
  releaseDate?: string | null;
  deprecated?: boolean | null;
  status?: string | null;
  /** Classifier tier: Frontier / High / Mid / Light. */
  tier?: string | null;
  tags?: string[] | null;
}

/**
 * A selectable model for the LLM-instance model picker (T577).
 * `id` is stored on `modelName`; `label` is the friendly dropdown text;
 * `kind` is the classified model type (T750) for the badge + type filter;
 * `metadata` (T776) carries the rich catalog facts (pricing, limits,
 * capabilities, benchmarks…) when the model came from the catalog.
 */
export interface TurLlmModelOption {
  id: string;
  label: string;
  kind?: TurLlmModelKind;
  metadata?: TurLlmModelMetadata | null;
}

/** Where the model list came from, for badging the picker. */
export type TurLlmModelSource = "LIVE" | "CATALOG" | "NONE";

export interface TurLlmModelList {
  source: TurLlmModelSource;
  models: TurLlmModelOption[];
}

/** Request payload for POST /llm/models. */
export interface TurLlmModelListRequest {
  vendorId: string;
  instanceId?: string;
  apiKey?: string;
  url?: string;
  providerOptionsJson?: string;
}
