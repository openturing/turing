import axios from "axios";
import type { TurPersonaSuggestionReport } from "@/models/persona/persona-suggestion.model.ts";

/**
 * Client for the auto-suggest endpoint (Block AA / §XXVI.8). POSTs content to
 * `/persona/suggest` and gets back the audience personas ranked by fit. Omit
 * `personaIds` to consider every enabled audience persona.
 */
export class TurPersonaSuggestionService {
  async suggest(
    content: string,
    options?: { personaIds?: string[]; regenerate?: boolean }
  ): Promise<TurPersonaSuggestionReport> {
    const query = options?.regenerate ? "?regenerate=true" : "";
    const response = await axios.post<TurPersonaSuggestionReport>(
      `/persona/suggest${query}`,
      { content, personaIds: options?.personaIds ?? null }
    );
    return response.data;
  }
}
