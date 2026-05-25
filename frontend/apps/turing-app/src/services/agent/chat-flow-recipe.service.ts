import axios from "axios";

import type { TurChatFlow } from "@/models/agent/chat-flow.model";
import type {
  TurChatFlowRecipe,
  TurChatFlowRecipeSummary,
} from "@/models/agent/chat-flow-recipe.model";

/**
 * T96 / §VII.11.f — client for the Turing Recipes marketplace browser plus
 * the per-agent install endpoint. The listing endpoints are agent-agnostic
 * (same catalog for every admin); the install one is agent-scoped because
 * a recipe always materialises onto a chosen agent.
 *
 * @since 2026.3.1
 */
export class TurChatFlowRecipeService {
  async list(): Promise<TurChatFlowRecipeSummary[]> {
    const response = await axios.get<TurChatFlowRecipeSummary[]>(`/chat-flow-recipes`);
    return response.data;
  }

  async get(recipeId: string): Promise<TurChatFlowRecipe> {
    const response = await axios.get<TurChatFlowRecipe>(`/chat-flow-recipes/${recipeId}`);
    return response.data;
  }

  async install(agentId: string, recipeId: string): Promise<TurChatFlow[]> {
    const response = await axios.post<TurChatFlow[]>(
      `/ai-agent/${agentId}/chat-flow/install-recipe/${recipeId}`,
    );
    return response.data;
  }
}
