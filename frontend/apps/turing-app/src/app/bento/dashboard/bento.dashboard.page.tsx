import { BentoHero } from "@/components/bento";
import { ROUTES } from "@/app/routes.const";
import { DashboardCustomize } from "@/app/console/dashboard/dashboard-customize";
import { DashboardGrid } from "@/app/console/dashboard/dashboard-grid";
import { useDashboard } from "@/app/console/dashboard/use-dashboard";
import { LiveMetricsWidget } from "@/app/console/dashboard/widgets/live-metrics.widget";
import { LLMStatusWidget } from "@/app/console/dashboard/widgets/llm-status.widget";
import { QuickLinksWidget } from "@/app/console/dashboard/widgets/quick-links.widget";
import { SNSitesWidget } from "@/app/console/dashboard/widgets/sn-sites.widget";
import { SystemHealthWidget } from "@/app/console/dashboard/widgets/system-health.widget";
import { TopTermsWidget } from "@/app/console/dashboard/widgets/top-terms.widget";
import { IconLayoutDashboard } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento dashboard — T565. Re-skins the console dashboard onto the frosted shell:
 * the customize control moves into the {@link BentoHero} trailing slot and the
 * widget grid renders under it. All widget logic + layout persistence is reused
 * unchanged ({@link useDashboard} + {@link DashboardGrid} + the widget set); we
 * only swap the console {@code PageHeader} (which pulls a SidebarTrigger) for a
 * BentoHero so the surface is safe inside a shell with no SidebarProvider.
 */
export default function BentoDashboardPage() {
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
      <BentoHero
        backTo={ROUTES.BENTO_HOME}
        backLabel={t("home.title")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-blue-500 to-indigo-600 text-white shadow-md">
            <IconLayoutDashboard size={24} />
          </span>
        }
        title={t("dashboard.title")}
        trailing={
          <DashboardCustomize
            widgets={widgets}
            onToggle={toggleWidget}
            onSetColSpan={setColSpan}
            onReset={resetLayout}
          />
        }
      />

      {visibleWidgets.length === 0 ? (
        <div className="flex flex-col items-center justify-center gap-3 py-20 text-center">
          <IconLayoutDashboard className="size-12 text-muted-foreground/40" />
          <p className="text-sm text-muted-foreground">{t("dashboard.allHidden")}</p>
          <button
            onClick={resetLayout}
            className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium transition-colors hover:bg-secondary/50"
          >
            {t("dashboard.resetLayout")}
          </button>
        </div>
      ) : (
        <DashboardGrid widgets={visibleWidgets} onReorder={reorder}>
          {visibleWidgets.map((w) => renderWidget(w.id, w.colSpan))}
        </DashboardGrid>
      )}
    </>
  );
}
