import { ROUTES } from "@/app/routes.const";
import { IndexingLoggingGrid } from "@/components/logging/indexing.logging.grid";
import { LoggingPageLayout } from "@/components/logging/logging.page.layout";
import type { TurLoggingIndexing } from "@/models/logging/logging-indexing.model";
import { TurLoggingInstanceService } from "@/services/logging/logging.service";
import { useLoggingPage } from "@/hooks/use-logging-page";
import { useCallback, useEffect, useRef, useState } from "react";

const turLoggingInstanceService = new TurLoggingInstanceService();

export default function LoggingIndexingPage() {
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [status, setStatus] = useState("");
  const [contentId, setContentId] = useState("");
  const [resultStatus, setResultStatus] = useState("");
  const [url, setUrl] = useState("");

  const contentIdTimeoutRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  const urlTimeoutRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  const debouncedContentId = useRef(contentId);
  const debouncedUrl = useRef(url);

  const logging = useLoggingPage<TurLoggingIndexing>({
    fetchService: (params) => turLoggingInstanceService.indexing(params),
    breadcrumbLabel: "Indexing",
    errorMessage: "Connection error or timeout while fetching indexing logging.",
    buildParams: () => ({
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
      status: status || undefined,
      contentId: debouncedContentId.current || undefined,
      resultStatus: resultStatus || undefined,
      url: debouncedUrl.current || undefined,
    }),
    filterDeps: [dateFrom, dateTo, status, resultStatus],
  });

  // Debounced contentId search
  useEffect(() => {
    if (logging.initialLoad) return;
    if (contentIdTimeoutRef.current) clearTimeout(contentIdTimeoutRef.current);
    contentIdTimeoutRef.current = setTimeout(() => {
      debouncedContentId.current = contentId;
      logging.setPage(0);
      logging.fetchData(0);
    }, 500);
    return () => {
      if (contentIdTimeoutRef.current) clearTimeout(contentIdTimeoutRef.current);
    };
  }, [contentId]);

  // Debounced URL search
  useEffect(() => {
    if (logging.initialLoad) return;
    if (urlTimeoutRef.current) clearTimeout(urlTimeoutRef.current);
    urlTimeoutRef.current = setTimeout(() => {
      debouncedUrl.current = url;
      logging.setPage(0);
      logging.fetchData(0);
    }, 500);
    return () => {
      if (urlTimeoutRef.current) clearTimeout(urlTimeoutRef.current);
    };
  }, [url]);

  const handleStatusChange = useCallback((newStatus: string) => {
    setStatus(newStatus === "all" ? "" : newStatus);
    logging.resetPage();
  }, [logging.resetPage]);

  const handleResultStatusChange = useCallback((newResultStatus: string) => {
    setResultStatus(newResultStatus === "all" ? "" : newResultStatus);
    logging.resetPage();
  }, [logging.resetPage]);

  return (
    <LoggingPageLayout
      pageData={logging.pageData}
      error={logging.error}
      tryAgainUrl={`${ROUTES.LOGGING_INSTANCE}/indexing`}
      refreshInterval={logging.refreshInterval}
      onRefreshIntervalChange={logging.setRefreshInterval}
    >
      <IndexingLoggingGrid
        gridItemList={logging.pageData?.content || []}
        page={logging.page}
        totalPages={logging.pageData?.totalPages || 0}
        totalElements={logging.pageData?.totalElements || 0}
        pageSize={logging.pageSize}
        dateFrom={dateFrom}
        dateTo={dateTo}
        status={status}
        contentId={contentId}
        resultStatus={resultStatus}
        url={url}
        onPageChange={logging.setPage}
        onPageSizeChange={logging.handlePageSizeChange}
        onDateFromChange={(v) => { setDateFrom(v); logging.resetPage(); }}
        onDateToChange={(v) => { setDateTo(v); logging.resetPage(); }}
        onStatusChange={handleStatusChange}
        onContentIdChange={setContentId}
        onResultStatusChange={handleResultStatusChange}
        onUrlChange={setUrl}
      />
    </LoggingPageLayout>
  );
}
