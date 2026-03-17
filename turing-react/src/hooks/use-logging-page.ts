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
  }, [pageSize, ...filterDeps]);

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
  }, []);

  // Fetch on filter/page changes (skip initial load)
  useEffect(() => {
    if (initialLoad || page < 0) return;
    if (skipNextFetch.current) {
      skipNextFetch.current = false;
      return;
    }
    fetchData(page);
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
