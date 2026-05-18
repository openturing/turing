import type { TurImportResult } from "@/models/marketplace/import-result.model";
import type { ContentExchangeProgress } from "@/services/sn/sn.service";
import axios from "axios";

export interface SiteConflict {
  id: string;
  name: string;
}

export interface AgentConflict {
  id: string;
  name: string;
}

export interface ZipCheckResult {
  hasContent: boolean;
  hasTemplate: boolean;
  templateName: string | null;
  conflicts: SiteConflict[];
  /** True when the ZIP envelope carries an {@code agents[]} block. @since 2026.2.8 */
  hasAgents: boolean;
  /** Titles of every agent present in the bundle (in envelope order). */
  agentTitles: string[];
  /** Agents that already exist in the target DB (matched by ID) — same UX as {@link conflicts}. */
  agentConflicts: AgentConflict[];
}

export class TurExchangeImportService {
  async importFile(
    file: File,
    onProgress?: (progress: number) => void,
    includeContent = false,
    includeTemplate = false,
    taskId?: string,
    overwrite = false,
  ): Promise<TurImportResult> {
    const formData = new FormData();
    formData.append("file", file);

    const params: Record<string, string | boolean> = { includeContent, includeTemplate, overwrite };
    if (taskId) params.taskId = taskId;

    const response = await axios.post<TurImportResult>("/import", formData, {
      params,
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          const percent = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total,
          );
          onProgress(percent);
        }
      },
    });
    return response.data;
  }

  async checkZip(file: File): Promise<ZipCheckResult> {
    const formData = new FormData();
    formData.append("file", file);
    const response = await axios.post<ZipCheckResult>(
      "/import/check-zip",
      formData,
    );
    return response.data;
  }

  async checkContent(file: File): Promise<boolean> {
    const result = await this.checkZip(file);
    return result.hasContent;
  }

  async subscribeImportProgress(
    taskId: string,
    onProgress: (data: ContentExchangeProgress) => void,
    onComplete: () => void,
    onError?: (error: unknown) => void,
  ): Promise<void> {
    const baseUrl = axios.defaults.baseURL || "/api";
    try {
      const response = await fetch(
        `${baseUrl}/import/progress/${taskId}`,
        { credentials: "include" },
      );
      if (!response.ok || !response.body) {
        onError?.(new Error(`SSE failed: ${response.status}`));
        return;
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";
        for (const line of lines) {
          if (line.startsWith("data:")) {
            const json = line.slice(5).trim();
            if (json) {
              const data: ContentExchangeProgress = JSON.parse(json);
              onProgress(data);
              if (data.phase === "completed") {
                onComplete();
                return;
              }
            }
          }
        }
      }
      onComplete();
    } catch (e) {
      onError?.(e);
    }
  }
}
