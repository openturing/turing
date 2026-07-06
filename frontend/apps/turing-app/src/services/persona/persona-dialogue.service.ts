import axios from "axios";
import type {
  TurDialogueTranscript,
  TurPersonaDialogueRequest,
} from "@/models/persona/persona-dialogue.model";

/**
 * Block AI / §XXXII.8 (T585) — runs a bounded turn-by-turn dialogue between two
 * personas on a topic. A single blocking POST; the backend orchestrates every
 * turn and returns the whole transcript (`/api/v2/persona-dialogue`).
 */
export class TurPersonaDialogueService {
  async run(request: TurPersonaDialogueRequest): Promise<TurDialogueTranscript> {
    const response = await axios.post<TurDialogueTranscript>(
      "/v2/persona-dialogue",
      request,
    );
    return response.data;
  }
}
