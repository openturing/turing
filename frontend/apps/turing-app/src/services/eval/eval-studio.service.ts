import axios from "axios";
import type {
  TurEvalDataset,
  TurEvalGraderStack,
} from "@/models/eval/eval-studio.model";

/**
 * T595–T600 / Block AJ — client for the reusable, agent-decoupled eval assets
 * surfaced in the Eval Studio: datasets and named grader stacks.
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
}
