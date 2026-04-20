export interface TurDailyUsageRow {
  date: string;
  instanceId: string;
  instanceTitle: string;
  vendorId: string;
  modelName: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  requestCount: number;
}

export interface TurMonthlySummaryRow {
  instanceId: string;
  instanceTitle: string;
  vendorId: string;
  modelName: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  requestCount: number;
}

export interface TurUsageReport {
  periodStart: string;
  periodEnd: string;
  daily: TurDailyUsageRow[];
  summary: TurMonthlySummaryRow[];
  totalInputTokens: number;
  totalOutputTokens: number;
  totalTokens: number;
  totalRequests: number;
}
