import { TurSNSiteMetricsTopTerm } from "./sn-site-metrics-top-term.model";

export interface TurSNSiteMetricsTerm {
  topTerms: TurSNSiteMetricsTopTerm[];
  totalTermsPeriod: number;
  totalTermsPreviousPeriod: number;
  variationPeriod: number;
}
