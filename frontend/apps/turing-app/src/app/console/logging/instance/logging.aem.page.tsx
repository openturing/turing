import { ROUTES } from "@/app/routes.const";
import { LoggingGrid } from "@/components/logging/logging.grid";
import { LoggingPageLayout } from "@/components/logging/logging.page.layout";
import type { TurLoggingGeneral } from "@/models/logging/logging-general.model";
import { TurLoggingInstanceService } from "@/services/logging/logging.service";
import { useLoggingPage } from "@/hooks/use-logging-page";
import { useCallback, useEffect, useRef, useState } from "react";

const turLoggingInstanceService = new TurLoggingInstanceService();

export default function LoggingAemPage() {
  const [level, setLevel] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [search, setSearch] = useState("");

  const searchTimeoutRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  const debouncedSearch = useRef(search);

  const logging = useLoggingPage<TurLoggingGeneral>({
    fetchService: (params) => turLoggingInstanceService.aem(params),
    breadcrumbLabel: "AEM",
    errorMessage: "Connection error or timeout while fetching AEM logging.",
    buildParams: () => ({
      level: level || undefined,
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
      search: debouncedSearch.current || undefined,
    }),
    filterDeps: [level, dateFrom, dateTo],
  });

  // T1024 — destructured so the callback below can name a stable dependency.
  // `logging.resetPage` as a dep is a member of an object rebuilt every render,
  // which the rule cannot treat as stable even though the function is.
  const { resetPage } = logging;

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
  // T1024 — keyed on the search text alone, deliberately. This is a debounce:
  // it must restart when what the reader typed changes and at no other time.
  // `logging` is a fresh object every render, so listing it would rebuild the
  // timer on every state change the page makes — including the ones this very
  // effect causes. The members it calls are stable; the values it reads are not,
  // and re-running on those would fire a search nobody asked for.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  const handleLevelChange = useCallback((newLevel: string) => {
    setLevel(newLevel === "all" ? "" : newLevel);
    resetPage();
  }, [resetPage]);

  return (
    <LoggingPageLayout
      pageData={logging.pageData}
      error={logging.error}
      tryAgainUrl={`${ROUTES.BENTO_LOGGING}/aem`}
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
        onDateFromChange={(v) => { setDateFrom(v); resetPage(); }}
        onDateToChange={(v) => { setDateTo(v); resetPage(); }}
        onSearchChange={setSearch}
      />
    </LoggingPageLayout>
  );
}
