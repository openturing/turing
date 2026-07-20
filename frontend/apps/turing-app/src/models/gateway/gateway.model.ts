/**
 * T748 / §XLIX — client models for the Governed LLM Gateway admin (Block AZ).
 * The secret is never modelled: the API returns only {@link TurGatewayKeyView}
 * (safe projection) plus the raw key exactly once inside {@link TurGatewayCreatedKey}.
 */

export interface TurGatewayKeyView {
  id: string;
  name: string;
  keyPrefix: string;
  allowedModels: string | null;
  tenantId: string | null;
  enabled: number;
  expiresAt: string | null;
  creationDate: string | null;
  monthlyBudgetUsd: number | null;
  budgetDowngradeLlmId: string | null;
  hardMonthlyCapUsd: number | null;
  rateLimitPerMinute: number | null;
}

/** Create/update payload — scope + budget + rate limit; the secret is never sent. */
export interface TurGatewayKeyUpsert {
  name?: string;
  allowedModels?: string | null;
  expiresAt?: string | null;
  monthlyBudgetUsd?: number | null;
  budgetDowngradeLlmId?: string | null;
  hardMonthlyCapUsd?: number | null;
  rateLimitPerMinute?: number | null;
  enabled?: number | null;
}

/** Create / rotate response: the raw `sk-turing-...` key is present exactly once. */
export interface TurGatewayCreatedKey {
  key: TurGatewayKeyView;
  rawKey: string;
}

/** One row of per-key spend for the dashboard. */
export interface TurGatewayKeyUsageRow {
  keyId: string;
  keyName: string;
  costUsd: number;
  totalTokens: number;
  requestCount: number;
}
