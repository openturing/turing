import type {
  TurNLFacetEvalPack,
  TurNLFacetEvalReport,
} from "@/models/sn/sn-nl-facet-eval.model.ts";
import axios from "axios";

/**
 * Client for the NL→facet eval-pack endpoint (T385).
 *
 * - {@link available} reports whether a usable default LLM is configured (the
 *   eval needs one to parse prose into a structured query).
 * - {@link run} posts an eval pack and returns the pass/fail report. With an
 *   empty `fields` list the backend resolves the schema from the live SN site
 *   named by `index`.
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
}
