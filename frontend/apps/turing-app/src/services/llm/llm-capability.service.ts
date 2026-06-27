import axios from "axios";
import type { TurLLMInstanceCapability } from "@/models/llm/llm-capability.model.ts";

/**
 * T132 / §X.2 — client for the per-LLM-instance native capability matrix
 * (`/api/llm/{instanceId}/capability`). `GET` returns the full catalogue merged
 * with the instance's persisted rows; `PUT` toggles/configures one capability.
 */
export class TurLLMInstanceCapabilityService {
  async query(instanceId: string): Promise<TurLLMInstanceCapability[]> {
    const response = await axios.get<TurLLMInstanceCapability[]>(
      `/llm/${instanceId}/capability`,
    );
    return response.data;
  }

  async upsert(
    instanceId: string,
    key: string,
    enabled: boolean,
    configJson: string | null,
  ): Promise<TurLLMInstanceCapability> {
    const response = await axios.put<TurLLMInstanceCapability>(
      `/llm/${instanceId}/capability/${key}`,
      { enabled, configJson },
    );
    return response.data;
  }

  async remove(instanceId: string, key: string): Promise<void> {
    await axios.delete(`/llm/${instanceId}/capability/${key}`);
  }
}
