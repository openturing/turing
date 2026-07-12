import type { TurPersona } from "./persona.model.ts";

/**
 * Block AA / §XXVI.7 — result of deriving a draft persona from an audio
 * recording. Mirrors the backend `TurPersonaAudioDeriveService.DraftResult`.
 * The `draft` is NOT saved — it seeds the persona form for human review.
 */
export interface PersonaDraftResult {
  success: boolean;
  error?: string | null;
  transcript?: string | null;
  draft?: TurPersona | null;
}

/**
 * T715 / §XLII.7 — lifecycle state of an async persona-from-audio job. Mirrors
 * the backend `TurPersonaAudioJobState`.
 */
export type PersonaAudioJobState =
  | "QUEUED"
  | "TRANSCRIBING"
  | "ANALYZING"
  | "SUCCEEDED"
  | "FAILED";

/**
 * T715 / §XLII.7 — a snapshot of an async persona-from-audio job pushed over the
 * SSE stream. Mirrors the backend `TurPersonaAudioJobStatus`. During
 * `TRANSCRIBING`, `completedChunks/totalChunks` advance; the `draft` +
 * `transcript` ride only the `SUCCEEDED` snapshot.
 */
export interface PersonaAudioJobStatus {
  jobId: string;
  state: PersonaAudioJobState;
  completedChunks: number;
  totalChunks: number;
  draft?: TurPersona | null;
  transcript?: string | null;
  error?: string | null;
}
