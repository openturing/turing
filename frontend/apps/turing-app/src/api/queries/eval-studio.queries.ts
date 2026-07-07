import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/api/queries/keys";
import type { TurEvalReviewRequest } from "@/models/eval/eval-studio.model";
import { TurEvalStudioService } from "@/services/eval/eval-studio.service";

/**
 * T599 / Block AJ — read hooks for the Eval Studio's agent-decoupled assets.
 * T593 adds the per-agent human-review inbox (list + submit-verdict + promote).
 *
 * @since 2026.3.4
 */
const service = new TurEvalStudioService();

export function useEvalDatasets() {
  return useQuery({
    queryKey: queryKeys.evalStudio.datasets(),
    queryFn: () => service.listDatasets(),
  });
}

export function useEvalGraderStacks() {
  return useQuery({
    queryKey: queryKeys.evalStudio.graderStacks(),
    queryFn: () => service.listGraderStacks(),
  });
}

/** T593 — open human-review tasks for one agent (disabled until an agent is picked). */
export function useEvalReviewInbox(agentId: string | undefined) {
  return useQuery({
    queryKey: agentId
      ? queryKeys.evalStudio.reviewInbox(agentId)
      : ["eval-studio", "review-inbox", "pending"],
    queryFn: () => service.listReviewTasks(agentId as string),
    enabled: Boolean(agentId),
  });
}

/** T593 — submit a reviewer verdict; refreshes the agent's inbox. */
export function useSubmitReview(agentId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, request }: { taskId: string; request: TurEvalReviewRequest }) =>
      service.submitReview(agentId as string, taskId, request),
    onSuccess: () => {
      if (agentId) {
        queryClient.invalidateQueries({
          queryKey: queryKeys.evalStudio.reviewInbox(agentId),
        });
      }
    },
  });
}

/** T593 — promote a reviewed task into a golden dataset row; refreshes datasets. */
export function usePromoteReview(agentId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, datasetId }: { taskId: string; datasetId: string }) =>
      service.promoteReview(agentId as string, taskId, datasetId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.evalStudio.datasets() });
    },
  });
}

/** T594 — inter-annotator agreement (Fleiss' kappa) over multi-reviewer tasks. */
export function useEvalAgreement(agentId: string | undefined) {
  return useQuery({
    queryKey: agentId
      ? queryKeys.evalStudio.agreement(agentId)
      : ["eval-studio", "agreement", "pending"],
    queryFn: () => service.getAgreement(agentId as string),
    enabled: Boolean(agentId),
  });
}

/** T594 — MODEL-vs-human calibration over the agent's reviewed audit tasks. */
export function useEvalCalibration(agentId: string | undefined) {
  return useQuery({
    queryKey: agentId
      ? queryKeys.evalStudio.calibration(agentId)
      : ["eval-studio", "calibration", "pending"],
    queryFn: () => service.getCalibration(agentId as string),
    enabled: Boolean(agentId),
  });
}

/** T594 — sample a MODEL-graded run into audit tasks; refreshes the inbox. */
export function useSampleAudit(agentId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      sampleSize,
      requiredReviewers,
    }: {
      sampleSize: number;
      requiredReviewers: number;
    }) => service.sampleAudit(agentId as string, sampleSize, requiredReviewers),
    onSuccess: () => {
      if (agentId) {
        queryClient.invalidateQueries({
          queryKey: queryKeys.evalStudio.reviewInbox(agentId),
        });
      }
    },
  });
}

/** T594 — set how many reviewers a still-open task needs; refreshes the inbox. */
export function useSetRequiredReviewers(agentId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, count }: { taskId: string; count: number }) =>
      service.setRequiredReviewers(agentId as string, taskId, count),
    onSuccess: () => {
      if (agentId) {
        queryClient.invalidateQueries({
          queryKey: queryKeys.evalStudio.reviewInbox(agentId),
        });
      }
    },
  });
}
