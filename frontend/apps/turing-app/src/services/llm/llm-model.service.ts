import axios from "axios";
import type {
  TurLlmModelList,
  TurLlmModelListRequest,
} from "@/models/llm/llm-model-option.model.ts";

/**
 * Fetches the selectable models for an LLM vendor (T577). The backend serves
 * a live list from the vendor API when a key is available, otherwise the
 * bundled static catalog.
 */
export class TurLLMModelService {
  async listModels(request: TurLlmModelListRequest): Promise<TurLlmModelList> {
    const response = await axios.post<TurLlmModelList>("/llm/models", request);
    return response.data;
  }
}
