import { PageHeader } from "@/components/page-header";
import { IconLayoutDashboard } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { DashboardCustomize } from "./dashboard-customize";
import { DashboardGrid } from "./dashboard-grid";
import { useDashboard } from "./use-dashboard";
import { LiveMetricsWidget } from "./widgets/live-metrics.widget";
import { LLMStatusWidget } from "./widgets/llm-status.widget";
import { QuickLinksWidget } from "./widgets/quick-links.widget";
import { SNSitesWidget } from "./widgets/sn-sites.widget";
import { SystemHealthWidget } from "./widgets/system-health.widget";
import { TopTermsWidget } from "./widgets/top-terms.widget";

export default function DashboardPage() {
  const { t } = useTranslation();
  const { widgets, visibleWidgets, toggleWidget, setColSpan, reorder, resetLayout } = useDashboard();

  function renderWidget(id: string, colSpan: 1 | 2 | 3) {
    switch (id) {
      case "systemHealth":
        return <SystemHealthWidget key={id} onHide={toggleWidget} colSpan={colSpan} />;
      case "liveMetrics":
        return <LiveMetricsWidget key={id} onHide={toggleWidget} colSpan={colSpan} />;
      case "snSites":
        return <SNSitesWidget key={id} onHide={toggleWidget} colSpan={colSpan} />;
      case "topTerms":
        return <TopTermsWidget key={id} onHide={toggleWidget} colSpan={colSpan} />;
      case "llmStatus":
        return <LLMStatusWidget key={id} onHide={toggleWidget} colSpan={colSpan} />;
      case "quickLinks":
        return <QuickLinksWidget key={id} onHide={toggleWidget} colSpan={colSpan} />;
      default:
        return null;
    }
  }

  return (
    <>
      <PageHeader turIcon={IconLayoutDashboard} title={t("dashboard.title")}>
        <DashboardCustomize
          widgets={widgets}
          onToggle={toggleWidget}
          onSetColSpan={setColSpan}
          onReset={resetLayout}
        />
      </PageHeader>

      <div className="px-6 py-4">
        {visibleWidgets.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-center gap-3">
            <IconLayoutDashboard className="size-12 text-muted-foreground/40" />
            <p className="text-muted-foreground text-sm">{t("dashboard.allHidden")}</p>
            <button
              onClick={resetLayout}
              className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs font-medium bg-card hover:bg-secondary/50 transition-colors"
            >
              {t("dashboard.resetLayout")}
            </button>
          </div>
        ) : (
          <DashboardGrid widgets={visibleWidgets} onReorder={reorder}>
            {visibleWidgets.map((w) => renderWidget(w.id, w.colSpan))}
          </DashboardGrid>
        )}
      </div>
    </>
  );
}