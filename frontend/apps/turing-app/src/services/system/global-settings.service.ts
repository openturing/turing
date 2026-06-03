import type { TurGlobalSettings } from "@/models/system/global-settings.model";
import axios from "axios";

export class TurGlobalSettingsService {
  async query(): Promise<TurGlobalSettings> {
    const response = await axios.get<TurGlobalSettings>(
      "/system/global-settings",
    );
    return response.data;
  }

  async update(settings: TurGlobalSettings): Promise<TurGlobalSettings> {
    const response = await axios.put<TurGlobalSettings>(
      "/system/global-settings",
      settings,
    );
    return response.data;
  }

  async sendTestEmail(): Promise<{ success: boolean; message: string }> {
    const response = await axios.post<{ success: boolean; message: string }>(
      "/system/global-settings/email/test",
    );
    return response.data;
  }

  /**
   * Reads the configured/blank status of the HMAC URL signing secret used
   * by the Code Interpreter file API. The actual secret value never crosses
   * the wire — only a boolean. Pair with {@link regenerateUrlSigningSecret}
   * to mint a fresh one.
   */
  async getUrlSigningSecretStatus(): Promise<{ configured: boolean }> {
    const response = await axios.get<{ configured: boolean }>(
      "/system/global-settings/code-interpreter/url-signing-secret",
    );
    return response.data;
  }

  /**
   * Mints a fresh HMAC URL signing secret server-side. Invalidates every
   * previously emitted signed download URL across the fleet — intended for
   * operator-initiated rotation (usually a response to a suspected leak).
   * Returns a short preview (first 8 Base64 chars) for the UI to confirm
   * the rotation happened; the full secret is never sent back.
   */
  async regenerateUrlSigningSecret(): Promise<{ rotated: boolean; preview: string }> {
    const response = await axios.post<{ rotated: boolean; preview: string }>(
      "/system/global-settings/code-interpreter/url-signing-secret/regenerate",
    );
    return response.data;
  }

  /**
   * T80 — probes whether Docker is reachable from the running server, so
   * the admin can validate the host before switching the Code Interpreter
   * execution mode to DOCKER. Returns availability + the daemon's server
   * version (or an error string when unavailable).
   */
  async getDockerStatus(): Promise<{
    available: boolean;
    serverVersion: string | null;
    error: string | null;
  }> {
    const response = await axios.get<{
      available: boolean;
      serverVersion: string | null;
      error: string | null;
    }>("/system/global-settings/code-interpreter/docker/status");
    return response.data;
  }
}
