import { ROUTES } from "@/app/routes.const";
import { AemMonitoringGrid } from "@/components/integration/aem.monitoring.grid";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurIntegrationMonitoring } from "@/models/integration/integration-monitoring.model";
import { TurIntegrationMonitoringService } from "@/services/integration/integration-monitoring.service";
import { IconGraph } from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

const DEFAULT_SOURCE = "all";

export default function IntegrationInstanceMonitoringPage() {
  const navigate = useNavigate();
  const { id, source = DEFAULT_SOURCE } = useParams<{ id: string; source?: string }>();

  const [integrationMonitoring, setIntegrationMonitoring] = useState<TurIntegrationMonitoring>();
  const [error, setError] = useState<string | null>(null);

  const monitoringService = useMemo(() => {
    return id ? new TurIntegrationMonitoringService(id) : null;
  }, [id]);

  const { pushItem, popItem } = useBreadcrumb();
  const [refreshInterval, setRefreshInterval] = useState(5000);
  const handleTabChange = useCallback((newSource: string) => {
    navigate(`${ROUTES.INTEGRATION_INSTANCE}/${id}/monitoring/${newSource}`);
  }, [navigate, id]);
  const REFRESH_OPTIONS = [
    { label: "Off", value: 0 },
    { label: "1s", value: 1000 },
    { label: "5s", value: 5000 },
    { label: "10s", value: 10000 },
    { label: "30s", value: 30000 },
    { label: "1m", value: 60000 },
    { label: "5m", value: 300000 },
  ];
  useEffect(() => {
    let isMounted = true;
    let itemsAdded = 0;

    const fetchData = async (isSilent = false) => {
      if (!monitoringService || !id) return;

      try {
        const data = source === DEFAULT_SOURCE
          ? await monitoringService.query()
          : await monitoringService.get(source);

        if (!isMounted) return;

        setIntegrationMonitoring({
          sources: data.sources || [],
          indexing: data.indexing || [],
        });

        if (!isSilent && itemsAdded === 0) {
          pushItem({ label: "Monitoring", href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/monitoring` });
          pushItem({ label: source === DEFAULT_SOURCE ? "All" : source });
          itemsAdded = 2;
        }
      } catch (err) {
        if (!isSilent) setError("Failed to load monitoring data.");
        console.error(err);
      }
    };

    fetchData();
    let interval: ReturnType<typeof setInterval>;
    if (refreshInterval > 0) {
      interval = setInterval(() => {
        fetchData(true);
      }, refreshInterval);
    }

    return () => {
      isMounted = false;
      if (interval) clearInterval(interval);
      for (let i = 0; i < itemsAdded; i++) {
        popItem();
      }
    };
  }, [source, id, refreshInterval]);

  if (!id) {
    return <div>Invalid integration instance ID</div>;
  }

  return (
    <LoadProvider checkIsNotUndefined={integrationMonitoring} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/monitoring`}>
      <SubPageHeader
        icon={IconGraph}
        name="Monitoring"
        feature="Monitoring"
        description="Verify the current status of AEM content indexing."
      />
      {integrationMonitoring && (
        <>
          <div className="flex items-center justify-between px-6 py-2 border-b">
            <Tabs
              value={source}
              onValueChange={handleTabChange}
              className="w-auto"
            >
              <TabsList className="bg-transparent border-none">
                <TabsTrigger value={DEFAULT_SOURCE}>All</TabsTrigger>
                {integrationMonitoring?.sources?.map((tab) => (
                  <TabsTrigger key={tab} value={tab}>
                    {tab}
                  </TabsTrigger>
                ))}
              </TabsList>
            </Tabs>
            <div className="flex items-center gap-2">
              <span className="text-xs text-muted-foreground italic">Auto-refreshing every:</span>
              <Select
                value={String(refreshInterval)}
                onValueChange={(v) => setRefreshInterval(Number(v))}
              >
                <SelectTrigger className="w-20 h-8 text-xs font-mono">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {REFRESH_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={String(opt.value)} className="text-xs">
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* Grid abaixo da linha de controles */}
          <div className="mt-4">
            <AemMonitoringGrid
              gridItemList={integrationMonitoring?.indexing || []}
              refreshInterval={refreshInterval}
            />
          </div>
        </>
      )}
    </LoadProvider>
  );
}
