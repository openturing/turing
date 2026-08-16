import axios from "axios";
import type {
  TurVigletPairingPoll,
  TurVigletPairingSession,
  TurVigletPairingStatus,
} from "@/models/genai/viglet-ai.model.ts";

/**
 * T958 — client for the T957 device-code pairing endpoints
 * (`/api/genai/viglet-ai/pairing/**`). Three calls, one conversation: ask
 * whether this install is paired, start an attempt, poll it until the
 * credential lands server-side.
 */
export class TurVigletAiService {
  /**
   * Whether this install is already paired. Not read by the activation card —
   * that surface only exists while there is no model at all — but it is the
   * probe the managed-plan surfaces (T959/T960) read.
   */
  async status(): Promise<TurVigletPairingStatus> {
    const response = await axios.get<TurVigletPairingStatus>("/genai/viglet-ai/pairing/status");
    return response.data;
  }

  async start(): Promise<TurVigletPairingSession> {
    const response = await axios.post<TurVigletPairingSession>("/genai/viglet-ai/pairing/start");
    return response.data;
  }

  async poll(deviceCode: string): Promise<TurVigletPairingPoll> {
    const response = await axios.post<TurVigletPairingPoll>("/genai/viglet-ai/pairing/poll", {
      deviceCode,
    });
    return response.data;
  }
}
