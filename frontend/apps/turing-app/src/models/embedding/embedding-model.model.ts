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
  /** T372 — null for the shared GLOBAL BYO-infra pool (read-only when tenancy is on). */
  tenantId?: string | null;
}
