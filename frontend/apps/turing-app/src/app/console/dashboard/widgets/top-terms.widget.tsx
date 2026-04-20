import { TurSNSiteMetricsService } from "@/services/sn/sn.site.metrics.service";
import { TurSNSiteService } from "@/services/sn/sn.service";
import type { TurSNSiteListItem } from "@/models/sn/sn-site-list-item.model";
import type { TurSNSiteMetricsTopTerm } from "@/models/sn/sn-site-metrics-top-term.model";
import { IconTrendingUp, IconRefresh } from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { WidgetId } from "../use-dashboard";
import { WidgetWrapper } from "./widget-wrapper";

const turSNSiteMetricsService = new TurSNSiteMetricsService();
const turSNSiteService = new TurSNSiteService();

type Period = "today" | "this-week" | "this-month" | "all-time";

interface TopTermsWidgetProps {
  onHide: (id: WidgetId) => void;
  colSpan?: 1 | 2 | 3;
}

export function TopTermsWidget({ onHide, colSpan }: TopTermsWidgetProps) {
  const { t } = useTranslation();
  const [sites, setSites] = useState<TurSNSiteListItem[]>([]);
  const [selectedSiteId, setSelectedSiteId] = useState<string>("");
  const [period, setPeriod] = useState<Period>("today");
  const [terms, setTerms] = useState<TurSNSiteMetricsTopTerm[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    turSNSiteService.query().then((list) => {
      setSites(list);
      if (list.length > 0) setSelectedSiteId(list[0].id);
    }).catch(() => {});
  }, []);

  const load = useCallback(async () => {
    if (!selectedSiteId) return;
    setLoading(true);
    try {
      const result = await turSNSiteMetricsService.topTermsByPeriod(selectedSiteId, period, 10);
      setTerms(result.topTerms ?? []);
    } catch {
      setTerms([]);
    } finally {
      setLoading(false);
    }
  }, [selectedSiteId, period]);

  useEffect(() => {
    load();
  }, [load]);

  const periodOptions: { value: Period; labelKey: string }[] = [
    { value: "today", labelKey: "dashboard.widgets.topTerms.today" },
    { value: "this-week", labelKey: "dashboard.widgets.topTerms.thisWeek" },
    { value: "this-month", labelKey: "dashboard.widgets.topTerms.thisMonth" },
    { value: "all-time", labelKey: "dashboard.widgets.topTerms.allTime" },
  ];

  return (
    <WidgetWrapper
      id="topTerms"
      titleKey="dashboard.widgets.topTerms.title"
      icon={IconTrendingUp}
      gradient="from-purple-600 to-pink-600"
      colSpan={colSpan}
      onHide={onHide}
    >
      <div className="flex flex-col gap-3">
        <div className="flex gap-2 flex-wrap">
          {sites.length > 1 && (
            <select
              value={selectedSiteId}
              onChange={(e) => setSelectedSiteId(e.target.value)}
              className="text-xs border rounded px-2 py-1 bg-background text-foreground"
            >
              {sites.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          )}
          <select
            value={period}
            onChange={(e) => setPeriod(e.target.value as Period)}
            className="text-xs border rounded px-2 py-1 bg-background text-foreground"
          >
            {periodOptions.map((o) => (
              <option key={o.value} value={o.value}>{t(o.labelKey)}</option>
            ))}
          </select>
        </div>

        {sites.length === 0 ? (
          <p className="text-xs text-muted-foreground">{t("dashboard.widgets.topTerms.noSites")}</p>
        ) : loading ? (
          <p className="text-xs text-muted-foreground">{t("dashboard.loading")}</p>
        ) : terms.length === 0 ? (
          <p className="text-xs text-muted-foreground">{t("dashboard.noData")}</p>
        ) : (
          <div className="h-[160px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={terms} layout="vertical" margin={{ left: 0, right: 16 }}>
                <XAxis
                  type="number"
                  stroke="var(--muted-foreground)"
                  fontSize={9}
                  tick={{ fill: "var(--muted-foreground)" }}
                  allowDecimals={false}
                />
                <YAxis
                  type="category"
                  dataKey="term"
                  width={80}
                  stroke="var(--muted-foreground)"
                  fontSize={9}
                  tick={{ fill: "var(--muted-foreground)" }}
                  tickFormatter={(v: string) => v.length > 12 ? `${v.slice(0, 12)}…` : v}
                />
                <Tooltip
                  contentStyle={{
                    background: "var(--card)",
                    border: "1px solid var(--border)",
                    borderRadius: "6px",
                    fontSize: "11px",
                  }}
                />
                <Bar dataKey="total" fill="#a855f7" radius={[0, 3, 3, 0]} name={t("dashboard.widgets.topTerms.searches")} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}

        <button
          onClick={load}
          className="flex items-center gap-1 text-[10px] text-muted-foreground hover:text-foreground transition-colors"
        >
          <IconRefresh className="size-3" />
          {t("dashboard.refresh")}
        </button>
      </div>
    </WidgetWrapper>
  );
}
