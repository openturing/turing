import type { TurSNSiteStatus } from "@/models/sn/sn-site-monitoring.model.ts";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import axios from "axios";

export class TurSNSiteService {
  async query(): Promise<TurSNSite[]> {
    const response = await axios.get<TurSNSite[]>("/sn");
    return response.data;
  }
  async get(id: string): Promise<TurSNSite> {
    const response = await axios.get<TurSNSite>(`/sn/${id}`);
    return response.data;
  }

  async getStatus(id: string): Promise<TurSNSiteStatus> {
    const response = await axios.get<TurSNSiteStatus>(`/sn/${id}/monitoring`);
    return response.data;
  }

  async nameExists(name: string, excludeId?: string): Promise<boolean> {
    const params: Record<string, string> = { name };
    if (excludeId) params.excludeId = excludeId;
    const response = await axios.get<{ exists: boolean }>("/sn/name-exists", { params });
    return response.data.exists;
  }
  async create(turSNSite: TurSNSite): Promise<TurSNSite> {
    const response = await axios.post<TurSNSite>("/sn", turSNSite);
    return response.data;
  }
  async update(turSNSite: TurSNSite): Promise<TurSNSite> {
    const response = await axios.put<TurSNSite>(
      `/sn/${turSNSite.id.toString()}`,
      turSNSite,
    );
    return response.data;
  }
  async delete(turSNSite: TurSNSite): Promise<boolean> {
    const response = await axios.delete<TurSNSite>(
      `/sn/${turSNSite.id.toString()}`,
    );
    return response.status == 200;
  }
  async exportAll(): Promise<Blob | null> {
    const response = await axios
      .get<Blob>("/sn/export", {
        responseType: "blob",
      })
      .then((res) => res.data)
      .catch((error) => {
        console.error("Failed to export all SN sites", error);
        return null;
      });
    return response;
  }
  async export(turSNSite: TurSNSite): Promise<Blob | null> {
    const response = await axios
      .get<Blob>(`/sn/${turSNSite.id.toString()}/export`, {
        responseType: "blob",
      })
      .then((res) => res.data)
      .catch((error) => {
        console.error("Failed to export SN site", error);
        return null;
      });
    return response;
  }

  async exportAsync(siteId: string, taskId: string): Promise<string | null> {
    const response = await axios
      .post<{ taskId: string }>(`/sn/${siteId}/export/async`, null, {
        params: { taskId },
      })
      .then((res) => res.data.taskId)
      .catch((error) => {
        console.error("Failed to start async export", error);
        return null;
      });
    return response;
  }

  async downloadExport(taskId: string): Promise<Blob | null> {
    const response = await axios
      .get<Blob>(`/sn/export/download/${taskId}`, {
        responseType: "blob",
      })
      .then((res) => res.data)
      .catch((error) => {
        console.error("Failed to download export", error);
        return null;
      });
    return response;
  }

  async subscribeExportProgress(
    taskId: string,
    onProgress: (data: ContentExchangeProgress) => void,
    onComplete: () => void,
    onError?: (error: unknown) => void,
  ): Promise<void> {
    const baseUrl = axios.defaults.baseURL || "/api";
    try {
      const response = await fetch(
        `${baseUrl}/sn/export/progress/${taskId}`,
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

export interface ContentExchangeProgress {
  totalDocuments: number;
  processedDocuments: number;
  percentage: number;
  currentLocale: string;
  phase: string;
  estimatedRemainingMillis: number;
}
