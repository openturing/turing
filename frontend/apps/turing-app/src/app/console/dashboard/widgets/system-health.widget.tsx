import { TurSystemInfoService } from "@/services/system/system-info.service";
import { IconCpu, IconDatabase, IconDeviceFloppy, IconRefresh } from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import type { WidgetId } from "../use-dashboard";
import { WidgetWrapper } from "./widget-wrapper";

const turSystemInfoService = new TurSystemInfoService();

interface SystemHealthData {
  memoryUsedPct: number;
  diskUsedPct: number;
  memoryUsedMb: number;
  memoryTotalMb: number;
  diskUsedGb: number;
  diskTotalGb: number;
  dbStatus: string;
}

interface SystemHealthWidgetProps {
  onHide: (id: WidgetId) => void;
  colSpan?: 1 | 2 | 3;
}

function GaugeBar({ value, color }: { value: number; color: string }) {
  return (
    <div className="w-full h-2 bg-secondary rounded-full overflow-hidden">
      <div
        className={`h-full rounded-full transition-all duration-700 ${color}`}
        style={{ width: `${Math.min(value, 100)}%` }}
      />
    </div>
  );
}

function getBarColor(pct: number): string {
  if (pct >= 90) return "bg-red-500";
  if (pct >= 75) return "bg-amber-500";
  return "bg-gradient-to-r from-blue-600 to-indigo-600";
}

export function SystemHealthWidget({ onHide, colSpan }: SystemHealthWidgetProps) {
  const { t } = useTranslation();
  const [data, setData] = useState<SystemHealthData | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const info = await turSystemInfoService.getInfo();
      const memUsed = info.memory.usedMemory;
      const memTotal = info.memory.maxMemory;
      const diskUsed = info.disk.usedSpace;
      const diskTotal = info.disk.totalSpace;
      setData({
        memoryUsedPct: Math.round((memUsed / memTotal) * 100),
        diskUsedPct: Math.round((diskUsed / diskTotal) * 100),
        memoryUsedMb: Math.round(memUsed / 1048576),
        memoryTotalMb: Math.round(memTotal / 1048576),
        diskUsedGb: Math.round((diskUsed / 1073741824) * 10) / 10,
        diskTotalGb: Math.round((diskTotal / 1073741824) * 10) / 10,
        dbStatus: info.database.status,
      });
    } catch {
      // silently fail
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const interval = setInterval(load, 15000);
    return () => clearInterval(interval);
  }, [load]);

  return (
    <WidgetWrapper
      id="systemHealth"
      titleKey="dashboard.widgets.systemHealth.title"
      icon={IconCpu}
      gradient="from-emerald-600 to-teal-600"
      colSpan={colSpan}
      onHide={onHide}
    >
      {loading ? (
        <div className="flex items-center justify-center h-full text-muted-foreground text-xs">
          {t("dashboard.loading")}
        </div>
      ) : data ? (
        <div className="space-y-4">
          {/* Memory */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-xs">
              <span className="flex items-center gap-1.5 text-muted-foreground">
                <IconDeviceFloppy className="size-3.5" />
                {t("dashboard.widgets.systemHealth.memory")}
              </span>
              <span className="font-medium tabular-nums">
                {data.memoryUsedMb} / {data.memoryTotalMb} MB
              </span>
            </div>
            <GaugeBar value={data.memoryUsedPct} color={getBarColor(data.memoryUsedPct)} />
            <p className="text-[10px] text-muted-foreground text-right">{data.memoryUsedPct}% {t("dashboard.widgets.systemHealth.used")}</p>
          </div>

          {/* Disk */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-xs">
              <span className="flex items-center gap-1.5 text-muted-foreground">
                <IconDatabase className="size-3.5" />
                {t("dashboard.widgets.systemHealth.disk")}
              </span>
              <span className="font-medium tabular-nums">
                {data.diskUsedGb} / {data.diskTotalGb} GB
              </span>
            </div>
            <GaugeBar value={data.diskUsedPct} color={getBarColor(data.diskUsedPct)} />
            <p className="text-[10px] text-muted-foreground text-right">{data.diskUsedPct}% {t("dashboard.widgets.systemHealth.used")}</p>
          </div>

          {/* DB Status */}
          <div className="flex items-center gap-2 pt-1 border-t border-border/40">
            <span className="text-xs text-muted-foreground">{t("dashboard.widgets.systemHealth.database")}:</span>
            <span className={`text-xs font-medium ${data.dbStatus === "OK" ? "text-emerald-600" : "text-red-500"}`}>
              {data.dbStatus}
            </span>
          </div>

          <button
            onClick={load}
            className="flex items-center gap-1 text-[10px] text-muted-foreground hover:text-foreground transition-colors"
          >
            <IconRefresh className="size-3" />
            {t("dashboard.refresh")}
          </button>
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">{t("dashboard.noData")}</p>
      )}
    </WidgetWrapper>
  );
}
