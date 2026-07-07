import type { TurSNSiteListItem } from "@/models/sn/sn-site-list-item.model.ts";
import type { TurSNSiteStatus } from "@/models/sn/sn-site-monitoring.model.ts";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import type { TurRagBm25CoreStatus } from "@/models/sn/sn-site-genai.model.ts";
import type { TurSNFieldCoverageReport } from "@/models/sn/sn-field-coverage.model.ts";
import type { TurSNContentFitReport } from "@/models/sn/sn-content-fit.model.ts";
import axios from "axios";

export class TurSNSiteService {
  async query(): Promise<TurSNSiteListItem[]> {
    const response = await axios.get<TurSNSiteListItem[]>("/sn");
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
  async export(turSNSite: TurSNSite, includeTemplate = false): Promise<Blob | null> {
    const response = await axios
      .get<Blob>(`/sn/${turSNSite.id.toString()}/export`, {
        responseType: "blob",
        params: { includeTemplate },
      })
      .then((res) => res.data)
      .catch((error) => {
        console.error("Failed to export SN site", error);
        return null;
      });
    return response;
  }

  async exportAsync(siteId: string, taskId: string, includeTemplate = false): Promise<string | null> {
    const response = await axios
      .post<{ taskId: string }>(`/sn/${siteId}/export/async`, null, {
        params: { taskId, includeTemplate },
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

  async reindexGenAi(siteId: string, taskId: string): Promise<string | null> {
    return axios
      .post<{ taskId: string }>(`/sn/${siteId}/genai/reindex`, null, {
        params: { taskId },
      })
      .then((res) => res.data.taskId)
      .catch((error) => {
        console.error("Failed to start RAG reindex", error);
        return null;
      });
  }

  async getActiveReindex(siteId: string): Promise<ActiveReindexStatus | null> {
    try {
      const response = await axios.get<ActiveReindexStatus>(
        `/sn/${siteId}/genai/reindex/status`,
      );
      // 204 No Content → axios returns response with empty data
      if (response.status === 204 || !response.data) {
        return null;
      }
      return response.data;
    } catch (error) {
      console.error("Failed to fetch active reindex status", error);
      return null;
    }
  }

  /**
   * T24b / §III.2 — fetches per-locale BM25 core status for an SN site
   * (one row per `TurSNSiteLocale`). Used by the "Hybrid Retrieval" card
   * to render the status table when `ragBm25Source = SE_INSTANCE`.
   * Returns an empty array when the default embedding store isn't
   * configured (admin must finish RAG global settings first).
   */
  async getRagCores(siteId: string): Promise<TurRagBm25CoreStatus[]> {
    try {
      const response = await axios.get<TurRagBm25CoreStatus[]>(
        `/sn/${siteId}/rag/cores`,
      );
      return response.data ?? [];
    } catch (error) {
      console.error("Failed to fetch RAG cores", error);
      return [];
    }
  }

  /**
   * T24b — provisions every missing BM25 core for the SN site's locales.
   * Idempotent: already-PROVISIONED cores skip the create call, ERROR
   * rows are retried. Returns the refreshed status table so the UI can
   * update in one round-trip.
   */
  async provisionRagCores(siteId: string): Promise<TurRagBm25CoreStatus[]> {
    try {
      const response = await axios.post<TurRagBm25CoreStatus[]>(
        `/sn/${siteId}/rag/cores/provision`,
      );
      return response.data ?? [];
    } catch (error) {
      console.error("Failed to provision RAG cores", error);
      return [];
    }
  }

  /**
   * T24b — deprovisions every BM25 core registered for the SN site's
   * store. Invoked when admin reverts `ragBm25Source` back to EMBEDDED.
   */
  async deprovisionRagCores(siteId: string): Promise<boolean> {
    try {
      const response = await axios.delete(`/sn/${siteId}/rag/cores`);
      return response.status === 204 || response.status === 200;
    } catch (error) {
      console.error("Failed to deprovision RAG cores", error);
      return false;
    }
  }

  /**
   * T388 — per-field coverage / completeness report for an SN site. Returns
   * the total document count plus each enabled field's completeness, so admins
   * can see which fields a source under-fills.
   */
  async getFieldCoverage(siteId: string): Promise<TurSNFieldCoverageReport> {
    const response = await axios.get<TurSNFieldCoverageReport>(
      `/sn/${siteId}/field-coverage`,
    );
    return response.data;
  }

  /**
   * T472 — index-time audience content-fit coverage: how many indexed documents
   * are "too complex for their audience" vs a good fit.
   */
  async getContentFit(siteId: string): Promise<TurSNContentFitReport> {
    const response = await axios.get<TurSNContentFitReport>(
      `/sn/${siteId}/content-fit`,
    );
    return response.data;
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
  /**
   * Worker-pool size of the in-flight job (RAG reindex). Sequential flows
   * (export, import) report {@code 1}.
   */
  parallelism?: number;
}

export interface ActiveReindexStatus extends ContentExchangeProgress {
  taskId: string;
}
