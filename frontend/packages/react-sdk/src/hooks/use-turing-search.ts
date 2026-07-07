import { useCallback, useEffect, useRef, useState } from "react";
import { fetchChat, fetchSearch, parseHrefToParams, postClick, type SearchParams } from "../core/api";
import { useTuringContext } from "../core/use-turing-context";
import { resolveDocuments, resolveGroups } from "../core/resolve";
import { TURING_ANALYTICS_EVENTS, type TuringAnalytics } from "../core/analytics";
import { getOrCreateTurSession } from "../core/session";
import type {
  ResolvedDocument,
  ResolvedGroup,
  SearchStatus,
  TurChatResponse,
  TurSearchResponse,
} from "../core/types";

export interface UseTuringSearchReturn {
  /** Current status */
  status: SearchStatus;
  /** Raw API response */
  data: TurSearchResponse | null;
  /** AI chat response (null for wildcard queries) */
  chat: TurChatResponse | null;
  /** Resolved documents with mapped default fields */
  documents: ResolvedDocument[];
  /** Resolved groups (when `group` param is used) */
  groups: ResolvedGroup[];
  /** Error message */
  error: string | null;
  /** Current search params (synced with URL) */
  params: SearchParams;
  /** Execute a search with given params */
  search: (params: SearchParams) => Promise<void>;
  /** Execute a new text query (resets page to 1) */
  searchQuery: (query: string) => Promise<void>;
  /** Navigate via a Turing href link (facet, pagination, spell check) */
  navigate: (href: string) => Promise<void>;
  /** Change locale */
  changeLocale: (locale: string) => Promise<void>;
  /** Change sort */
  changeSort: (sort: string) => Promise<void>;
  /** Go to specific page */
  goToPage: (page: number) => Promise<void>;
  /**
   * T462/T463 — record a result click: mirrors `postClick` (server CTR) **and**
   * emits `turing_search_result_click` when an `analytics` bus is supplied.
   */
  trackResultClick: (documentId: string, position: number, term?: string) => void;
}

export interface UseTuringSearchOptions {
  /**
   * T463 (Block Z) — canonical analytics bus (from {@link useTuringAnalytics}).
   * When set, the hook emits `turing_search` / `turing_search_no_results` /
   * `turing_search_refined` and stamps the `TUR_SESSION` id for cross-surface
   * stitching with chat. No-op when absent.
   */
  readonly analytics?: TuringAnalytics;
}

/**
 * Core search hook for manual (non-URL-synced) mode.
 * Manages the full search lifecycle: query, results,
 * pagination, facet navigation, locale/sort changes, and AI chat.
 *
 * For URL-synced mode, use `<TuringProvider urlSync={...}>` and
 * the derived hooks (useTuringFacets, useTuringPagination, etc.) instead.
 *
 * @example
 * ```tsx
 * const { data, documents, search, navigate, status } = useTuringSearch();
 * ```
 *
 * @since 2026.2.0
 */
export function useTuringSearch(
  initialParams?: Partial<SearchParams>,
  options: UseTuringSearchOptions = {},
): UseTuringSearchReturn {
  const { config } = useTuringContext();
  const { analytics } = options;
  const abortRef = useRef(0);
  // Last real (non-wildcard) query, for refinement detection.
  const lastQueryRef = useRef<string | null>(null);

  // T463 — stamp the cross-surface session id so a later chat lead stitches
  // back to the originating search (both key off the `TUR_SESSION` cookie).
  useEffect(() => {
    if (analytics) {
      analytics.setContext({ site: config.site, sessionId: getOrCreateTurSession() ?? undefined });
    }
  }, [analytics, config.site]);

  const defaultParams: SearchParams = {
    q: "*",
    p: "1",
    _setlocale: config.locale,
    sort: config.sort ?? "relevance",
    ...initialParams,
  };

  const [status, setStatus] = useState<SearchStatus>("idle");
  const [data, setData] = useState<TurSearchResponse | null>(null);
  const [chatData, setChatData] = useState<TurChatResponse | null>(null);
  const [documents, setDocuments] = useState<ResolvedDocument[]>([]);
  const [groups, setGroups] = useState<ResolvedGroup[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [params, setParams] = useState<SearchParams>(defaultParams);

  const executeSearch = useCallback(
    async (searchParams: SearchParams) => {
      const requestId = ++abortRef.current;
      setStatus("loading");
      setError(null);
      setParams(searchParams);

      try {
        const q = searchParams.q || "*";
        const shouldChat = q !== "*";

        const [searchResult, chatResult] = await Promise.all([
          fetchSearch(config.site, searchParams),
          shouldChat
            ? fetchChat(config.site, { q, _setlocale: searchParams._setlocale })
            : Promise.resolve(null),
        ]);

        if (requestId !== abortRef.current) return;

        const resolved = resolveDocuments(searchResult);
        setData(searchResult);
        setChatData(chatResult);
        setDocuments(resolved);
        setGroups(resolveGroups(searchResult));
        setStatus("success");

        // T463 — search funnel events. `count` is the total hit count.
        if (analytics && q && q !== "*") {
          const resultCount = searchResult?.queryContext?.count ?? resolved.length;
          const prev = lastQueryRef.current;
          if (prev && prev !== q) {
            analytics.emit(TURING_ANALYTICS_EVENTS.searchRefined, { from: prev, to: q });
          }
          analytics.emit(TURING_ANALYTICS_EVENTS.search, { query: q, results: resultCount });
          if (resultCount === 0) {
            analytics.emit(TURING_ANALYTICS_EVENTS.searchNoResults, { query: q });
          }
          lastQueryRef.current = q;
        }
      } catch (err) {
        if (requestId !== abortRef.current) return;
        setError(err instanceof Error ? err.message : "Search failed");
        setStatus("error");
      }
    },
    [config.site, analytics],
  );

  const search = useCallback(
    (p: SearchParams) => executeSearch(p),
    [executeSearch],
  );

  const searchQuery = useCallback(
    (query: string) => executeSearch({ ...params, q: query, p: "1" }),
    [executeSearch, params],
  );

  const navigate = useCallback(
    (href: string) => {
      const parsed = parseHrefToParams(href);
      return executeSearch(parsed);
    },
    [executeSearch],
  );

  const changeLocale = useCallback(
    (locale: string) => executeSearch({ ...params, _setlocale: locale }),
    [executeSearch, params],
  );

  const changeSort = useCallback(
    (sort: string) => executeSearch({ ...params, sort }),
    [executeSearch, params],
  );

  const goToPage = useCallback(
    (page: number) => executeSearch({ ...params, p: String(page) }),
    [executeSearch, params],
  );

  const trackResultClick = useCallback(
    (documentId: string, position: number, term?: string) => {
      const query = term ?? params.q ?? "";
      void postClick(config.site, {
        term: query,
        documentId,
        position,
        locale: params._setlocale,
      });
      analytics?.emit(TURING_ANALYTICS_EVENTS.searchResultClick, {
        query,
        document_id: documentId,
        position,
      });
    },
    [config.site, params, analytics],
  );

  return {
    status,
    data,
    chat: chatData,
    documents,
    groups,
    error,
    params,
    search,
    searchQuery,
    navigate,
    changeLocale,
    changeSort,
    goToPage,
    trackResultClick,
  };
}
