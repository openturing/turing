import axios from "axios";
import { getCsrfToken } from "@/lib/axios";
import type {
  ResearchConceptFit,
  ResearchDrift,
  ResearchGraph,
  ResearchPersonaRef,
  ResearchProgramRollup,
  ResearchProposalResult,
  ResearchReport,
  ResearchRunEvent,
  ResearchSaturation,
  ResearchStudy,
  ResearchStudyRequest,
  ResearchStudySummary,
} from "@/models/persona/persona-research.model";

/** Deep-linkable summary of a promoted eval dataset (T725 bridge). */
export interface PromotedDataset {
  id: string;
  name: string;
  rowCount?: number;
}

/** SSE callbacks for a streaming cohort interview run. */
export interface ResearchRunHandlers {
  onEvent: (event: ResearchRunEvent) => void;
  onDone?: (event: ResearchRunEvent) => void;
  onError?: (error: unknown, event?: ResearchRunEvent) => void;
}

/**
 * Synthetic User Research REST client (Block AW / §XLVI, T730). Wraps
 * `/api/research-study`: study CRUD, the ordered audience roster, the persisted
 * interviews + synthesized report / saturation / theme graph / drift, the
 * concept-fit and eval-dataset bridges, and the cohort interview run — both the
 * blocking form and the live SSE stream that fills the studio's interview feed.
 */
export class TurPersonaResearchService {
  async list(): Promise<ResearchStudySummary[]> {
    const response = await axios.get<ResearchStudySummary[]>("/research-study");
    return response.data;
  }

  async get(id: string): Promise<ResearchStudy> {
    const response = await axios.get<ResearchStudy>(`/research-study/${id}`);
    return response.data;
  }

  async create(body: ResearchStudyRequest): Promise<ResearchStudy> {
    const response = await axios.post<ResearchStudy>("/research-study", body);
    return response.data;
  }

  async update(id: string, body: ResearchStudyRequest): Promise<ResearchStudy> {
    const response = await axios.put<ResearchStudy>(`/research-study/${id}`, body);
    return response.data;
  }

  async remove(id: string): Promise<boolean> {
    const response = await axios.delete(`/research-study/${id}`);
    return response.status === 200;
  }

  async setPersonas(id: string, personaIds: string[]): Promise<ResearchPersonaRef[]> {
    const response = await axios.put<ResearchPersonaRef[]>(
      `/research-study/${id}/personas`,
      personaIds,
    );
    return response.data;
  }

  async insights(id: string, regenerate = false): Promise<ResearchReport> {
    const response = await axios.get<ResearchReport>(
      `/research-study/${id}/insights?regenerate=${regenerate}`,
    );
    return response.data;
  }

  async saturation(id: string): Promise<ResearchSaturation> {
    const response = await axios.get<ResearchSaturation>(`/research-study/${id}/saturation`);
    return response.data;
  }

  async graph(id: string): Promise<ResearchGraph> {
    const response = await axios.get<ResearchGraph>(`/research-study/${id}/graph`);
    return response.data;
  }

  async drift(id: string): Promise<ResearchDrift> {
    const response = await axios.get<ResearchDrift>(`/research-study/${id}/drift`);
    return response.data;
  }

  async conceptFit(id: string, regenerate = false): Promise<ResearchConceptFit> {
    const response = await axios.get<ResearchConceptFit>(
      `/research-study/${id}/concept-fit?regenerate=${regenerate}`,
    );
    return response.data;
  }

  /** Program rollup (T733): aggregate several studies into a program-level view. */
  async rollup(studyIds: string[]): Promise<ResearchProgramRollup> {
    const response = await axios.post<ResearchProgramRollup>(
      "/research-study/program/rollup",
      studyIds,
    );
    return response.data;
  }

  /** Blocking run of one study (used by the program board's "run all"). */
  async run(id: string, force = false): Promise<void> {
    await axios.post(`/research-study/${id}/run?force=${force}`);
  }

  /** Research Assistant (T732): a free-text goal → a proposed study config (not saved). */
  async propose(brief: string, regenerate = false): Promise<ResearchProposalResult> {
    const response = await axios.post<ResearchProposalResult>(
      "/research-study/assistant/propose",
      { brief, regenerate },
    );
    return response.data;
  }

  async promoteToDataset(id: string, name?: string): Promise<PromotedDataset> {
    const query = name ? `?name=${encodeURIComponent(name)}` : "";
    const response = await axios.post<PromotedDataset>(
      `/research-study/${id}/promote-to-dataset${query}`,
    );
    return response.data;
  }

  /**
   * Run the cohort interviews and stream progress as SSE. Mirrors the persona
   * match / dialogue streaming clients: the XSRF token is primed via `/csrf` and
   * sent as a header because this POST bypasses the axios interceptor.
   */
  async runStream(
    id: string,
    force: boolean,
    handlers: ResearchRunHandlers,
    signal?: AbortSignal,
  ): Promise<void> {
    const baseUrl = axios.defaults.baseURL || "/api";
    const token = await getCsrfToken();
    try {
      const response = await fetch(
        `${baseUrl}/research-study/${id}/run/stream?force=${force}`,
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
          const event: ResearchRunEvent = JSON.parse(json);
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
