import axios from "axios";
import type {
  TurEvalDataset,
  TurEvalGraderStack,
  TurEvalReviewRequest,
  TurEvalReviewTask,
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
}
