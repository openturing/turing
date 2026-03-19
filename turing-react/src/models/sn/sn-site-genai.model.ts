import type { TurEmbeddingModel } from "../embedding/embedding-model.model.ts";
import type { TurLLMInstance } from "../llm/llm-instance.model.ts";
import type { TurStoreInstance } from "../store/store-instance.model.ts";

export interface TurSNSiteGenAi {
  id: string;
  turLLMInstance: TurLLMInstance;
  turStoreInstance: TurStoreInstance;
  turEmbeddingModelInstance: TurEmbeddingModel;
  enabled: boolean;
  systemPrompt: string;
  sitePrompt?: string;
}
