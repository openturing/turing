import axios from "axios";
import type { TurAgentEvalReport } from "@/models/agent/agent-eval.model";
import type {
  TurEvalAgreement,
  TurEvalCalibration,
  TurEvalDataset,
  TurEvalDatasetRowDraft,
  TurEvalGraderStack,
  TurEvalReviewRequest,
  TurEvalReviewTask,
  TurOnlineEvalSnapshot,
} from "@/models/eval/eval-studio.model";

/**
 * T595–T600 / Block AJ — client for the reusable, agent-decoupled eval assets
 * surfaced in the Eval Studio: datasets and named grader stacks. T593 adds the
 * per-agent human-review inbox (list / submit verdict / promote-to-golden-row).
 *
 * @since 2026.3.4
 */
export class TurEvalStudioService {
  /** List datasets (summaries — no rows). */
  async listDatasets(): Promise<TurEvalDataset[]> {
    const response = await axios.get<TurEvalDataset[]>("/eval/dataset");
    return response.data;
  }

  /** One dataset with its rows. */
  async getDataset(id: string): Promise<TurEvalDataset> {
    const response = await axios.get<TurEvalDataset>(`/eval/dataset/${id}`);
    return response.data;
  }

  /** List reusable grader stacks. */
  async listGraderStacks(): Promise<TurEvalGraderStack[]> {
    const response = await axios.get<TurEvalGraderStack[]>("/eval/grader-stack");
    return response.data;
  }

  // ─────────────────────────── T599 run history ───────────────────────────

  /** The agent's eval run history (newest first) with the per-case breakdown. */
  async listRunHistory(agentId: string): Promise<TurAgentEvalReport[]> {
    const response = await axios.get<TurAgentEvalReport[]>(
      `/ai-agent/${agentId}/eval/history`,
    );
    return response.data;
  }

  /** Replay every enabled golden set now and return the fresh report. */
  async runEval(agentId: string): Promise<TurAgentEvalReport> {
    const response = await axios.post<TurAgentEvalReport>(
      `/ai-agent/${agentId}/eval/run`,
    );
    return response.data;
  }

  // ─────────────────────────── T597 LLM-assisted generation ───────────────────────────

  /** Synthesize candidate rows for an agent (draft — NOT persisted). */
  async generateRows(
    agentId: string,
    count: number,
  ): Promise<TurEvalDatasetRowDraft[]> {
    const response = await axios.post<TurEvalDatasetRowDraft[]>(
      "/eval/dataset/generate",
      null,
      { params: { agentId, count } },
    );
    return response.data;
  }

  /** Paraphrase an existing dataset's rows into variants (draft — NOT persisted). */
  async augmentDataset(
    datasetId: string,
    variants: number,
  ): Promise<TurEvalDatasetRowDraft[]> {
    const response = await axios.post<TurEvalDatasetRowDraft[]>(
      `/eval/dataset/${datasetId}/augment`,
      null,
      { params: { variants } },
    );
    return response.data;
  }

  /** Persist reviewed draft rows: append to datasetId, else create a new dataset. */
  async saveReviewedRows(
    rows: TurEvalDatasetRowDraft[],
    options: { name?: string; datasetId?: string },
  ): Promise<TurEvalDataset> {
    const params: Record<string, string> = {};
    if (options.name) params.name = options.name;
    if (options.datasetId) params.datasetId = options.datasetId;
    const response = await axios.post<TurEvalDataset>(
      "/eval/dataset/save-reviewed",
      rows,
      { params },
    );
    return response.data;
  }

  // ─────────────────────────── T593 human-review inbox ───────────────────────────

  /** Open (PENDING) human-review tasks parked by an agent's HUMAN grader. */
  async listReviewTasks(agentId: string): Promise<TurEvalReviewTask[]> {
    const response = await axios.get<TurEvalReviewTask[]>(
      `/ai-agent/${agentId}/eval/review`,
    );
    return response.data;
  }

  /** Submit a reviewer verdict; merges back into the latest report. */
  async submitReview(
    agentId: string,
    taskId: string,
    request: TurEvalReviewRequest,
  ): Promise<TurEvalReviewTask> {
    const response = await axios.post<TurEvalReviewTask>(
      `/ai-agent/${agentId}/eval/review/${taskId}`,
      request,
    );
    return response.data;
  }

  /** Promote a reviewed task into a golden dataset row (closes the loop). */
  async promoteReview(
    agentId: string,
    taskId: string,
    datasetId: string,
  ): Promise<TurEvalDataset> {
    const response = await axios.post<TurEvalDataset>(
      `/ai-agent/${agentId}/eval/review/${taskId}/promote`,
      null,
      { params: { datasetId } },
    );
    return response.data;
  }

  // ─────────────────────────── T594 calibration & agreement ───────────────────────────

  /** Inter-annotator agreement (Fleiss' kappa) over the agent's multi-reviewer tasks. */
  async getAgreement(agentId: string): Promise<TurEvalAgreement> {
    const response = await axios.get<TurEvalAgreement>(
      `/ai-agent/${agentId}/eval/review/agreement`,
    );
    return response.data;
  }

  /** MODEL-vs-human calibration over the agent's reviewed audit tasks. */
  async getCalibration(agentId: string): Promise<TurEvalCalibration> {
    const response = await axios.get<TurEvalCalibration>(
      `/ai-agent/${agentId}/eval/review/calibration`,
    );
    return response.data;
  }

  /** Sample MODEL-graded cases from the latest run into audit tasks. */
  async sampleAudit(
    agentId: string,
    sampleSize: number,
    requiredReviewers: number,
  ): Promise<TurEvalReviewTask[]> {
    const response = await axios.post<TurEvalReviewTask[]>(
      `/ai-agent/${agentId}/eval/review/audit`,
      null,
      { params: { sampleSize, requiredReviewers } },
    );
    return response.data;
  }

  /** Set how many reviewers a still-open task needs before consensus. */
  async setRequiredReviewers(
    agentId: string,
    taskId: string,
    count: number,
  ): Promise<TurEvalReviewTask> {
    const response = await axios.post<TurEvalReviewTask>(
      `/ai-agent/${agentId}/eval/review/${taskId}/reviewers`,
      null,
      { params: { count } },
    );
    return response.data;
  }

  // ─────────────────────────── T603 continuous / online eval ───────────────────────────

  /** The agent's online-eval snapshots (newest first) — the drift timeline. */
  async listOnlineHistory(
    agentId: string,
    limit = 50,
  ): Promise<TurOnlineEvalSnapshot[]> {
    const response = await axios.get<TurOnlineEvalSnapshot[]>(
      `/ai-agent/${agentId}/eval/online/history`,
      { params: { limit } },
    );
    return response.data;
  }

  /** Sample live traffic and grade it now; records (or returns "unavailable"). */
  async runOnlineEval(agentId: string): Promise<TurOnlineEvalSnapshot> {
    const response = await axios.post<TurOnlineEvalSnapshot>(
      `/ai-agent/${agentId}/eval/online/run`,
    );
    return response.data;
  }

  /** Promote a snapshot to the agent's healthy drift baseline. */
  async promoteOnlineBaseline(
    agentId: string,
    snapshotId: string,
  ): Promise<TurOnlineEvalSnapshot> {
    const response = await axios.post<TurOnlineEvalSnapshot>(
      `/ai-agent/${agentId}/eval/online/baseline/${snapshotId}`,
    );
    return response.data;
  }
}
