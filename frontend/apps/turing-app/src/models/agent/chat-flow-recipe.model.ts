import type { TurChatFlow } from "./chat-flow.model";

/**
 * T96 / §VII.11.f — listing entry for the Turing Recipes marketplace.
 *
 * @since 2026.3.1
 */
export interface TurChatFlowRecipeSummary {
  id: string;
  version: string;
  name: string;
  description: string;
  vertical: string;
  tags: string[];
  flowCount: number;
  personaCount: number;
  slotCount: number;
}

/**
 * T96 / §VII.11.f — full recipe payload returned by
 * {@code GET /api/chat-flow-recipes/{id}}. Carries the install bundle so the
 * preview pane can show what will be created and the install dialog has
 * everything it needs once the user picks a target agent.
 *
 * @since 2026.3.1
 */
export interface TurChatFlowRecipe extends TurChatFlowRecipeSummary {
  /** Same shape as the existing import-bundle payload. */
  bundle: Array<{
    chatFlow: TurChatFlow;
    personas?: Array<{ name: string; description?: string | null }>;
    slots?: Array<{ name: string; description?: string | null; type?: string | null }>;
  }>;
}
