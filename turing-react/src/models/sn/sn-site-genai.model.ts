import type { TurLLMInstance } from "../llm/llm-instance.model.ts";
import type { TurStoreInstance } from "../store/store-instance.model.ts";

export interface TurSNSiteGenAi {
  id: string;
  turLLMInstance: TurLLMInstance;
  turStoreInstance: TurStoreInstance;
  enabled: boolean;
  systemPrompt: string;
  sitePrompt?: string;
}
