/**
 * T290 / §XVI.2 (Block L) — cost & token governance dashboard models.
 * Mirror the backend records in {@code TurLLMCostAPI} and {@code TurLLMPriceAPI}.
 */

export interface TurAgentCostRow {
  agentId: string | null;
  agentTitle: string;
  costUsd: number;
  totalTokens: number;
  requestCount: number;
}

export interface TurModelCostRow {
  vendorId: string;
  modelName: string;
  costUsd: number;
  totalTokens: number;
  requestCount: number;
  inputTokens: number;
  outputTokens: number;
}

export interface TurStageCostRow {
  stage: string;
  costUsd: number;
  totalTokens: number;
  requestCount: number;
}

export interface TurTenantCostRow {
  tenantId: string | null;
  costUsd: number;
  totalTokens: number;
  requestCount: number;
}

export interface TurCostReport {
  periodStart: string;
  periodEnd: string;
  totalCostUsd: number;
  totalTokens: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalRequests: number;
  byAgent: TurAgentCostRow[];
  byModel: TurModelCostRow[];
  byStage: TurStageCostRow[];
  byTenant: TurTenantCostRow[];
}

export interface TurDailyCostPoint {
  date: string;
  costUsd: number;
  totalTokens: number;
  requestCount: number;
}

/** Row in the admin-editable per-model price table ({@code tur_llm_price}). */
export interface TurLLMPrice {
  id?: string;
  vendorId: string;
  modelName: string;
  inputPricePerMillion: number;
  outputPricePerMillion: number;
  currency?: string;
  updatedAt?: string;
}
