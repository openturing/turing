/**
 * Block AA / §XXVI.4–5 — content-fit report shapes. Mirror the backend
 * `TurContentFitResult` / `TurPersonaFitReport` records.
 */
export interface TurContentFitMisfit {
  span: string;
  /** too-complex | jargon | tone | missing-context */
  reason: string;
  suggestion?: string | null;
}

export interface TurContentFitResult {
  fitScore: number;
  summary?: string | null;
  fits: string[];
  misfits: TurContentFitMisfit[];
  readabilityScore: number;
  metrics?: {
    fleschReadingEase: number;
    fleschKincaidGrade: number;
    avgSentenceLength: number;
    avgSyllablesPerWord: number;
    complexWordRatio: number;
    passiveVoiceRatio: number;
    wordCount: number;
    sentenceCount: number;
  } | null;
  llmUsed: boolean;
  error?: string | null;
  canRegenerate: boolean;
  sourceId?: string | null;
  sourceName?: string | null;
}

export interface TurPersonaFitReport {
  personaId: string;
  overallScore: number;
  evaluatedCount: number;
  totalSources: number;
  llmAvailable: boolean;
  canRegenerate: boolean;
  sources: TurContentFitResult[];
}
