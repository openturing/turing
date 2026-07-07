/**
 * Block AA / §XXVI.8 — auto-suggest report shapes. Mirror the backend
 * `TurPersonaSuggestion` / `TurPersonaSuggestionReport` records: a piece of
 * content is graded against every audience persona and the personas are ranked
 * best-fit first.
 */
import type { TurContentFitResult } from "@/models/persona/persona-fit.model.ts";

export interface TurPersonaSuggestion {
  personaId: string;
  personaName: string;
  fitScore: number;
  /** 1 = best fit. */
  rank: number;
  result: TurContentFitResult;
}

export interface TurPersonaSuggestionReport {
  bestPersonaId?: string | null;
  bestPersonaName?: string | null;
  bestScore: number;
  evaluatedCount: number;
  llmAvailable: boolean;
  /** Sorted best-fit first. */
  rankings: TurPersonaSuggestion[];
}
