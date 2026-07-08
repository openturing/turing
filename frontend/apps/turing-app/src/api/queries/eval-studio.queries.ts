import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/api/queries/keys";
import type {
  TurEvalDatasetRowDraft,
  TurEvalReviewRequest,
} from "@/models/eval/eval-studio.model";
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

// ─────────────────────────── T599 run history ───────────────────────────

/** T599 — the agent's eval run history (disabled until an agent is picked). */
export function useEvalRunHistory(agentId: string | undefined) {
  return useQuery({
    queryKey: agentId
      ? queryKeys.evalStudio.runHistory(agentId)
      : ["eval-studio", "run-history", "pending"],
    queryFn: () => service.listRunHistory(agentId as string),
    enabled: Boolean(agentId),
  });
}

/** T599 — run the gate now; refreshes the agent's run history. */
export function useRunEval(agentId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => service.runEval(agentId as string),
    onSuccess: () => {
      if (agentId) {
        queryClient.invalidateQueries({
          queryKey: queryKeys.evalStudio.runHistory(agentId),
        });
      }
    },
  });
}

// ─────────────────────────── T597 LLM-assisted generation ───────────────────────────

/** T597 — synthesize candidate rows for an agent (draft, not persisted). */
export function useGenerateRows() {
  return useMutation({
    mutationFn: ({ agentId, count }: { agentId: string; count: number }) =>
      service.generateRows(agentId, count),
  });
}

/** T597 — paraphrase a dataset's rows into variants (draft, not persisted). */
export function useAugmentDataset() {
  return useMutation({
    mutationFn: ({ datasetId, variants }: { datasetId: string; variants: number }) =>
      service.augmentDataset(datasetId, variants),
  });
}

/** T597 — persist reviewed draft rows; refreshes the dataset list. */
export function useSaveReviewedRows() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      rows,
      name,
      datasetId,
    }: {
      rows: TurEvalDatasetRowDraft[];
      name?: string;
      datasetId?: string;
    }) => service.saveReviewedRows(rows, { name, datasetId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.evalStudio.datasets() });
    },
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

// ─────────────────────────── T603 continuous / online eval ───────────────────────────

/** T603 — the agent's online-eval snapshots (disabled until an agent is picked). */
export function useOnlineEvalHistory(agentId: string | undefined) {
  return useQuery({
    queryKey: agentId
      ? queryKeys.evalStudio.onlineHistory(agentId)
      : ["eval-studio", "online-history", "pending"],
    queryFn: () => service.listOnlineHistory(agentId as string),
    enabled: Boolean(agentId),
  });
}

/** T603 — sample + grade live traffic now; refreshes the drift timeline. */
export function useRunOnlineEval(agentId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => service.runOnlineEval(agentId as string),
    onSuccess: () => {
      if (agentId) {
        queryClient.invalidateQueries({
          queryKey: queryKeys.evalStudio.onlineHistory(agentId),
        });
      }
    },
  });
}

/** T603 — promote a snapshot to the healthy baseline; refreshes the timeline. */
export function usePromoteOnlineBaseline(agentId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (snapshotId: string) =>
      service.promoteOnlineBaseline(agentId as string, snapshotId),
    onSuccess: () => {
      if (agentId) {
        queryClient.invalidateQueries({
          queryKey: queryKeys.evalStudio.onlineHistory(agentId),
        });
      }
    },
  });
}
