import type { TurSystemInfo } from "@/models/system/system-info.model";
import axios from "axios";

export class TurSystemInfoService {
  async getInfo(): Promise<TurSystemInfo> {
    const response = await axios.get<TurSystemInfo>("/system/info");
    return response.data;
  }

  async getVariables(): Promise<Record<string, string>> {
    const response = await axios.get<Record<string, string>>(
      "/system/info/variables",
    );
    return response.data;
  }
}
