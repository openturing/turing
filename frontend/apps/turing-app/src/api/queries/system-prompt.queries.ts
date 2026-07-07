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
export function useSystemPromptPreview(
  agentId: string | undefined,
  flowId?: string,
  nodeId?: string,
  vars?: string,
) {
  return useQuery({
    queryKey: agentId
      ? queryKeys.aiAgents.systemPromptPreview(agentId, flowId, nodeId, vars)
      : ["ai-agents", "system-prompt", "preview", "pending"],
    queryFn: () => service.preview(agentId as string, flowId, nodeId, vars),
    enabled: Boolean(agentId),
  });
}

/**
 * T612/T618 — replay the assembled prompt from a real conversation. Enabled only
 * when a non-blank `conversationId` is supplied; changing it (or `turnIndex`)
 * refetches. Without `turnIndex` the result is the T612 current-state replay;
 * with it, the T618 verbatim past-turn capture. The `replay` envelope carries
 * the conversation source, its tool-call trace, and the available captured turns.
 */
export function useSystemPromptReplayPreview(
  agentId: string | undefined,
  conversationId: string | undefined,
  turnIndex?: number,
) {
  const trimmed = conversationId?.trim();
  return useQuery({
    queryKey:
      agentId && trimmed
        ? queryKeys.aiAgents.systemPromptReplay(agentId, trimmed, turnIndex)
        : ["ai-agents", "system-prompt", "replay", "pending"],
    queryFn: () =>
      service.previewReplay(agentId as string, trimmed as string, turnIndex),
    enabled: Boolean(agentId) && Boolean(trimmed),
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
