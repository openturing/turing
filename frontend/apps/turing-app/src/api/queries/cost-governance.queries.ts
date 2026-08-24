import { useQuery } from "@tanstack/react-query";
import type {
  TurCostReport,
  TurDailyCostPoint,
  TurLLMConsumerPlan,
  TurLLMPrice,
} from "@/models/cost-governance/cost-governance.model";
import { TurCostGovernanceService } from "@/services/cost-governance/cost-governance.service";
import { queryKeys } from "./keys";

const service = new TurCostGovernanceService();

/** T1018 — spend totals for one date range. */
export function useCostSummary(from: string, to: string) {
  return useQuery<TurCostReport>({
    queryKey: queryKeys.costGovernance.summary(from, to),
    queryFn: () => service.getSummary(from, to),
  });
}

/** T1018 — the daily series behind the chart, for the same range. */
export function useCostTimeseries(from: string, to: string) {
  return useQuery<TurDailyCostPoint[]>({
    queryKey: queryKeys.costGovernance.timeseries(from, to),
    queryFn: () => service.getTimeseries(from, to),
  });
}

/**
 * T1018 — the per-model price table.
 *
 * Its own entry rather than part of the range queries: prices do not move with
 * the dates, so changing the range must not re-fetch them.
 */
export function useLlmPrices() {
  return useQuery<TurLLMPrice[]>({
    queryKey: queryKeys.costGovernance.prices(),
    queryFn: () => service.listPrices(),
  });
}

/** T1018 — vendor pricing-page links, keyed by provider. */
export function useProviderLinks() {
  return useQuery<Record<string, string>>({
    queryKey: queryKeys.costGovernance.providerLinks(),
    queryFn: () => service.getProviderLinks(),
  });
}

/** T1018 — the published consumer plans, keyed by provider. */
export function useConsumerPlans() {
  return useQuery<Record<string, TurLLMConsumerPlan[]>>({
    queryKey: queryKeys.costGovernance.consumerPlans(),
    queryFn: () => service.getConsumerPlans(),
  });
}
