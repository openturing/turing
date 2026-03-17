import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { LoggingGrid } from "@/components/logging/logging.grid";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurLoggingGeneral } from "@/models/logging/logging-general.model";
import type { TurLoggingPage } from "@/models/logging/logging-page.model";
import { TurLoggingInstanceService } from "@/services/logging/logging.service";
import { useCallback, useEffect, useRef, useState } from "react";

const turLoggingInstanceService = new TurLoggingInstanceService();

const REFRESH_OPTIONS = [
  { label: "Off", value: 0 },
  { label: "1s", value: 1000 },
  { label: "5s", value: 5000 },
  { label: "10s", value: 10000 },
  { label: "30s", value: 30000 },
  { label: "1m", value: 60000 },
  { label: "5m", value: 300000 },
];

export default function LoggingAemPage() {
  const [pageData, setPageData] = useState<TurLoggingPage<TurLoggingGeneral>>();
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();

  const [page, setPage] = useState(-1);
  const [pageSize, setPageSize] = useState(100);
  const [level, setLevel] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [search, setSearch] = useState("");
  const [refreshInterval, setRefreshInterval] = useState(0);
  const [initialLoad, setInitialLoad] = useState(true);

  const searchTimeoutRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  const debouncedSearch = useRef(search);

  const fetchData = useCallback(async (fetchPage: number, sort: "asc" | "desc" = "asc", silent = false) => {
    try {
      const data = await turLoggingInstanceService.aem({
        page: fetchPage,
        pageSize,
        level: level || undefined,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        search: debouncedSearch.current || undefined,
        sort,
      });
      if (sort === "desc") {
        data.content = [...data.content].reverse();
      }
      setPageData(data);
      if (!silent) setError(null);
      return data;
    } catch {
      if (!silent) setError("Connection error or timeout while fetching AEM logging.");
      return null;
    }
  }, [pageSize, level, dateFrom, dateTo]);

  // Initial load: fetch page 0 desc (newest N rows), single request
  useEffect(() => {
    let added = false;
    const init = async () => {
      const data = await fetchData(0, "desc");
      if (data) {
        const lastPage = Math.max(0, data.totalPages - 1);
        setPage(lastPage);
        setInitialLoad(false);
        pushItem({ label: "AEM" });
        added = true;
      }
    };
    init();
    return () => {
      if (added) popItem();
    };
  }, []);

  // Fetch on filter/page changes (skip initial load)
  useEffect(() => {
    if (initialLoad || page < 0) return;
    fetchData(page);
  }, [page, pageSize, level, dateFrom, dateTo, fetchData]);

  // Debounced text search
  useEffect(() => {
    if (initialLoad) return;
    if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    searchTimeoutRef.current = setTimeout(() => {
      debouncedSearch.current = search;
      setPage(0);
      fetchData(0);
    }, 500);
    return () => {
      if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    };
  }, [search]);

  // Auto-refresh: always reload latest rows (page 0 desc)
  useEffect(() => {
    if (refreshInterval <= 0 || initialLoad) return;
    const interval = setInterval(async () => {
      const data = await fetchData(0, "desc", true);
      if (data) {
        setPage(Math.max(0, data.totalPages - 1));
      }
    }, refreshInterval);
    return () => clearInterval(interval);
  }, [refreshInterval, pageSize, fetchData, initialLoad]);

  const handlePageSizeChange = useCallback((newPageSize: number) => {
    setPageSize(newPageSize);
    setPage(0);
  }, []);

  const handleLevelChange = useCallback((newLevel: string) => {
    setLevel(newLevel === "all" ? "" : newLevel);
    setPage(0);
  }, []);

  return (
    <LoadProvider checkIsNotUndefined={pageData} error={error} tryAgainUrl={`${ROUTES.LOGGING_INSTANCE}/aem`}>
      <div className="flex items-center justify-end px-6 py-2 border-b">
        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground italic">Auto-refresh:</span>
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
      <div className="mt-4">
        <LoggingGrid
          gridItemList={pageData?.content || []}
          page={Math.max(0, page)}
          totalPages={pageData?.totalPages || 0}
          totalElements={pageData?.totalElements || 0}
          pageSize={pageSize}
          level={level}
          dateFrom={dateFrom}
          dateTo={dateTo}
          search={search}
          onPageChange={setPage}
          onPageSizeChange={handlePageSizeChange}
          onLevelChange={handleLevelChange}
          onDateFromChange={(v) => { setDateFrom(v); setPage(0); }}
          onDateToChange={(v) => { setDateTo(v); setPage(0); }}
          onSearchChange={setSearch}
        />
      </div>
    </LoadProvider>
  )
}
