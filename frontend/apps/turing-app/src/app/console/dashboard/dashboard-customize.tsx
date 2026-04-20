import { IconCheck, IconColumns2, IconColumns3, IconLayoutColumns, IconRefreshAlert, IconSettings2 } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { WidgetConfig, WidgetId } from "./use-dashboard";

interface DashboardCustomizeProps {
  widgets: WidgetConfig[];
  onToggle: (id: WidgetId) => void;
  onSetColSpan: (id: WidgetId, colSpan: 1 | 2 | 3) => void;
  onReset: () => void;
}

const WIDGET_LABEL_KEYS: Record<WidgetId, string> = {
  systemHealth: "dashboard.widgets.systemHealth.title",
  liveMetrics: "dashboard.widgets.liveMetrics.title",
  snSites: "dashboard.widgets.snSites.title",
  topTerms: "dashboard.widgets.topTerms.title",
  llmStatus: "dashboard.widgets.llmStatus.title",
  quickLinks: "dashboard.widgets.quickLinks.title",
};

export function DashboardCustomize({ widgets, onToggle, onSetColSpan, onReset }: DashboardCustomizeProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs font-medium bg-card hover:bg-secondary/50 transition-colors"
      >
        <IconSettings2 className="size-3.5" />
        {t("dashboard.customize")}
      </button>

      {open && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 z-40"
            onClick={() => setOpen(false)}
          />
          {/* Panel */}
          <div className="absolute right-0 top-full mt-2 z-50 w-72 rounded-xl border border-border bg-card shadow-lg p-4">
            <div className="flex items-center justify-between mb-3">
              <p className="text-sm font-semibold">{t("dashboard.customizeTitle")}</p>
              <button
                onClick={() => { onReset(); setOpen(false); }}
                className="flex items-center gap-1 text-[10px] text-muted-foreground hover:text-foreground transition-colors"
              >
                <IconRefreshAlert className="size-3" />
                {t("dashboard.resetLayout")}
              </button>
            </div>

            <div className="space-y-1">
              {widgets.map((w) => (
                <div key={w.id} className="flex items-center gap-2 rounded-lg p-2 hover:bg-secondary/40 transition-colors">
                  {/* Visibility toggle */}
                  <button
                    onClick={() => onToggle(w.id)}
                    className={`flex-shrink-0 w-5 h-5 rounded border-2 flex items-center justify-center transition-colors ${
                      w.visible
                        ? "bg-gradient-to-br from-blue-600 to-indigo-600 border-transparent"
                        : "border-border bg-background"
                    }`}
                  >
                    {w.visible && <IconCheck className="size-3 text-white" />}
                  </button>
                  <span className="flex-1 text-xs">{t(WIDGET_LABEL_KEYS[w.id])}</span>

                  {/* ColSpan controls */}
                  <div className="flex items-center gap-0.5">
                    {([1, 2, 3] as const).map((span) => {
                      const Icon = span === 1 ? IconLayoutColumns : span === 2 ? IconColumns2 : IconColumns3;
                      return (
                        <button
                          key={span}
                          onClick={() => onSetColSpan(w.id, span)}
                          disabled={!w.visible}
                          title={`${span} ${t("dashboard.columns")}`}
                          className={`p-1 rounded transition-colors ${
                            w.colSpan === span
                              ? "bg-gradient-to-br from-blue-600 to-indigo-600 text-white"
                              : "text-muted-foreground hover:bg-secondary/50 disabled:opacity-30"
                          }`}
                        >
                          <Icon className="size-3" />
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>

            <p className="text-[10px] text-muted-foreground mt-3 pt-3 border-t border-border/50">
              {t("dashboard.dragHint")}
            </p>
          </div>
        </>
      )}
    </div>
  );
}
