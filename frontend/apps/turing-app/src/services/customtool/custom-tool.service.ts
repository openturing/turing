import axios from "axios";
import type { TurCustomTool } from "@/models/customtool/custom-tool.model.ts";
import type { CustomToolEditorDescriptor } from "@/models/customtool/custom-tool-descriptor.model.ts";

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
  /**
   * T40: editor descriptor used by the CodeMirror auto-complete extension on
   * the Custom Tool admin page. Lists every binding (`http`, `slots`, ...) and
   * their method signatures.
   */
  async getDescriptor(): Promise<CustomToolEditorDescriptor> {
    const response = await axios.get<CustomToolEditorDescriptor>("/custom-tool/descriptor");
    return response.data;
  }
  /**
   * T41: pushes the in-editor Groovy as a live-preview draft for the current
   * admin. The same admin's chat conversations will execute the draft instead
   * of the persisted version; visitors and other admins see no change.
   */
  async putDraft(id: string, groovyScript: string): Promise<DraftPutResponse> {
    const response = await axios.put<DraftPutResponse>(
      `/custom-tool/${id}/draft`, { groovyScript });
    return response.data;
  }
  /** T41: clears the calling admin's draft for this tool. */
  async clearDraft(id: string): Promise<DraftClearResponse> {
    const response = await axios.delete<DraftClearResponse>(`/custom-tool/${id}/draft`);
    return response.data;
  }
  /** T41: fetches the calling admin's current draft status (used on page mount). */
  async getDraft(id: string): Promise<DraftStatusResponse> {
    const response = await axios.get<DraftStatusResponse>(`/custom-tool/${id}/draft`);
    return response.data;
  }
}

export interface DraftPutResponse {
  success: boolean;
  error?: string | null;
  line?: number | null;
  column?: number | null;
  createdAt?: string | null;
}

export interface DraftClearResponse {
  cleared: boolean;
}

export interface DraftStatusResponse {
  active: boolean;
  groovyScript?: string | null;
  createdAt?: string | null;
  lastTouched?: string | null;
}
