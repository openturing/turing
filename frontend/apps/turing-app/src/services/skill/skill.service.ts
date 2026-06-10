import axios from "axios";
import type {
  TurSkillFileContent,
  TurSkillFileNode,
  TurSkillSummary,
} from "@/models/skill/skill.model";

/**
 * T318 — REST client for skill folders (`/api/skill`). The catalog list powers
 * the `/admin/skill` page; the file operations power the mini-VS-Code editor,
 * all addressed by paths relative to the skill root.
 */
export class TurSkillService {
  async query(): Promise<TurSkillSummary[]> {
    const response = await axios.get<TurSkillSummary[]>("/skill");
    return response.data;
  }

  async get(id: string): Promise<TurSkillSummary> {
    const response = await axios.get<TurSkillSummary>(`/skill/${id}`);
    return response.data;
  }

  async create(name: string): Promise<TurSkillSummary> {
    const response = await axios.post<TurSkillSummary>("/skill", null, { params: { name } });
    return response.data;
  }

  async setEnabled(id: string, value: boolean): Promise<TurSkillSummary> {
    const response = await axios.put<TurSkillSummary>(`/skill/${id}/enabled`, null, { params: { value } });
    return response.data;
  }

  async reindex(): Promise<{ enabled: boolean; indexed: number }> {
    const response = await axios.post<{ enabled: boolean; indexed: number }>("/skill/reindex");
    return response.data;
  }

  async importZip(file: File): Promise<string[]> {
    const formData = new FormData();
    formData.append("file", file);
    const response = await axios.post<string[]>("/skill/import", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return response.data;
  }

  /** Download a skill folder as an Anthropic-compatible ZIP (triggers a browser download). */
  async download(id: string, name: string): Promise<void> {
    const response = await axios.get<Blob>(`/skill/${id}/export`, { responseType: "blob" });
    const safe = (name || "skill").replace(/[^A-Za-z0-9._-]/g, "-").toLowerCase();
    const url = URL.createObjectURL(response.data);
    try {
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${safe}.zip`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
    } finally {
      setTimeout(() => URL.revokeObjectURL(url), 0);
    }
  }

  // ---- file tree (scoped to the skill root) ----

  async listFiles(id: string): Promise<TurSkillFileNode[]> {
    const response = await axios.get<TurSkillFileNode[]>(`/skill/${id}/files`);
    return response.data;
  }

  async readFile(id: string, path: string): Promise<TurSkillFileContent> {
    const response = await axios.get<TurSkillFileContent>(`/skill/${id}/file`, { params: { path } });
    return response.data;
  }

  async writeFile(id: string, path: string, content: string): Promise<void> {
    await axios.put(`/skill/${id}/file`, { content }, { params: { path } });
  }

  async createFolder(id: string, path: string): Promise<void> {
    await axios.post(`/skill/${id}/folder`, null, { params: { path } });
  }

  async rename(id: string, from: string, to: string): Promise<void> {
    await axios.post(`/skill/${id}/rename`, null, { params: { from, to } });
  }

  async deletePath(id: string, path: string): Promise<void> {
    await axios.delete(`/skill/${id}/file`, { params: { path } });
  }
}
