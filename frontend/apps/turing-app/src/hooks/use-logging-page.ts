import type { TurLoggingPage } from "@/models/logging/logging-page.model";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { useCallback, useEffect, useRef, useState } from "react";

export interface UseLoggingPageOptions<T> {
  fetchService: (params: Record<string, unknown>) => Promise<TurLoggingPage<T>>;
  breadcrumbLabel: string;
  errorMessage: string;
  buildParams: () => Record<string, unknown>;
  filterDeps: unknown[];
}

export function useLoggingPage<T>({
  fetchService,
  breadcrumbLabel,
  errorMessage,
  buildParams,
  filterDeps,
}: UseLoggingPageOptions<T>) {
  const [pageData, setPageData] = useState<TurLoggingPage<T>>();
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();

  const [page, setPage] = useState(-1);
  const [pageSize, setPageSize] = useState(100);
  const [refreshInterval, setRefreshInterval] = useState(0);
  const [initialLoad, setInitialLoad] = useState(true);
  const skipNextFetch = useRef(false);
  const filterKey = JSON.stringify(filterDeps);

  const fetchData = useCallback(async (fetchPage: number, sort: "asc" | "desc" = "asc", silent = false) => {
    try {
      const data = await fetchService({
        ...buildParams(),
        page: fetchPage,
        pageSize,
        sort,
      });
      if (sort === "desc") {
        data.content = [...data.content].reverse();
      }
      setPageData(data);
      if (!silent) setError(null);
      return data;
    } catch {
      if (!silent) setError(errorMessage);
      return null;
    }
    // T1016 — the filters travel as one key rather than spread into the list.
    // A dependency list has to be a literal of constant length, and a spread is
    // neither: the three callers pass three, four and three entries, so the
    // arity is a property of the call site rather than of this hook. Serialising
    // them keeps the same trigger (the callback is rebuilt when any filter
    // moves) with a shape React and the linter can both read.
  // T1024 — the three options are read, not depended on. They are rebuilt by the
  // caller every render, so listing them would give this callback a new identity
  // each pass; the effect below depends on it, so that is a fetch per render.
  // The filters travel through `filterKey`, which is what actually changes.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageSize, filterKey]);

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
        pushItem({ label: breadcrumbLabel });
        added = true;
      }
    };
    init();
    return () => {
      if (added) popItem();
    };
  // T1024 — mounted once, deliberately: this registers a breadcrumb and does the
  // first fetch. Listing what it reads would re-register on every render of the
  // page that owns it.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Fetch on filter/page changes (skip initial load)
  useEffect(() => {
    if (initialLoad || page < 0) return;
    if (skipNextFetch.current) {
      skipNextFetch.current = false;
      return;
    }
    fetchData(page);
  // T1024 — `initialLoad` is this effect's guard, not its trigger. Listing it
  // would run the effect the moment it flips false, which is the instant the
  // initial fetch is landing, so the page would be fetched twice on open.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, fetchData]);

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

  const resetPage = useCallback(() => setPage(0), []);

  return {
    pageData,
    error,
    page: Math.max(0, page),
    pageSize,
    refreshInterval,
    setRefreshInterval,
    setPage,
    handlePageSizeChange,
    resetPage,
    initialLoad,
    fetchData,
  };
}
