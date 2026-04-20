import { TurSNSiteService } from "@/services/sn/sn.service";
import type { TurSNSiteListItem } from "@/models/sn/sn-site-list-item.model";
import type { TurSNSiteStatus } from "@/models/sn/sn-site-monitoring.model";
import { IconCompass, IconRefresh } from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink } from "react-router-dom";
import { ROUTES } from "@/app/routes.const";
import type { WidgetId } from "../use-dashboard";
import { WidgetWrapper } from "./widget-wrapper";

const turSNSiteService = new TurSNSiteService();

interface SiteWithStatus {
  site: TurSNSiteListItem;
  status: TurSNSiteStatus | null;
}

interface SNSitesWidgetProps {
  onHide: (id: WidgetId) => void;
  colSpan?: 1 | 2 | 3;
}

export function SNSitesWidget({ onHide, colSpan }: SNSitesWidgetProps) {
  const { t } = useTranslation();
  const [sites, setSites] = useState<SiteWithStatus[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await turSNSiteService.query();
      const withStatus = await Promise.all(
        list.map(async (site) => {
          try {
            const status = await turSNSiteService.getStatus(site.id);
            return { site, status };
          } catch {
            return { site, status: null };
          }
        })
      );
      setSites(withStatus);
    } catch {
      // silently fail
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <WidgetWrapper
      id="snSites"
      titleKey="dashboard.widgets.snSites.title"
      icon={IconCompass}
      gradient="from-emerald-600 to-teal-600"
      colSpan={colSpan}
      onHide={onHide}
    >
      {loading ? (
        <div className="text-xs text-muted-foreground">{t("dashboard.loading")}</div>
      ) : sites.length === 0 ? (
        <div className="flex flex-col items-center justify-center h-full gap-2 text-center py-4">
          <IconCompass className="size-8 text-muted-foreground/40" />
          <p className="text-xs text-muted-foreground">{t("dashboard.widgets.snSites.noSites")}</p>
          <NavLink
            to={ROUTES.SN_ROOT}
            className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline"
          >
            {t("dashboard.widgets.snSites.createFirst")}
          </NavLink>
        </div>
      ) : (
        <div className="space-y-2">
          {sites.map(({ site, status }) => (
            <div
              key={site.id}
              className="flex items-center justify-between rounded-lg border border-border/50 px-3 py-2 bg-secondary/20 hover:bg-secondary/40 transition-colors"
            >
              <NavLink
                to={`${ROUTES.SN_INSTANCE}/${site.id}/detail`}
                className="flex items-center gap-2 min-w-0"
              >
                <span className="w-1.5 h-1.5 rounded-full bg-gradient-to-br from-emerald-500 to-teal-500 shrink-0" />
                <span className="text-xs font-medium truncate">{site.name}</span>
              </NavLink>
              <div className="flex items-center gap-3 ml-2 shrink-0">
                {status && (
                  <>
                    <span className="text-[10px] text-muted-foreground tabular-nums">
                      {status.documents.toLocaleString()} {t("dashboard.widgets.snSites.docs")}
                    </span>
                    {status.queue > 0 && (
                      <span className="text-[10px] bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400 rounded px-1.5 py-0.5">
                        {status.queue} {t("dashboard.widgets.snSites.queued")}
                      </span>
                    )}
                  </>
                )}
              </div>
            </div>
          ))}
          <button
            onClick={load}
            className="flex items-center gap-1 text-[10px] text-muted-foreground hover:text-foreground transition-colors mt-1"
          >
            <IconRefresh className="size-3" />
            {t("dashboard.refresh")}
          </button>
        </div>
      )}
    </WidgetWrapper>
  );
}
