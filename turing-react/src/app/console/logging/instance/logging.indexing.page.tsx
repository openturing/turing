import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { IndexingLoggingGrid } from "@/components/logging/indexing.logging.grid";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurLoggingIndexing } from "@/models/logging/logging-indexing.model";
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

export default function LoggingIndexingPage() {
  const [pageData, setPageData] = useState<TurLoggingPage<TurLoggingIndexing>>();
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();

  const [page, setPage] = useState(-1);
  const [pageSize, setPageSize] = useState(100);
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [status, setStatus] = useState("");
  const [contentId, setContentId] = useState("");
  const [resultStatus, setResultStatus] = useState("");
  const [url, setUrl] = useState("");
  const [refreshInterval, setRefreshInterval] = useState(0);
  const [initialLoad, setInitialLoad] = useState(true);
  const skipNextFetch = useRef(false);

  const contentIdTimeoutRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  const urlTimeoutRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  const debouncedContentId = useRef(contentId);
  const debouncedUrl = useRef(url);

  const fetchData = useCallback(async (fetchPage: number, sort: "asc" | "desc" = "asc", silent = false) => {
    try {
      const data = await turLoggingInstanceService.indexing({
        page: fetchPage,
        pageSize,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        status: status || undefined,
        contentId: debouncedContentId.current || undefined,
        resultStatus: resultStatus || undefined,
        url: debouncedUrl.current || undefined,
        sort,
      });
      if (sort === "desc") {
        data.content = [...data.content].reverse();
      }
      setPageData(data);
      if (!silent) setError(null);
      return data;
    } catch {
      if (!silent) setError("Connection error or timeout while fetching indexing logging.");
      return null;
    }
  }, [pageSize, dateFrom, dateTo, status, resultStatus]);

  // Initial load: fetch page 0 desc (newest N rows), single request
  useEffect(() => {
    let added = false;
    const init = async () => {
      const data = await fetchData(0, "desc");
      if (data) {
        const lastPage = Math.max(0, data.totalPages - 1);
        skipNextFetch.current = true;
        setPage(lastPage);
        setInitialLoad(false);
        pushItem({ label: "Indexing" });
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
    if (skipNextFetch.current) {
      skipNextFetch.current = false;
      return;
    }
    fetchData(page);
  }, [page, pageSize, dateFrom, dateTo, status, resultStatus, fetchData]);

  // Debounced contentId search
  useEffect(() => {
    if (initialLoad) return;
    if (contentIdTimeoutRef.current) clearTimeout(contentIdTimeoutRef.current);
    contentIdTimeoutRef.current = setTimeout(() => {
      debouncedContentId.current = contentId;
      setPage(0);
      fetchData(0);
    }, 500);
    return () => {
      if (contentIdTimeoutRef.current) clearTimeout(contentIdTimeoutRef.current);
    };
  }, [contentId]);

  // Debounced URL search
  useEffect(() => {
    if (initialLoad) return;
    if (urlTimeoutRef.current) clearTimeout(urlTimeoutRef.current);
    urlTimeoutRef.current = setTimeout(() => {
      debouncedUrl.current = url;
      setPage(0);
      fetchData(0);
    }, 500);
    return () => {
      if (urlTimeoutRef.current) clearTimeout(urlTimeoutRef.current);
    };
  }, [url]);

  // Auto-refresh: always reload latest rows (page 0 desc)
  useEffect(() => {
    if (refreshInterval <= 0 || initialLoad) return;
    const interval = setInterval(async () => {
      const data = await fetchData(0, "desc", true);
      if (data) {
        const lastPage = Math.max(0, data.totalPages - 1);
        if (lastPage !== page) {
          skipNextFetch.current = true;
          setPage(lastPage);
        }
      }
    }, refreshInterval);
    return () => clearInterval(interval);
  }, [refreshInterval, pageSize, fetchData, initialLoad, page]);

  const handlePageSizeChange = useCallback((newPageSize: number) => {
    setPageSize(newPageSize);
    setPage(0);
  }, []);

  const handleStatusChange = useCallback((newStatus: string) => {
    setStatus(newStatus === "all" ? "" : newStatus);
    setPage(0);
  }, []);

  const handleResultStatusChange = useCallback((newResultStatus: string) => {
    setResultStatus(newResultStatus === "all" ? "" : newResultStatus);
    setPage(0);
  }, []);

  return (
    <LoadProvider checkIsNotUndefined={pageData} error={error} tryAgainUrl={`${ROUTES.LOGGING_INSTANCE}/indexing`}>
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
        <IndexingLoggingGrid
          gridItemList={pageData?.content || []}
          page={Math.max(0, page)}
          totalPages={pageData?.totalPages || 0}
          totalElements={pageData?.totalElements || 0}
          pageSize={pageSize}
          dateFrom={dateFrom}
          dateTo={dateTo}
          status={status}
          contentId={contentId}
          resultStatus={resultStatus}
          url={url}
          onPageChange={setPage}
          onPageSizeChange={handlePageSizeChange}
          onDateFromChange={(v) => { setDateFrom(v); setPage(0); }}
          onDateToChange={(v) => { setDateTo(v); setPage(0); }}
          onStatusChange={handleStatusChange}
          onContentIdChange={setContentId}
          onResultStatusChange={handleResultStatusChange}
          onUrlChange={setUrl}
        />
      </div>
    </LoadProvider>
  )
}
