import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import type { TurChatFlow } from "@/models/agent/chat-flow.model";
import type {
  TurChatFlowRecipe,
  TurChatFlowRecipeSummary,
} from "@/models/agent/chat-flow-recipe.model";
import { TurChatFlowRecipeService } from "@/services/agent/chat-flow-recipe.service";

import { queryKeys } from "./keys";

const service = new TurChatFlowRecipeService();

/** Catalog listing — cheap metadata only. Shared by every admin. */
export function useChatFlowRecipes() {
  return useQuery<TurChatFlowRecipeSummary[]>({
    queryKey: queryKeys.chatFlowRecipes.list(),
    queryFn: () => service.list(),
  });
}

/** Full recipe payload — used by the preview pane before install. */
export function useChatFlowRecipe(recipeId: string | undefined) {
  return useQuery<TurChatFlowRecipe>({
    queryKey: recipeId
      ? queryKeys.chatFlowRecipes.detail(recipeId)
      : ["chat-flow-recipes", "detail", "pending"],
    queryFn: () => service.get(recipeId as string),
    enabled: Boolean(recipeId),
  });
}

/**
 * Install a recipe onto an agent. On success, invalidate every chat-flow
 * cache (list, detail, lint, trigger-conflicts) so the affected agent's
 * editor refreshes on the next render.
 */
export function useInstallChatFlowRecipe() {
  const queryClient = useQueryClient();
  return useMutation<TurChatFlow[], Error, { agentId: string; recipeId: string }>({
    mutationFn: ({ agentId, recipeId }) => service.install(agentId, recipeId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.chatFlows.all() });
      queryClient.invalidateQueries({ queryKey: queryKeys.aiAgents.all() });
      queryClient.invalidateQueries({ queryKey: queryKeys.personas.all() });
      queryClient.invalidateQueries({ queryKey: queryKeys.aiAgentSlots.all() });
    },
  });
}
