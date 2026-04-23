import type { TurLLMInstance } from "../llm/llm-instance.model.ts";

export interface TurEmbeddingModel {
  id: string;
  modelName: string;
  description: string;
  providerType: string;
  turLLMInstance?: TurLLMInstance;
  modelReference: string;
  batchSize?: number;
  modelPath?: string;
  tokenizerPath?: string;
  enabled: number;
  icon?: string | null;
}
