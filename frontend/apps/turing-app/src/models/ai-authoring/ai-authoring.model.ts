/**
 * Generic AI Authoring chat protocol — shared shape used by every CRUD
 * that opts in to the chat-side-by-side authoring pattern.
 *
 * Each entity (Intent, AI Agent, Custom Tool, ...) defines its own
 * generation shape `T` (e.g. `IntentGeneration`) and reuses these
 * envelopes for transport.
 *
 * @since 2026.2.5
 */

export interface AiAuthoringMessage {
  role: "user" | "assistant" | "system";
  content: string;
}

export interface AiAuthoringRequest<T> {
  messages: AiAuthoringMessage[];
  currentState: T | null;
}

export interface AiAuthoringResponse<T> {
  /** Conversational reply for the chat panel. */
  message: string;
  /** Full updated entity snapshot — replaces the form state wholesale. */
  state: T;
}

/** Function the hook calls to dispatch one chat turn. */
export type AiAuthoringEndpoint<T> = (
  request: AiAuthoringRequest<T>,
) => Promise<AiAuthoringResponse<T>>;
