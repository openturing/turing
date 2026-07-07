import axios from "axios";
import type { TurPersonaFitReport } from "@/models/persona/persona-fit.model.ts";

/**
 * Client for the content-fit report (Block AA / §XXVI.5). POSTs to
 * `/persona/{id}/content-fit`; omit `sourceId` to evaluate the whole notebook.
 */
export class TurPersonaFitService {
  async evaluate(
    personaId: string,
    options?: { sourceId?: string; regenerate?: boolean }
  ): Promise<TurPersonaFitReport> {
    const params = new URLSearchParams();
    if (options?.sourceId) params.set("sourceId", options.sourceId);
    if (options?.regenerate) params.set("regenerate", "true");
    const query = params.toString();
    const response = await axios.post<TurPersonaFitReport>(
      `/persona/${personaId}/content-fit${query ? `?${query}` : ""}`,
      {}
    );
    return response.data;
  }
}
