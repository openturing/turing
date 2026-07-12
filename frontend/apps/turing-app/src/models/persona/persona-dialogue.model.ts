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

// ---------------------------------------------------------------------------
// Dialogue projects (Block AU / §XLIV) — saved, multi-project dialogues.
// Mirrors the backend TurPersonaDialogueProjectDto / speaker / turn DTOs.
// ---------------------------------------------------------------------------

export interface DialogueSpeaker {
  /** persona id */
  id: string;
  name: string;
  kind: string | null;
  order: number;
}

export interface DialogueTurn {
  index: number;
  personaId: string | null;
  personaName: string | null;
  content: string | null;
}

/** Project summary (list) — speakers/transcript omitted, count present. */
export interface DialogueProjectSummary {
  id: string;
  name: string;
  description: string | null;
  topic: string | null;
  llmInstanceId: string | null;
  llmName: string | null;
  turns: number;
  lastRunAt: string | null;
  speakerCount: number;
}

/** Project detail — collections populated. */
export interface DialogueProject extends DialogueProjectSummary {
  speakers: DialogueSpeaker[];
  transcript: DialogueTurn[];
}

/** Create/update request body (subset the backend reads on write). */
export interface DialogueProjectRequest {
  name: string;
  description?: string | null;
  topic?: string | null;
  llmInstanceId?: string | null;
  turns: number;
}
