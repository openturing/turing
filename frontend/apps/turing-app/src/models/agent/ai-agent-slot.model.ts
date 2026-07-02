/**
 * Typed variable declared on an AI agent. The chat-flow editor pulls from
 * this catalogue when authors pick an {@code outputVariable}, so values
 * captured across turns land in a stable schema.
 *
 * @since 2026.2.7
 */
export type TurAIAgentSlotType =
  | "STRING"
  | "INTEGER"
  | "BOOLEAN"
  | "FLOAT"
  | "TEXT"
  | "IMAGE"
  | "AUDIO"
  | "VIDEO"
  | "FILE";

export const TUR_AI_AGENT_SLOT_TYPES: TurAIAgentSlotType[] = [
  "STRING",
  "INTEGER",
  "BOOLEAN",
  "FLOAT",
  "TEXT",
  "IMAGE",
  "AUDIO",
  "VIDEO",
  "FILE",
];

/**
 * Multi-modal slot types (T64) hold a URL to a binary in object storage
 * rather than an inline scalar value. The chat-flow editor / UI treats them
 * differently (upload widget, image preview) from scalar slots. {@code VIDEO}
 * (T501) additionally supports native Gemini video understanding on upload.
 */
export const TUR_MULTI_MODAL_SLOT_TYPES: TurAIAgentSlotType[] = ["IMAGE", "AUDIO", "VIDEO", "FILE"];

export function isMultiModalSlotType(type: TurAIAgentSlotType): boolean {
  return TUR_MULTI_MODAL_SLOT_TYPES.includes(type);
}

export interface TurAIAgentSlot {
  id: string;
  name: string;
  description?: string | null;
  type: TurAIAgentSlotType;
  /**
   * T100 — when true, this slot is a default target of document extraction
   * (`POST /chat/slot-extract`) even when the caller passes no slot names. The
   * set of slots flagged this way is the agent's document schema.
   */
  extractFromDocument?: boolean;
  /**
   * T100 — optional Java regex an extracted value must fully match to be
   * persisted; also surfaced to the LLM as a formatting hint.
   */
  validationPattern?: string | null;
}
