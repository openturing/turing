import { ROUTES } from "@/app/routes.const";
import { AemMonitoringGrid } from "@/components/integration/aem.monitoring.grid";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurIntegrationMonitoringPage } from "@/models/integration/integration-monitoring-page.model";
import type { TurIntegrationMonitoringRequest } from "@/models/integration/integration-monitoring-request.model";
import { TurIntegrationMonitoringService } from "@/services/integration/integration-monitoring.service";
import { IconFilter, IconFilterOff, IconGraph } from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

const DEFAULT_SOURCE = "all";

const STATUS_OPTIONS = [
  "PREPARE_INDEX",
  "PREPARE_UNCHANGED",
  "PREPARE_REINDEX",
  "PREPARE_FORCED_REINDEX",
  "RECEIVED_AND_SENT_TO_TURING",
  "SENT_TO_QUEUE",
  "RECEIVED_FROM_QUEUE",
  "INDEXED",
  "FINISHED",
  "DEINDEXED",
  "NOT_PROCESSED",
  "IGNORED",
];

const REFRESH_OPTIONS = [
  { label: "Off", value: 0 },
  { label: "1s", value: 1000 },
  { label: "5s", value: 5000 },
  { label: "10s", value: 10000 },
  { label: "30s", value: 30000 },
  { label: "1m", value: 60000 },
  { label: "5m", value: 300000 },
];

const NONE_VALUE = "__none__";

export default function IntegrationInstanceMonitoringPage() {
  const navigate = useNavigate();
  const { id, source = DEFAULT_SOURCE } = useParams<{ id: string; source?: string }>();

  const [monitoringPage, setMonitoringPage] = useState<TurIntegrationMonitoringPage>();
  const [error, setError] = useState<string | null>(null);
  const [showFilters, setShowFilters] = useState(false);

  const [objectIdFilter, setObjectIdFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [environmentFilter, setEnvironmentFilter] = useState("");
  const [localeFilter, setLocaleFilter] = useState("");
  const [siteFilter, setSiteFilter] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  const [sortBy, setSortBy] = useState("modificationDate");
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("desc");

  const monitoringService = useMemo(() => {
    return id ? new TurIntegrationMonitoringService(id) : null;
  }, [id]);

  const { pushItem, popItem } = useBreadcrumb();
  const [refreshInterval, setRefreshInterval] = useState(5000);
  const breadcrumbAdded = useRef(false);

  const handleTabChange = useCallback((newSource: string) => {
    setPage(0);
    navigate(`${ROUTES.INTEGRATION_INSTANCE}/${id}/monitoring/${newSource}`);
  }, [navigate, id]);

  const buildRequest = useCallback((): TurIntegrationMonitoringRequest => {
    return {
      page,
      size: pageSize,
      sortBy,
      sortDirection,
      source: source === DEFAULT_SOURCE ? undefined : source,
      objectId: objectIdFilter || undefined,
      statuses: statusFilter ? [statusFilter] : undefined,
      environment: environmentFilter || undefined,
      locale: localeFilter || undefined,
      site: siteFilter || undefined,
      dateFrom: dateFrom ? new Date(dateFrom).toISOString() : undefined,
      dateTo: dateTo ? new Date(dateTo + "T23:59:59").toISOString() : undefined,
    };
  }, [page, pageSize, sortBy, sortDirection, source, objectIdFilter, statusFilter, environmentFilter, localeFilter, siteFilter, dateFrom, dateTo]);

  const clearFilters = useCallback(() => {
    setObjectIdFilter("");
    setStatusFilter("");
    setEnvironmentFilter("");
    setLocaleFilter("");
    setSiteFilter("");
    setDateFrom("");
    setDateTo("");
    setPage(0);
  }, []);

  const hasActiveFilters = objectIdFilter || statusFilter || environmentFilter || localeFilter || siteFilter || dateFrom || dateTo;

  useEffect(() => {
    let isMounted = true;

    const fetchData = async (isSilent = false) => {
      if (!monitoringService || !id) return;

      try {
        const request = buildRequest();
        const data = await monitoringService.search(request);

        if (!isMounted) return;

        setMonitoringPage(data);

        if (!isSilent && !breadcrumbAdded.current) {
          pushItem({ label: "Monitoring", href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/monitoring` });
          pushItem({ label: source === DEFAULT_SOURCE ? "All" : source });
          breadcrumbAdded.current = true;
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
    };
  }, [source, id, refreshInterval, buildRequest]);

  useEffect(() => {
    return () => {
      if (breadcrumbAdded.current) {
        popItem();
        popItem();
        breadcrumbAdded.current = false;
      }
    };
  }, []);

  if (!id) {
    return <div>Invalid integration instance ID</div>;
  }

  return (
    <LoadProvider checkIsNotUndefined={monitoringPage} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/monitoring`}>
      <SubPageHeader
        icon={IconGraph}
        name="Monitoring"
        feature="Monitoring"
        description="Verify the current status of AEM content indexing."
      />
      {monitoringPage && (
        <>
          <div className="flex items-center justify-between px-6 py-2 border-b">
            <Tabs
              value={source}
              onValueChange={handleTabChange}
              className="w-auto"
            >
              <TabsList className="bg-transparent border-none">
                <TabsTrigger value={DEFAULT_SOURCE}>All</TabsTrigger>
                {monitoringPage.sources?.map((tab) => (
                  <TabsTrigger key={tab} value={tab}>
                    {tab}
                  </TabsTrigger>
                ))}
              </TabsList>
            </Tabs>
            <div className="flex items-center gap-2">
              <GradientButton
                variant="outline"
                size="sm"
                className="h-8 gap-1.5"
                onClick={() => setShowFilters(!showFilters)}
              >
                {showFilters ? <IconFilterOff className="h-3.5 w-3.5" /> : <IconFilter className="h-3.5 w-3.5" />}
                <span className="text-xs">Filters</span>
                {hasActiveFilters && (
                  <span className="ml-1 h-2 w-2 rounded-full bg-blue-500" />
                )}
              </GradientButton>
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

          {showFilters && (
            <div className="px-6 py-4 border-b bg-muted/30">
              <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-3">
                <div className="space-y-1">
                  <Label className="text-xs font-medium">Date From</Label>
                  <Input
                    type="date"
                    value={dateFrom}
                    onChange={(e) => { setDateFrom(e.target.value); setPage(0); }}
                    className="h-8 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs font-medium">Date To</Label>
                  <Input
                    type="date"
                    value={dateTo}
                    onChange={(e) => { setDateTo(e.target.value); setPage(0); }}
                    className="h-8 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs font-medium">Object ID</Label>
                  <Input
                    type="text"
                    placeholder="Search..."
                    value={objectIdFilter}
                    onChange={(e) => { setObjectIdFilter(e.target.value); setPage(0); }}
                    className="h-8 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs font-medium">Status</Label>
                  <Select
                    value={statusFilter || NONE_VALUE}
                    onValueChange={(v) => { setStatusFilter(v === NONE_VALUE ? "" : v); setPage(0); }}
                  >
                    <SelectTrigger className="h-8 text-xs">
                      <SelectValue placeholder="All" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE} className="text-xs">All</SelectItem>
                      {STATUS_OPTIONS.map((s) => (
                        <SelectItem key={s} value={s} className="text-xs">{s.replaceAll("_", " ")}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label className="text-xs font-medium">Environment</Label>
                  <Select
                    value={environmentFilter || NONE_VALUE}
                    onValueChange={(v) => { setEnvironmentFilter(v === NONE_VALUE ? "" : v); setPage(0); }}
                  >
                    <SelectTrigger className="h-8 text-xs">
                      <SelectValue placeholder="All" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE} className="text-xs">All</SelectItem>
                      {monitoringPage.environments?.map((env) => (
                        <SelectItem key={env} value={env} className="text-xs">{env}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label className="text-xs font-medium">Language</Label>
                  <Select
                    value={localeFilter || NONE_VALUE}
                    onValueChange={(v) => { setLocaleFilter(v === NONE_VALUE ? "" : v); setPage(0); }}
                  >
                    <SelectTrigger className="h-8 text-xs">
                      <SelectValue placeholder="All" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE} className="text-xs">All</SelectItem>
                      {monitoringPage.locales?.map((loc) => (
                        <SelectItem key={loc} value={loc} className="text-xs">{loc}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label className="text-xs font-medium">Sites</Label>
                  <Select
                    value={siteFilter || NONE_VALUE}
                    onValueChange={(v) => { setSiteFilter(v === NONE_VALUE ? "" : v); setPage(0); }}
                  >
                    <SelectTrigger className="h-8 text-xs">
                      <SelectValue placeholder="All" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE} className="text-xs">All</SelectItem>
                      {monitoringPage.sites?.map((site) => (
                        <SelectItem key={site} value={site} className="text-xs">{site}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
              {hasActiveFilters && (
                <div className="mt-3 flex justify-end">
                  <GradientButton
                    variant="outline"
                    size="sm"
                    className="h-7 text-xs gap-1"
                    onClick={clearFilters}
                  >
                    <IconFilterOff className="h-3 w-3" />
                    Clear Filters
                  </GradientButton>
                </div>
              )}
            </div>
          )}

          <div className="mt-4">
            <AemMonitoringGrid
              gridItemList={monitoringPage.content || []}
              refreshInterval={refreshInterval}
              page={page}
              pageSize={pageSize}
              totalElements={monitoringPage.totalElements}
              totalPages={monitoringPage.totalPages}
              sortBy={sortBy}
              sortDirection={sortDirection}
              onPageChange={setPage}
              onPageSizeChange={(size: number) => { setPageSize(size); setPage(0); }}
              onSortChange={(field: string, direction: "asc" | "desc") => { setSortBy(field); setSortDirection(direction); setPage(0); }}
            />
          </div>
        </>
      )}
    </LoadProvider>
  );
}
