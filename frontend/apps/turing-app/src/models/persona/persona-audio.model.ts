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
