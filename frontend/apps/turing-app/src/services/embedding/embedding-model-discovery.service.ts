import axios from "axios";
import type {
  TurEmbeddingDimensionCheck,
  TurHuggingFaceModelList,
} from "@/models/embedding/huggingface-model.model.ts";
import type {
  TurLlmModelList,
  TurLlmModelListRequest,
} from "@/models/llm/llm-model-option.model.ts";

/**
 * Fetches the selectable models for the provider-aware embedding-model picker
 * (T626). Two sources plus the manual local path:
 *
 * - {@link listHuggingFaceModels} — ONNX-verified HuggingFace repos (T624).
 * - {@link listLlmEmbeddingModels} — embedding-capable models for an LLM
 *   vendor (T625), the same combobox the chat LLM form has, filtered to
 *   embeddings.
 */
export class TurEmbeddingModelDiscoveryService {
  async listHuggingFaceModels(query?: string): Promise<TurHuggingFaceModelList> {
    const response = await axios.get<TurHuggingFaceModelList>("/embedding-model/hf-models", {
      params: query ? { query } : undefined,
    });
    return response.data;
  }

  async listLlmEmbeddingModels(request: TurLlmModelListRequest): Promise<TurLlmModelList> {
    const response = await axios.post<TurLlmModelList>("/embedding-model/llm-models", request);
    return response.data;
  }

  async checkHuggingFaceDimension(repoId: string): Promise<TurEmbeddingDimensionCheck> {
    const response = await axios.get<TurEmbeddingDimensionCheck>(
      "/embedding-model/hf-dimension-check",
      { params: { repoId } },
    );
    return response.data;
  }

  async listHuggingFaceVariants(repoId: string): Promise<string[]> {
    const response = await axios.get<string[]>("/embedding-model/hf-variants", {
      params: { repoId },
    });
    return response.data;
  }
}
