import { useMutation, useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/api/queries/keys";
import type { TurSystemPromptIssue } from "@/models/agent/system-prompt.model";
import { TurSystemPromptService } from "@/services/agent/system-prompt.service";

/**
 * React Query hooks for the AI Agent system-prompt Live Preview + validation.
 *
 * @since 2026.3.1
 */
const service = new TurSystemPromptService();

/**
 * Live Preview of the assembled system prompt (segments + tools). `flowId`
 * selects which chat flow governs the previewed turn; changing it refetches.
 */
export function useSystemPromptPreview(agentId: string | undefined, flowId?: string) {
  return useQuery({
    queryKey: agentId
      ? queryKeys.aiAgents.systemPromptPreview(agentId, flowId)
      : ["ai-agents", "system-prompt", "preview", "pending"],
    queryFn: () => service.preview(agentId as string, flowId),
    enabled: Boolean(agentId),
  });
}

/** Fast heuristic conflict validation — runs automatically on the page. */
export function useValidateSystemPrompt(agentId: string | undefined) {
  return useQuery<TurSystemPromptIssue[]>({
    queryKey: agentId
      ? queryKeys.aiAgents.systemPromptValidate(agentId)
      : ["ai-agents", "system-prompt", "validate", "pending"],
    queryFn: () => service.validate(agentId as string),
    enabled: Boolean(agentId),
  });
}

/**
 * Deep LLM-powered conflict audit. A mutation (not a query) because it costs a
 * model call and is button-triggered; the result lives in `mutation.data`.
 */
export function useDeepCheckSystemPrompt() {
  return useMutation<TurSystemPromptIssue[], Error, string>({
    mutationFn: (agentId: string) => service.deepCheck(agentId),
  });
}
