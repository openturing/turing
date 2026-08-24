import { TurSNSiteMetricsService } from "@/services/sn/sn.site.metrics.service";
import { useSnSites } from "@/api/queries/sn-site.queries";
import { IconChartLine } from "@tabler/icons-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { WidgetId } from "../use-dashboard";
import { WidgetWrapper } from "./widget-wrapper";

const turSNSiteMetricsService = new TurSNSiteMetricsService();

interface LivePoint {
  time: number;
  accesses: number;
}

interface LiveMetricsWidgetProps {
  onHide: (id: WidgetId) => void;
  colSpan?: 1 | 2 | 3;
}

export function LiveMetricsWidget({ onHide, colSpan }: LiveMetricsWidgetProps) {
  const { t } = useTranslation();
  const [data, setData] = useState<LivePoint[]>([]);
  // T1018 — the site list comes from the shared hook, and the selection is
  // derived: an unpicked one is the first site. The effect that fetched the list
  // also wrote that default into state, which is the pattern this task removes.
  // The rolling window below stays local — it accumulates 60 points from a 2s
  // poll, which is not a cacheable read.
  const { data: sites = [] } = useSnSites();
  const [pickedSiteId, setPickedSiteId] = useState<string>("");
  const selectedSiteId = pickedSiteId || sites[0]?.id || "";
  const selectedRef = useRef(selectedSiteId);
  // T1020 — the mirror is written after commit, not during render. A render
  // React starts and abandons still runs this line, so writing it inline could
  // leave the ref holding a value no commit ever produced; an effect only runs
  // for the render that won. What reads it below runs after a commit too, so it
  // still sees the latest value.
  useEffect(() => {
    selectedRef.current = selectedSiteId
  }, [selectedSiteId])

  const fetchMetrics = useCallback(async () => {
    const siteId = selectedRef.current;
    if (!siteId) return;
    try {
      const res = await turSNSiteMetricsService.live(siteId);
      const arr = Array.isArray(res) ? res : [res];
      setData((prev) => {
        const newPoints: LivePoint[] = arr
          .map((p) => {
            const t = typeof p.time === "number" ? p.time : parseFloat(String(p.time));
            return isNaN(t) ? null : { time: t, accesses: p.accesses };
          })
          .filter((p): p is LivePoint => p !== null);
        return [...prev, ...newPoints].slice(-60);
      });
    } catch {
      // silently fail
    }
  }, []);

  // T1018 — this one stays an effect. It owns a 2s interval, which is the
  // external system effects are for, and the synchronous `setData([])` empties
  // the rolling window because those sixty points belong to the site being left.
  // Doing it anywhere later would chart the previous site's traffic under the
  // new one's name.
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!selectedSiteId) return;
    setData([]);
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 2000);
    return () => clearInterval(interval);
  }, [selectedSiteId, fetchMetrics]);
  /* eslint-enable react-hooks/set-state-in-effect */

  return (
    <WidgetWrapper
      id="liveMetrics"
      titleKey="dashboard.widgets.liveMetrics.title"
      icon={IconChartLine}
      gradient="from-blue-600 to-indigo-600"
      colSpan={colSpan}
      onHide={onHide}
    >
      <div className="flex flex-col gap-3 h-full">
        {sites.length > 1 && (
          <select
            value={selectedSiteId}
            onChange={(e) => setPickedSiteId(e.target.value)}
            className="w-full text-xs border rounded px-2 py-1 bg-background text-foreground"
          >
            {sites.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
        )}
        {sites.length === 0 ? (
          <p className="text-xs text-muted-foreground">{t("dashboard.widgets.liveMetrics.noSites")}</p>
        ) : (
          <div className="h-[180px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={data}>
                <defs>
                  <linearGradient id="liveGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.7} />
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border)" />
                <XAxis
                  dataKey="time"
                  type="number"
                  domain={["dataMin", "dataMax"]}
                  tickFormatter={(v) =>
                    new Date(v * 1000).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" })
                  }
                  stroke="var(--muted-foreground)"
                  fontSize={9}
                  tick={{ fill: "var(--muted-foreground)" }}
                />
                <YAxis
                  stroke="var(--muted-foreground)"
                  fontSize={9}
                  tick={{ fill: "var(--muted-foreground)" }}
                  domain={[0, "auto"]}
                  allowDecimals={false}
                />
                <Tooltip
                  contentStyle={{
                    background: "var(--card)",
                    border: "1px solid var(--border)",
                    borderRadius: "6px",
                    fontSize: "11px",
                  }}
                  labelFormatter={(v) =>
                    new Date(Number(v) * 1000).toLocaleTimeString()
                  }
                />
                <Area
                  type="step"
                  dataKey="accesses"
                  stroke="#3b82f6"
                  fillOpacity={1}
                  fill="url(#liveGradient)"
                  isAnimationActive={false}
                  dot={false}
                  name={t("dashboard.widgets.liveMetrics.accesses")}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}
        <p className="text-[10px] text-muted-foreground">{t("dashboard.widgets.liveMetrics.description")}</p>
      </div>
    </WidgetWrapper>
  );
}
