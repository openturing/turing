import axios from "axios";
import type {
  TurAgentBudgetStatus,
  TurBatchCostEstimate,
  TurBatchCostRequest,
  TurCostReport,
  TurDailyCostPoint,
  TurLLMConsumerPlan,
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

  /**
   * T778 — per-vendor official "verify at vendor" pricing-page links, keyed by
   * lower-cased vendor slug, from the public catalog's providers registry.
   */
  async getProviderLinks(): Promise<Record<string, string>> {
    const response = await axios.get<Record<string, string>>("/v2/llm/price/provider-links");
    return response.data ?? {};
  }

  /**
   * T789 — indicative consumer subscription plans per vendor from the public
   * catalog's plans reference (admin help). Reference only — verify at vendor.
   */
  async getConsumerPlans(): Promise<Record<string, TurLLMConsumerPlan[]>> {
    const response = await axios.get<Record<string, TurLLMConsumerPlan[]>>("/v2/llm/price/consumer-plans");
    return response.data ?? {};
  }

  /** T783 — quote the projected cost of a batch run before it spends. */
  async preflightBatch(request: TurBatchCostRequest): Promise<TurBatchCostEstimate> {
    const response = await axios.post<TurBatchCostEstimate>("/v2/llm/cost/preflight-batch", request);
    return response.data;
  }
}
