import axios from "axios";
import { getCsrfToken } from "@/lib/axios";
import type {
  MatchMatrix,
  MatchPersonaRef,
  MatchProject,
  MatchProjectRequest,
  MatchProjectSummary,
  MatchRunEvent,
  MatchSource,
  MatchSourceRequest,
} from "@/models/persona/persona-match.model";

/** SSE callbacks for a streaming N×N analysis run. */
export interface MatchRunHandlers {
  onEvent: (event: MatchRunEvent) => void;
  onDone?: (event: MatchRunEvent) => void;
  onError?: (error: unknown, event?: MatchRunEvent) => void;
}

/**
 * Persona Match REST client (Block AT / §XLIII, T701). Wraps `/api/persona-match`:
 * project CRUD, the persona set, project-scoped content management, the
 * persisted matrix + report, and the N×N run — both the blocking form and the
 * live SSE stream that fills the heatmap cell-by-cell.
 */
export class TurPersonaMatchService {
  async list(): Promise<MatchProjectSummary[]> {
    const response = await axios.get<MatchProjectSummary[]>("/persona-match");
    return response.data;
  }

  async get(id: string): Promise<MatchProject> {
    const response = await axios.get<MatchProject>(`/persona-match/${id}`);
    return response.data;
  }

  async create(body: MatchProjectRequest): Promise<MatchProject> {
    const response = await axios.post<MatchProject>("/persona-match", body);
    return response.data;
  }

  async update(id: string, body: MatchProjectRequest): Promise<MatchProject> {
    const response = await axios.put<MatchProject>(`/persona-match/${id}`, body);
    return response.data;
  }

  async remove(id: string): Promise<boolean> {
    const response = await axios.delete(`/persona-match/${id}`);
    return response.status === 200;
  }

  async setPersonas(id: string, personaIds: string[]): Promise<MatchPersonaRef[]> {
    const response = await axios.put<MatchPersonaRef[]>(
      `/persona-match/${id}/personas`,
      personaIds,
    );
    return response.data;
  }

  async addSource(id: string, body: MatchSourceRequest): Promise<MatchSource> {
    const response = await axios.post<MatchSource>(
      `/persona-match/${id}/sources`,
      body,
    );
    return response.data;
  }

  async uploadSource(id: string, file: File): Promise<MatchSource> {
    const form = new FormData();
    form.append("file", file);
    const response = await axios.post<MatchSource>(
      `/persona-match/${id}/sources/upload`,
      form,
    );
    return response.data;
  }

  async reExtract(id: string, sourceId: string): Promise<MatchSource> {
    const response = await axios.post<MatchSource>(
      `/persona-match/${id}/sources/${sourceId}/extract`,
    );
    return response.data;
  }

  async deleteSource(id: string, sourceId: string): Promise<boolean> {
    const response = await axios.delete(`/persona-match/${id}/sources/${sourceId}`);
    return response.status === 200;
  }

  async matrix(id: string): Promise<MatchMatrix> {
    const response = await axios.get<MatchMatrix>(`/persona-match/${id}/matrix`);
    return response.data;
  }

  /**
   * Run the N×N analysis and stream progress as SSE. Mirrors the persona
   * dialogue streaming client: the XSRF token is primed via `/csrf` and sent as
   * a header because this POST bypasses the axios interceptor.
   */
  async runStream(
    id: string,
    force: boolean,
    handlers: MatchRunHandlers,
    signal?: AbortSignal,
  ): Promise<void> {
    const baseUrl = axios.defaults.baseURL || "/api";
    const token = await getCsrfToken();
    try {
      const response = await fetch(
        `${baseUrl}/persona-match/${id}/run/stream?force=${force}`,
        {
          method: "POST",
          credentials: "include",
          signal,
          headers: {
            Accept: "text/event-stream",
            ...(token ? { "X-XSRF-TOKEN": token } : {}),
          },
        },
      );
      if (!response.ok || !response.body) {
        handlers.onError?.(new Error(`Run failed: ${response.status}`));
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
          if (!line.startsWith("data:")) continue;
          const json = line.slice(5).trim();
          if (!json) continue;
          const event: MatchRunEvent = JSON.parse(json);
          handlers.onEvent(event);
          if (event.type === "DONE") handlers.onDone?.(event);
          else if (event.type === "ERROR")
            handlers.onError?.(new Error(event.error ?? "Run error"), event);
        }
      }
    } catch (e) {
      handlers.onError?.(e);
    }
  }
}
