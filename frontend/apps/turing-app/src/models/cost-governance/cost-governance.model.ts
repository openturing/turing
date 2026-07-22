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

/**
 * T184 / §X.14.d — per-agent budget status for the AI-Spend card's alert
 * thresholds. Mirrors {@code TurLLMCostAPI.AgentBudgetStatus}.
 */
export interface TurAgentBudgetStatus {
  agentId: string;
  agentTitle: string;
  monthlyBudgetUsd: number;
  monthToDateSpendUsd: number;
  projectedMonthEndUsd: number;
  overBudget: boolean;
  projectedOverBudget: boolean;
  perTurnSoftCapUsd: number | null;
  downgradeConfigured: boolean;
}

/** Provenance of a price row (T777/T778). */
export type TurLLMPriceSource = "CATALOG" | "MANUAL";

/** Row in the admin-editable per-model price table ({@code tur_llm_price}). */
export interface TurLLMPrice {
  id?: string;
  vendorId: string;
  modelName: string;
  inputPricePerMillion: number;
  outputPricePerMillion: number;
  currency?: string;
  updatedAt?: string;
  /**
   * T777/T778 — provenance: `MANUAL` (operator-entered / negotiated, always wins)
   * or `CATALOG` (auto-seeded from the public model catalog).
   */
  source?: TurLLMPriceSource;
  /** T778 — whether the rate is an indicative (published list) price. */
  indicative?: boolean;
  /** T778 — catalog pricing provenance (e.g. `litellm`) for CATALOG rows. */
  priceProvenance?: string;
  /** T778 — catalog `lastVerified` ISO date, for the staleness warning. */
  lastVerified?: string;
}
