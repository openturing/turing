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

/**
 * A selectable model for the LLM-instance model picker (T577).
 * `id` is stored on `modelName`; `label` is the friendly dropdown text;
 * `kind` is the classified model type (T750) for the badge + type filter.
 */
export interface TurLlmModelOption {
  id: string;
  label: string;
  kind?: TurLlmModelKind;
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
