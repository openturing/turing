import axios from "axios";
import type {
  PersonaAudioJobStatus,
  PersonaDraftResult,
} from "@/models/persona/persona-audio.model.ts";

/** SSE callbacks for a streaming persona-from-audio job. */
export interface PersonaAudioJobHandlers {
  onStatus: (status: PersonaAudioJobStatus) => void;
  /** Transport/stream error only — terminal FAILED is read from the status. */
  onError?: (error: unknown) => void;
}

/**
 * Client for persona-from-audio derivation (Block AA / §XXVI.7). POSTs an audio
 * file to `/persona/derive-from-audio`; the returned draft is never saved — the
 * caller opens the persona form pre-filled for review.
 *
 * T715 / §XLII.7 adds the async surface: {@link submitJob} returns a job id
 * immediately and {@link streamJob} relays the live progress (transcription
 * chunk X/N → analysing → done) over SSE, so a long recording shows a real
 * progress bar instead of a blocking spinner.
 */
export class TurPersonaAudioService {
  /** Legacy blocking derivation — kept for callers that don't need progress. */
  async deriveFromAudio(
    file: File,
    language?: string
  ): Promise<PersonaDraftResult> {
    const response = await axios.post<PersonaDraftResult>(
      "/persona/derive-from-audio",
      this.buildForm(file, language)
    );
    return response.data;
  }

  /** Submit for background derivation; returns the initial QUEUED status. */
  async submitJob(
    file: File,
    language?: string
  ): Promise<PersonaAudioJobStatus> {
    const response = await axios.post<PersonaAudioJobStatus>(
      "/persona/derive-from-audio/jobs",
      this.buildForm(file, language)
    );
    return response.data;
  }

  /**
   * Poll a job's authoritative status/result. Used to fetch the terminal draft
   * after the SSE stream ends: this GET is serialised by the same servlet
   * converter as the blocking derivation, so the draft is fully populated (the
   * reactive SSE encoder is only trusted for live progress, not the entity).
   */
  async getJob(jobId: string): Promise<PersonaAudioJobStatus> {
    const response = await axios.get<PersonaAudioJobStatus>(
      `/persona/derive-from-audio/jobs/${jobId}`
    );
    return response.data;
  }

  /**
   * Subscribe to a job's SSE stream. GET needs no CSRF; we read the
   * `ReadableStream` and parse `data:` frames, mirroring the Persona Match run
   * stream. Completes when the stream closes (terminal event) or is aborted.
   */
  async streamJob(
    jobId: string,
    handlers: PersonaAudioJobHandlers,
    signal?: AbortSignal
  ): Promise<void> {
    const baseUrl = axios.defaults.baseURL || "/api";
    try {
      const response = await fetch(
        `${baseUrl}/persona/derive-from-audio/jobs/${jobId}/stream`,
        {
          method: "GET",
          credentials: "include",
          signal,
          headers: { Accept: "text/event-stream" },
        }
      );
      if (!response.ok || !response.body) {
        handlers.onError?.(new Error(`Stream failed: ${response.status}`));
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
          const status: PersonaAudioJobStatus = JSON.parse(json);
          handlers.onStatus(status);
        }
      }
    } catch (e) {
      if ((e as Error)?.name !== "AbortError") handlers.onError?.(e);
    }
  }

  private buildForm(file: File, language?: string): FormData {
    const form = new FormData();
    form.append("file", file);
    if (language) form.append("language", language);
    return form;
  }
}
