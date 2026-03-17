import { ROUTES } from "@/app/routes.const";
import { LoggingGrid } from "@/components/logging/logging.grid";
import { LoggingPageLayout } from "@/components/logging/logging.page.layout";
import type { TurLoggingGeneral } from "@/models/logging/logging-general.model";
import { TurLoggingInstanceService } from "@/services/logging/logging.service";
import { useLoggingPage } from "@/hooks/use-logging-page";
import { useCallback, useEffect, useRef, useState } from "react";

const turLoggingInstanceService = new TurLoggingInstanceService();

export default function LoggingServerPage() {
  const [level, setLevel] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [search, setSearch] = useState("");

  const searchTimeoutRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  const debouncedSearch = useRef(search);

  const logging = useLoggingPage<TurLoggingGeneral>({
    fetchService: (params) => turLoggingInstanceService.server(params),
    breadcrumbLabel: "Turing ES Server",
    errorMessage: "Connection error or timeout while fetching server logging.",
    buildParams: () => ({
      level: level || undefined,
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
      search: debouncedSearch.current || undefined,
    }),
    filterDeps: [level, dateFrom, dateTo],
  });

  // Debounced text search
  useEffect(() => {
    if (logging.initialLoad) return;
    if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    searchTimeoutRef.current = setTimeout(() => {
      debouncedSearch.current = search;
      logging.setPage(0);
      logging.fetchData(0);
    }, 500);
    return () => {
      if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    };
  }, [search]);

  const handleLevelChange = useCallback((newLevel: string) => {
    setLevel(newLevel === "all" ? "" : newLevel);
    logging.resetPage();
  }, [logging.resetPage]);

  return (
    <LoggingPageLayout
      pageData={logging.pageData}
      error={logging.error}
      tryAgainUrl={`${ROUTES.LOGGING_INSTANCE}/server`}
      refreshInterval={logging.refreshInterval}
      onRefreshIntervalChange={logging.setRefreshInterval}
    >
      <LoggingGrid
        gridItemList={logging.pageData?.content || []}
        page={logging.page}
        totalPages={logging.pageData?.totalPages || 0}
        totalElements={logging.pageData?.totalElements || 0}
        pageSize={logging.pageSize}
        level={level}
        dateFrom={dateFrom}
        dateTo={dateTo}
        search={search}
        onPageChange={logging.setPage}
        onPageSizeChange={logging.handlePageSizeChange}
        onLevelChange={handleLevelChange}
        onDateFromChange={(v) => { setDateFrom(v); logging.resetPage(); }}
        onDateToChange={(v) => { setDateTo(v); logging.resetPage(); }}
        onSearchChange={setSearch}
      />
    </LoggingPageLayout>
  );
}
