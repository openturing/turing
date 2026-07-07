/**
 * Block AI / §XXXII.8 (T585; N-persona + streaming in T604) — automatic
 * persona↔persona conversation types. Mirrors the backend `TurDialogueEvent` /
 * `PersonaDialogueRequest`.
 */

export type TurDialogueEventType = "TURN" | "DONE" | "ERROR";

export interface TurDialogueEvent {
  type: TurDialogueEventType;
  /** TURN: 0-based turn index · DONE: number of turns produced. */
  index?: number | null;
  personaId?: string | null;
  personaName?: string | null;
  content?: string | null;
  error?: string | null;
}

export interface TurPersonaDialogueRequest {
  topic: string;
  /** Ordered roster of speaker personas (two or more). */
  personaIds: string[];
  llmInstanceId: string;
  /** Total number of turns; omitted → backend default (10), clamped [2, 40]. */
  turns?: number;
}
