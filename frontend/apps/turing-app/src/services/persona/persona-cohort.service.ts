import axios from "axios";
import type { TurPersona } from "@/models/persona/persona.model";

/**
 * Result of an audience-cohort synthesis (Block AW / §XLVI.5, T731) — mirrors the
 * backend `TurAudienceCohortService.CohortResult`. The `personas` are **drafts**:
 * never saved, handed to the persona review form for keep/edit/discard.
 */
export interface CohortResult {
  success: boolean;
  error?: string | null;
  personas: TurPersona[];
}

/** Cohort synthesis request body (mirrors `TurAudienceCohortAPI.CohortRequest`). */
export interface CohortRequest {
  brief: string;
  count: number;
  regenerate: boolean;
}

/**
 * Audience cohort synthesis client (Block AW / §XLVI.5, T731). Wraps
 * `POST /api/persona/cohort`: a one-paragraph brief → a diverse set of unsaved
 * persona drafts with spread OCEAN facets, for the Synthetic Research studio's
 * cohort-from-brief flow.
 */
export class TurPersonaCohortService {
  async synthesize(body: CohortRequest): Promise<CohortResult> {
    const response = await axios.post<CohortResult>("/persona/cohort", body);
    return response.data;
  }
}
