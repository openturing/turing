/**
 * A selectable model for the LLM-instance model picker (T577).
 * `id` is stored on `modelName`; `label` is the friendly dropdown text.
 */
export interface TurLlmModelOption {
  id: string;
  label: string;
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
