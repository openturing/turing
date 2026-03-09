import axios from "axios";
import type { TurUsageReport } from "@/models/llm/llm-token-usage.model";

export class TurLLMTokenUsageService {
  async getReport(month?: string): Promise<TurUsageReport> {
    const params = month ? { month } : {};
    const response = await axios.get<TurUsageReport>("/v2/llm/token-usage", { params });
    return response.data;
  }
}
