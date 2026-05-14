/**
 * Typed variable declared on an AI agent. The chat-flow editor pulls from
 * this catalogue when authors pick an {@code outputVariable}, so values
 * captured across turns land in a stable schema.
 *
 * @since 2026.2.7
 */
export type TurAIAgentSlotType = "STRING" | "INTEGER" | "BOOLEAN" | "FLOAT" | "TEXT";

export const TUR_AI_AGENT_SLOT_TYPES: TurAIAgentSlotType[] = [
  "STRING",
  "INTEGER",
  "BOOLEAN",
  "FLOAT",
  "TEXT",
];

export interface TurAIAgentSlot {
  id: string;
  name: string;
  description?: string | null;
  type: TurAIAgentSlotType;
}
