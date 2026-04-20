import type { MetricDataLive } from "@/models/sn/sn-site-metric-live";
import type { TurSNSiteMetricsTerm } from "@/models/sn/sn-site-metrics-term.model";
import axios from "axios";

export type TurSNTopTermsPeriod =
  | "today"
  | "this-week"
  | "this-month"
  | "all-time";

export class TurSNSiteMetricsService {
  async live(siteId: string): Promise<MetricDataLive> {
    const response = await axios.get<MetricDataLive>(
      `/sn/${siteId}/metrics/live`,
    );
    return response.data;
  }
  async topTermsAllTime(
    siteId: string,
    rows: number,
  ): Promise<TurSNSiteMetricsTerm> {
    const response = await axios.get<TurSNSiteMetricsTerm>(
      `/sn/${siteId}/metrics/top-terms/all-time/${rows}`,
    );
    return response.data;
  }

  async topTermsToday(
    siteId: string,
    rows: number,
  ): Promise<TurSNSiteMetricsTerm> {
    const response = await axios.get<TurSNSiteMetricsTerm>(
      `/sn/${siteId}/metrics/top-terms/today/${rows}`,
    );
    return response.data;
  }

  async topTermsThisWeek(
    siteId: string,
    rows: number,
  ): Promise<TurSNSiteMetricsTerm> {
    const response = await axios.get<TurSNSiteMetricsTerm>(
      `/sn/${siteId}/metrics/top-terms/this-week/${rows}`,
    );
    return response.data;
  }

  async topTermsThisMonth(
    siteId: string,
    rows: number,
  ): Promise<TurSNSiteMetricsTerm> {
    const response = await axios.get<TurSNSiteMetricsTerm>(
      `/sn/${siteId}/metrics/top-terms/this-month/${rows}`,
    );
    return response.data;
  }

  async topTermsByPeriod(
    siteId: string,
    period: TurSNTopTermsPeriod,
    rows: number,
  ): Promise<TurSNSiteMetricsTerm> {
    if (period === "today") {
      return this.topTermsToday(siteId, rows);
    }
    if (period === "this-week") {
      return this.topTermsThisWeek(siteId, rows);
    }
    if (period === "all-time") {
      return this.topTermsAllTime(siteId, rows);
    }
    return this.topTermsThisMonth(siteId, rows);
  }
}
