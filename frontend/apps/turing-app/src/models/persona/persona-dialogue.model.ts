/**
 * Block AI / §XXXII.8 (T585) — automatic persona↔persona conversation types.
 * Mirrors the backend `TurDialogueTranscript` / `PersonaDialogueRequest`.
 */

export interface TurPersonaRef {
  id: string;
  name: string;
}

export interface TurDialogueTurn {
  personaId: string;
  personaName: string;
  content: string;
}

export interface TurDialogueTranscript {
  topic: string;
  personaA: TurPersonaRef;
  personaB: TurPersonaRef;
  turns: number;
  transcript: TurDialogueTurn[];
  /** True when a turn failed mid-dialogue; `transcript` still holds prior turns. */
  llmError: boolean;
  error?: string | null;
}

export interface TurPersonaDialogueRequest {
  topic: string;
  personaAId: string;
  personaBId: string;
  llmInstanceId: string;
  /** Total number of turns; omitted → backend default (10), clamped [2, 20]. */
  turns?: number;
}
