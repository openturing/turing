import type { TurEvalDataset } from "@/models/eval/eval-studio.model.ts";
import type {
  TurCopilotPlanningComparison,
  TurCopilotPlanningComparisonRequest,
  TurNLFacetEvalPack,
  TurNLFacetEvalReport,
} from "@/models/sn/sn-nl-facet-eval.model.ts";
import axios from "axios";

/**
 * Client for the NL→facet eval-pack endpoint (T385) plus, since T601, the
 * NL→facet-as-eval-dataset bridge that folds Block R into the Eval Studio.
 *
 * - {@link available} reports whether a usable default LLM is configured (the
 *   eval needs one to parse prose into a structured query).
 * - {@link run} posts an eval pack and returns the pass/fail report. With an
 *   empty `fields` list the backend resolves the schema from the live SN site
 *   named by `index`.
 * - {@link importDataset} / {@link listDatasets} / {@link runDataset} /
 *   {@link deleteDataset} migrate a pack into a reusable {@link TurEvalDataset}
 *   and run it through the intact Block R scorer.
 */
export class TurSNNLFacetEvalService {
  /** True when a usable default LLM is configured (the eval can run). */
  async available(): Promise<boolean> {
    const response = await axios.get<{ available: boolean }>(
      "/sn/nl-facet-eval/available",
    );
    return response.data.available;
  }

  /** Run an eval pack and return the pass/fail report. */
  async run(pack: TurNLFacetEvalPack): Promise<TurNLFacetEvalReport> {
    const response = await axios.post<TurNLFacetEvalReport>(
      "/sn/nl-facet-eval",
      pack,
    );
    return response.data;
  }

  /** T601 — import an eval pack as a reusable NL→facet eval dataset. */
  async importDataset(pack: TurNLFacetEvalPack): Promise<TurEvalDataset> {
    const response = await axios.post<TurEvalDataset>(
      "/sn/nl-facet-eval/dataset",
      pack,
    );
    return response.data;
  }

  /** T601 — list the saved NL→facet eval datasets. */
  async listDatasets(): Promise<TurEvalDataset[]> {
    const response = await axios.get<TurEvalDataset[]>(
      "/sn/nl-facet-eval/dataset",
    );
    return response.data;
  }

  /** T601 — run a saved NL→facet dataset and return the pass/fail report. */
  async runDataset(id: string): Promise<TurNLFacetEvalReport> {
    const response = await axios.post<TurNLFacetEvalReport>(
      `/sn/nl-facet-eval/dataset/${id}/run`,
    );
    return response.data;
  }

  /** T601 — delete a saved NL→facet dataset. */
  async deleteDataset(id: string): Promise<void> {
    await axios.delete(`/sn/nl-facet-eval/dataset/${id}`);
  }

  /**
   * T821 — run an ad-hoc pack through every copilot planning strategy and get the
   * quality / latency / LLM-cost comparison back.
   */
  async comparePlanning(
    request: TurCopilotPlanningComparisonRequest,
  ): Promise<TurCopilotPlanningComparison> {
    const response = await axios.post<TurCopilotPlanningComparison>(
      "/sn/nl-facet-eval/planning-comparison",
      request,
    );
    return response.data;
  }

  /** T821 — the same comparison over a saved NL→facet dataset. */
  async comparePlanningForDataset(
    id: string,
  ): Promise<TurCopilotPlanningComparison> {
    const response = await axios.post<TurCopilotPlanningComparison>(
      `/sn/nl-facet-eval/dataset/${id}/planning-comparison`,
    );
    return response.data;
  }
}
