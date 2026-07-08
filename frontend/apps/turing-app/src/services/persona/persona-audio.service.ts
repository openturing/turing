import axios from "axios";
import type { PersonaDraftResult } from "@/models/persona/persona-audio.model.ts";

/**
 * Client for persona-from-audio derivation (Block AA / §XXVI.7). POSTs an audio
 * file to `/persona/derive-from-audio`; the returned draft is never saved — the
 * caller opens the persona form pre-filled for review.
 */
export class TurPersonaAudioService {
  async deriveFromAudio(
    file: File,
    language?: string
  ): Promise<PersonaDraftResult> {
    const form = new FormData();
    form.append("file", file);
    if (language) {
      form.append("language", language);
    }
    const response = await axios.post<PersonaDraftResult>(
      "/persona/derive-from-audio",
      form
    );
    return response.data;
  }
}
