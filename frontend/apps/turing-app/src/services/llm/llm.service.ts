import axios from "axios";
import type {
  TurLLMInstance,
  TurLlmCatalogChangeNotification,
  TurLlmDeprecatedModelUsage,
} from "@/models/llm/llm-instance.model.ts";
import type {
  TurLlmAdvisorQuery,
  TurLlmModelRecommendation,
} from "@/models/llm/llm-model-advisor.model.ts";
import type { TurModelVerifyResult } from "@/models/verify.model.ts";

export class TurLLMInstanceService {
  async query(): Promise<TurLLMInstance[]> {
    const response = await axios.get<TurLLMInstance[]>("/llm");
    return response.data;
  }
  async get(id: string): Promise<TurLLMInstance> {
    const response = await axios.get<TurLLMInstance>(`/llm/${id}`);
    return response.data;
  }
  async create(turLLMInstance: TurLLMInstance): Promise<TurLLMInstance> {
    const response = await axios.post<TurLLMInstance>("/llm",
      turLLMInstance
    );
    return response.data;
  }
  async update(turLLMInstance: TurLLMInstance): Promise<TurLLMInstance> {
    const response = await axios.put<TurLLMInstance>(
      `/llm/${turLLMInstance.id.toString()}`,
      turLLMInstance
    );
    return response.data;
  }
  async delete(turLLMInstance: TurLLMInstance): Promise<boolean> {
    const response = await axios.delete<TurLLMInstance>(
      `/llm/${turLLMInstance.id.toString()}`
    );
    return  response.status == 200;
  }
  /** Fires a live probe against the saved instance to confirm it works. */
  async verify(id: string): Promise<TurModelVerifyResult> {
    const response = await axios.post<TurModelVerifyResult>(`/llm/${id}/verify`);
    return response.data;
  }

  /**
   * T782 — instances whose configured model the public catalog marks
   * deprecated/retired, each with a suggested GA replacement when one exists.
   */
  async deprecations(): Promise<TurLlmDeprecatedModelUsage[]> {
    const response = await axios.get<TurLlmDeprecatedModelUsage[]>(
      "/v2/llm/model-lifecycle/deprecations",
    );
    return response.data ?? [];
  }

  /** T788 — catalog change-feed notifications for configured (in-use) models. */
  async changeFeed(): Promise<TurLlmCatalogChangeNotification[]> {
    const response = await axios.get<TurLlmCatalogChangeNotification[]>(
      "/v2/llm/model-lifecycle/change-feed",
    );
    return response.data ?? [];
  }

  /** T785 — rank catalog models that satisfy the given constraints. */
  async recommendModels(query: TurLlmAdvisorQuery): Promise<TurLlmModelRecommendation[]> {
    const response = await axios.post<TurLlmModelRecommendation[]>(
      "/v2/llm/model-advisor/recommend",
      query,
    );
    return response.data ?? [];
  }
}
