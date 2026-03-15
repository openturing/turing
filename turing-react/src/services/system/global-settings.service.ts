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
}
