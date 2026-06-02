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
  | "FILE";

export const TUR_AI_AGENT_SLOT_TYPES: TurAIAgentSlotType[] = [
  "STRING",
  "INTEGER",
  "BOOLEAN",
  "FLOAT",
  "TEXT",
  "IMAGE",
  "AUDIO",
  "FILE",
];

/**
 * Multi-modal slot types (T64) hold a URL to a binary in object storage
 * rather than an inline scalar value. The chat-flow editor / UI treats them
 * differently (upload widget, image preview) from scalar slots.
 */
export const TUR_MULTI_MODAL_SLOT_TYPES: TurAIAgentSlotType[] = ["IMAGE", "AUDIO", "FILE"];

export function isMultiModalSlotType(type: TurAIAgentSlotType): boolean {
  return TUR_MULTI_MODAL_SLOT_TYPES.includes(type);
}

export interface TurAIAgentSlot {
  id: string;
  name: string;
  description?: string | null;
  type: TurAIAgentSlotType;
}
