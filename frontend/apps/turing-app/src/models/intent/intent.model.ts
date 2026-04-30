export interface TurIntentAction {
  id: string;
  label: string;
  prompt: string;
  sortOrder: number;
}

export interface TurIntent {
  id: string;
  title: string;
  description?: string;
  icon?: string | null;
  enabled: number;
  sortOrder: number;
  actions: TurIntentAction[];
}

/**
 * LLM-shape used by the AI Authoring chat (mirrors the backend
 * {@code IntentGeneration} record). Strips id/sortOrder so the LLM
 * can't write to fields owned by the backend.
 *
 * @since 2026.2.5
 */
export interface IntentGeneration {
  title: string;
  description?: string | null;
  icon?: string | null;
  enabled?: number | null;
  actions: IntentActionGeneration[];
}

export interface IntentActionGeneration {
  label: string;
  prompt: string;
}
