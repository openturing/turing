import axios from "axios";
import { getCsrfToken } from "@/lib/axios";
import type {
  DialogueProject,
  DialogueProjectRequest,
  DialogueProjectSummary,
  DialogueSpeaker,
  DialogueTurn,
  TurDialogueEvent,
} from "@/models/persona/persona-dialogue.model";

/** SSE callbacks for a streaming project run. */
export interface DialogueRunHandlers {
  onTurn: (event: TurDialogueEvent) => void;
  onDone?: (event: TurDialogueEvent) => void;
  onError?: (error: unknown, event?: TurDialogueEvent) => void;
}

/**
 * Persona Dialogue **project** REST client (Block AU / §XLIV, T706). Wraps
 * `/api/persona-dialogue`: project CRUD, the ordered speaker roster, the
 * persisted last transcript, and the project-scoped run that streams the
 * dialogue live over SSE (mirrors the ephemeral dialogue stream but saves the
 * transcript). Separate from the ephemeral `TurPersonaDialogueService`.
 */
export class TurPersonaDialogueProjectService {
  async list(): Promise<DialogueProjectSummary[]> {
    const response = await axios.get<DialogueProjectSummary[]>("/persona-dialogue");
    return response.data;
  }

  async get(id: string): Promise<DialogueProject> {
    const response = await axios.get<DialogueProject>(`/persona-dialogue/${id}`);
    return response.data;
  }

  async create(body: DialogueProjectRequest): Promise<DialogueProject> {
    const response = await axios.post<DialogueProject>("/persona-dialogue", body);
    return response.data;
  }

  async update(id: string, body: DialogueProjectRequest): Promise<DialogueProject> {
    const response = await axios.put<DialogueProject>(`/persona-dialogue/${id}`, body);
    return response.data;
  }

  async remove(id: string): Promise<boolean> {
    const response = await axios.delete(`/persona-dialogue/${id}`);
    return response.status === 200;
  }

  async setSpeakers(id: string, personaIds: string[]): Promise<DialogueSpeaker[]> {
    const response = await axios.put<DialogueSpeaker[]>(
      `/persona-dialogue/${id}/speakers`,
      personaIds,
    );
    return response.data;
  }

  async transcript(id: string): Promise<DialogueTurn[]> {
    const response = await axios.get<DialogueTurn[]>(`/persona-dialogue/${id}/transcript`);
    return response.data;
  }

  /**
   * Run the project's dialogue and stream each turn as SSE. Mirrors the ephemeral
   * dialogue client: the XSRF token is primed via `/csrf` and sent as a header
   * because this POST bypasses the axios interceptor.
   */
  async runStream(
    id: string,
    handlers: DialogueRunHandlers,
    signal?: AbortSignal,
  ): Promise<void> {
    const baseUrl = axios.defaults.baseURL || "/api";
    const token = await getCsrfToken();
    try {
      const response = await fetch(`${baseUrl}/persona-dialogue/${id}/run/stream`, {
        method: "POST",
        credentials: "include",
        signal,
        headers: {
          Accept: "text/event-stream",
          ...(token ? { "X-XSRF-TOKEN": token } : {}),
        },
      });
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
          const event: TurDialogueEvent = JSON.parse(json);
          if (event.type === "TURN") handlers.onTurn(event);
          else if (event.type === "DONE") handlers.onDone?.(event);
          else if (event.type === "ERROR")
            handlers.onError?.(new Error(event.error ?? "Dialogue error"), event);
        }
      }
    } catch (e) {
      handlers.onError?.(e);
    }
  }
}
