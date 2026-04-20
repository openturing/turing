import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { TuringContext, SearchStoreContext, type SearchStoreValue } from "./context";
import { fetchChat, fetchSearch, parseHrefToParams, type SearchParams } from "./api";
import type {
  TuringConfig,
  ResolvedDocument,
  ResolvedGroup,
  SearchStatus,
  TurChatResponse,
  TurSearchResponse,
} from "./types";
import { resolveDocuments, resolveGroups } from "./resolve";

// ── URL helpers ──

function urlToSearchParams(
  url: URLSearchParams,
  defaults: { locale?: string; sort?: string },
): SearchParams {
  return {
    q: url.get("q") || "*",
    p: url.get("p") || "1",
    _setlocale: url.get("_setlocale") || defaults.locale,
    sort: url.get("sort") || defaults.sort || "relevance",
    fq: [...url.getAll("fq"), ...url.getAll("fq[]")].filter(Boolean) || undefined,
    tr: [...url.getAll("tr"), ...url.getAll("tr[]")].filter(Boolean) || undefined,
    nfpr: url.get("nfpr") || undefined,
    group: url.get("group") || undefined,
    rows: url.get("rows") || undefined,
  };
}

function searchParamsToUrl(params: SearchParams): string {
  const url = new URLSearchParams();
  if (params.q && params.q !== "*") url.set("q", params.q);
  if (params.p && params.p !== "1") url.set("p", params.p);
  if (params._setlocale) url.set("_setlocale", params._setlocale);
  if (params.sort && params.sort !== "relevance") url.set("sort", params.sort);
  for (const v of params.fq ?? []) url.append("fq[]", v);
  for (const v of params.tr ?? []) url.append("tr[]", v);
  if (params.nfpr) url.set("nfpr", params.nfpr);
  if (params.group) url.set("group", params.group);
  if (params.rows) url.set("rows", params.rows);
  return url.toString().replaceAll("%5B%5D", "[]");
}

/**
 * Merges explicit (URL) params with all registered implicit params.
 * Explicit values always win — implicit only fills gaps.
 */
function mergeParams(
  explicit: SearchParams,
  implicitMap: Map<string, Partial<SearchParams>>,
): SearchParams {
  if (implicitMap.size === 0) return explicit;

  // Start with explicit, apply implicit only for missing keys
  const merged: SearchParams = { ...explicit };

  const mergedAny = merged as unknown as Record<string, unknown>;
  for (const imp of implicitMap.values()) {
    for (const [k, v] of Object.entries(imp)) {
      if (v !== undefined && mergedAny[k] === undefined) {
        mergedAny[k] = v;
      }
    }
  }

  return merged;
}

// ── Props ──

interface UrlSyncOptions {
  /** Current URL search params (e.g. from useSearchParams()) */
  searchParams: URLSearchParams;
  /** Callback to update the URL (e.g. setSearchParams) */
  setSearchParams: (params: string) => void;
}

interface TuringProviderProps {
  readonly config: TuringConfig;
  /**
   * Enable automatic URL synchronization.
   * When provided, the URL becomes the single source of truth for search state.
   * Any param change updates the URL, and any URL change triggers a search.
   */
  readonly urlSync?: UrlSyncOptions;
  readonly children: ReactNode;
}

/**
 * TuringProvider makes the Turing configuration and search state available
 * to all hooks. When `urlSync` is provided, it automatically manages
 * URL synchronization and search execution.
 *
 * **Implicit params**: Hooks can register params (via `setImplicitParams`)
 * that are merged into every API request without polluting the URL.
 * For example, `useTuringTabs` registers `group` and `rows` for the
 * active tab, keeping the URL clean.
 *
 * @example
 * ```tsx
 * // With URL sync (recommended)
 * function App() {
 *   const [searchParams, setSearchParams] = useSearchParams();
 *   return (
 *     <TuringProvider
 *       config={{ site: "my-site", locale: "pt_BR" }}
 *       urlSync={{ searchParams, setSearchParams }}
 *     >
 *       <SearchPage />
 *     </TuringProvider>
 *   );
 * }
 * ```
 *
 * @since 2026.3.0
 */
export function TuringProvider({ config, urlSync, children }: TuringProviderProps) {
  const ctxValue = useMemo(() => ({ config }), [config]);

  if (urlSync) {
    return (
      <TuringContext.Provider value={ctxValue}>
        <UrlSyncSearchStore config={config} urlSync={urlSync}>
          {children}
        </UrlSyncSearchStore>
      </TuringContext.Provider>
    );
  }

  return (
    <TuringContext.Provider value={ctxValue}>
      {children}
    </TuringContext.Provider>
  );
}

// ── Internal: URL-synced search store ──

function UrlSyncSearchStore({
  config,
  urlSync,
  children,
}: {
  readonly config: TuringConfig;
  readonly urlSync: UrlSyncOptions;
  readonly children: ReactNode;
}) {
  const { searchParams, setSearchParams } = urlSync;
  const abortRef = useRef(0);

  const [status, setStatus] = useState<SearchStatus>("idle");
  const [data, setData] = useState<TurSearchResponse | null>(null);
  const [chatData, setChatData] = useState<TurChatResponse | null>(null);
  const [documents, setDocuments] = useState<ResolvedDocument[]>([]);
  const [groups, setGroups] = useState<ResolvedGroup[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [inputValue, setInputValue] = useState(searchParams.get("q") || "");

  // ── Implicit params registry ──
  const [implicitMap, setImplicitMap] = useState<Map<string, Partial<SearchParams>>>(
    () => new Map(),
  );

  const setImplicitParams = useCallback(
    (key: string, params: Partial<SearchParams> | null) => {
      setImplicitMap((prev) => {
        const next = new Map(prev);
        if (params === null) {
          next.delete(key);
        } else {
          next.set(key, params);
        }
        return next;
      });
    },
    [],
  );

  // Explicit params from URL
  const explicitParams = urlToSearchParams(searchParams, {
    locale: config.locale,
    sort: config.sort,
  });

  // Effective params = explicit + implicit (explicit wins)
  const params = useMemo(
    () => mergeParams(explicitParams, implicitMap),
    [explicitParams, implicitMap],
  );

  // Serialize for effect dependency (stable string comparison)
  const paramsKey = useMemo(() => JSON.stringify(params), [params]);

  // Execute search whenever effective params change
  useEffect(() => {
    const requestId = ++abortRef.current;
    setStatus("loading");
    setError(null);

    const q = params.q || "*";
    const shouldChat = q !== "*";

    Promise.all([
      fetchSearch(config.site, params),
      shouldChat
        ? fetchChat(config.site, { q, _setlocale: params._setlocale })
        : Promise.resolve(null),
    ])
      .then(([searchResult, chatResult]) => {
        if (requestId !== abortRef.current) return;
        setData(searchResult);
        setChatData(chatResult);
        setDocuments(resolveDocuments(searchResult));
        setGroups(resolveGroups(searchResult));
        setStatus("success");
      })
      .catch((err) => {
        if (requestId !== abortRef.current) return;
        setError(err instanceof Error ? err.message : "Search failed");
        setStatus("error");
      });
  }, [paramsKey]);

  // ── URL mutation helpers (operate on explicit params only) ──

  const setUrl = useCallback(
    (next: SearchParams) => setSearchParams(searchParamsToUrl(next)),
    [setSearchParams],
  );

  const updateParams = useCallback(
    (partial: Partial<SearchParams>) => {
      const next = { ...explicitParams };
      for (const [key, value] of Object.entries(partial)) {
        if (value === undefined) {
          delete (next as Record<string, unknown>)[key];
        } else {
          (next as Record<string, unknown>)[key] = value;
        }
      }
      setUrl(next);
    },
    [explicitParams, setUrl],
  );

  const navigate = useCallback(
    (href: string) => setUrl(parseHrefToParams(href)),
    [setUrl],
  );

  const submitSearch = useCallback(() => {
    updateParams({ q: inputValue || "*", p: "1" });
  }, [inputValue, updateParams]);

  const showAll = useCallback(() => {
    setInputValue("*");
    updateParams({ q: "*", p: "1" });
  }, [updateParams]);

  const setLocale = useCallback(
    (locale: string) => updateParams({ _setlocale: locale }),
    [updateParams],
  );

  const setSort = useCallback(
    (sort: string) => updateParams({ sort }),
    [updateParams],
  );

  const store: SearchStoreValue = useMemo(() => ({
    status,
    data,
    chat: chatData,
    documents,
    groups,
    error,
    params,
    inputValue,
    setInputValue,
    submitSearch,
    navigate,
    setLocale,
    setSort,
    showAll,
    updateParams,
    setImplicitParams,
  }), [
    status, data, chatData, documents, groups, error, params,
    inputValue, submitSearch, navigate, setLocale, setSort, showAll,
    updateParams, setImplicitParams,
  ]);

  return (
    <SearchStoreContext.Provider value={store}>
      {children}
    </SearchStoreContext.Provider>
  );
}
