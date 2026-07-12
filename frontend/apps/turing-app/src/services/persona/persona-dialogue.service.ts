import axios from "axios";
import { getCsrfToken } from "@/lib/axios";
import type {
  TurDialogueEvent,
  TurPersonaDialogueRequest,
} from "@/models/persona/persona-dialogue.model";

/** SSE callbacks for a streaming dialogue. */
export interface DialogueStreamHandlers {
  onTurn: (event: TurDialogueEvent) => void;
  onDone?: (event: TurDialogueEvent) => void;
  onError?: (error: unknown, event?: TurDialogueEvent) => void;
}

/**
 * Block AI / §XXXII.8 (T585; streamed in T604) — runs a bounded round-robin
 * dialogue between two or more personas on a topic. A single POST to
 * `/api/v2/persona-dialogue` that **streams** the conversation as SSE
 * `TurDialogueEvent`s (one per completed turn) so the UI renders it live.
 */
export class TurPersonaDialogueService {
  async stream(
    request: TurPersonaDialogueRequest,
    handlers: DialogueStreamHandlers,
    signal?: AbortSignal,
  ): Promise<void> {
    const baseUrl = axios.defaults.baseURL || "/api";
    // Prime + read the CSRF token via the shared axios helper and send it as the
    // header (this POST bypasses the axios interceptor that normally does this).
    // The XSRF-TOKEN cookie is HttpOnly, so it can't be read from document.cookie
    // — the token comes from the /csrf response body instead.
    const token = await getCsrfToken();

    try {
      const response = await fetch(`${baseUrl}/v2/persona-dialogue`, {
        method: "POST",
        credentials: "include",
        signal,
        headers: {
          "Content-Type": "application/json",
          Accept: "text/event-stream",
          ...(token ? { "X-XSRF-TOKEN": token } : {}),
        },
        body: JSON.stringify(request),
      });
      if (!response.ok || !response.body) {
        handlers.onError?.(new Error(`Dialogue failed: ${response.status}`));
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
