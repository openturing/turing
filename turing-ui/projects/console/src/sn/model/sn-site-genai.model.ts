import {TurLLMInstance} from "../../llm/model/llm-instance.model";
import {TurStoreInstance} from "../../store/model/store-instance.model";

export interface TurSNSiteGenAi {
  id: string;
  turLLMInstance: TurLLMInstance;
  turStoreInstance: TurStoreInstance;
  enabled: boolean;
  systemPrompt: string;
}
