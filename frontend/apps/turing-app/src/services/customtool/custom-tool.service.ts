import axios from "axios";
import type { TurCustomTool } from "@/models/customtool/custom-tool.model.ts";

export interface ValidateScriptResponse {
  valid: boolean;
  error?: string | null;
  line?: number | null;
  column?: number | null;
}

/**
 * @since 2026.2.5
 */
export class TurCustomToolService {
  async query(): Promise<TurCustomTool[]> {
    const response = await axios.get<TurCustomTool[]>("/custom-tool");
    return response.data;
  }
  async get(id: string): Promise<TurCustomTool> {
    const response = await axios.get<TurCustomTool>(`/custom-tool/${id}`);
    return response.data;
  }
  async create(tool: TurCustomTool): Promise<TurCustomTool> {
    const response = await axios.post<TurCustomTool>("/custom-tool", tool);
    return response.data;
  }
  async update(tool: TurCustomTool): Promise<TurCustomTool> {
    const response = await axios.put<TurCustomTool>(`/custom-tool/${tool.id.toString()}`, tool);
    return response.data;
  }
  async delete(tool: TurCustomTool): Promise<boolean> {
    const response = await axios.delete<TurCustomTool>(`/custom-tool/${tool.id.toString()}`);
    return response.status == 200;
  }
  async validate(script: string): Promise<ValidateScriptResponse> {
    const response = await axios.post<ValidateScriptResponse>("/custom-tool/validate", { script });
    return response.data;
  }
}
