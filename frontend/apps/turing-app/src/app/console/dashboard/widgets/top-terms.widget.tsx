import { useSnSites } from "@/api/queries/sn-site.queries";
import { useSnTopTerms } from "@/api/queries/sn-site-metrics.queries";
import { IconTrendingUp, IconRefresh } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { WidgetId } from "../use-dashboard";
import { WidgetWrapper } from "./widget-wrapper";

type Period = "today" | "this-week" | "this-month" | "all-time";

interface TopTermsWidgetProps {
  onHide: (id: WidgetId) => void;
  colSpan?: 1 | 2 | 3;
}

export function TopTermsWidget({ onHide, colSpan }: TopTermsWidgetProps) {
  const { t } = useTranslation();
  // T1018 — both reads go through hooks now. The site list was fetched here and
  // its first entry written into the selection by an effect; an unpicked
  // selection is just the first site, so it is derived. The terms query is
  // disabled until there is one, which is what the `if (!selectedSiteId) return`
  // guard did.
  const { data: sites = [] } = useSnSites();
  const [pickedSiteId, setPickedSiteId] = useState<string>("");
  const [period, setPeriod] = useState<Period>("today");
  const selectedSiteId = pickedSiteId || sites[0]?.id || "";

  const { data: topTerms, isLoading: loading, refetch } = useSnTopTerms(selectedSiteId, period, 10);
  const terms = topTerms?.topTerms ?? [];

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
              onChange={(e) => setPickedSiteId(e.target.value)}
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
          onClick={() => void refetch()}
          className="flex items-center gap-1 text-[10px] text-muted-foreground hover:text-foreground transition-colors"
        >
          <IconRefresh className="size-3" />
          {t("dashboard.refresh")}
        </button>
      </div>
    </WidgetWrapper>
  );
}
