import axios from "axios";
import type {
  TurAgentBudgetStatus,
  TurCostReport,
  TurDailyCostPoint,
  TurLLMPrice,
} from "@/models/cost-governance/cost-governance.model";

/**
 * T290 / §XVI.2 (Block L) — client for the live AI-spend dashboard and the
 * per-model price table. {@code from}/{@code to} are ISO dates (YYYY-MM-DD);
 * omitted → backend defaults to the last 30 days.
 */
export class TurCostGovernanceService {
  async getSummary(from?: string, to?: string): Promise<TurCostReport> {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await axios.get<TurCostReport>("/v2/llm/cost/summary", { params });
    return response.data;
  }

  async getBudgetStatus(): Promise<TurAgentBudgetStatus[]> {
    const response = await axios.get<TurAgentBudgetStatus[]>("/v2/llm/cost/budget-status");
    return response.data ?? [];
  }

  async getTimeseries(from?: string, to?: string): Promise<TurDailyCostPoint[]> {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await axios.get<TurDailyCostPoint[]>("/v2/llm/cost/timeseries", { params });
    return response.data ?? [];
  }

  async listPrices(): Promise<TurLLMPrice[]> {
    const response = await axios.get<TurLLMPrice[]>("/v2/llm/price");
    return response.data ?? [];
  }

  async savePrice(price: TurLLMPrice): Promise<TurLLMPrice> {
    const response = await axios.post<TurLLMPrice>("/v2/llm/price", price);
    return response.data;
  }

  async deletePrice(id: string): Promise<void> {
    await axios.delete(`/v2/llm/price/${id}`);
  }
}
