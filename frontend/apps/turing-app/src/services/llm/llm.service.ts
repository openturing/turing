import axios from "axios";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts";
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
}
