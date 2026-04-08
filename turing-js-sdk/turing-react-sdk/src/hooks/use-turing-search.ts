import { useCallback, useRef, useState } from "react";
import { fetchChat, fetchSearch, parseHrefToParams, type SearchParams } from "../core/api";
import { useTuringContext } from "../core/use-turing-context";
import { resolveDocuments, resolveGroups } from "../core/resolve";
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
): UseTuringSearchReturn {
  const { config } = useTuringContext();
  const abortRef = useRef(0);

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

        setData(searchResult);
        setChatData(chatResult);
        setDocuments(resolveDocuments(searchResult));
        setGroups(resolveGroups(searchResult));
        setStatus("success");
      } catch (err) {
        if (requestId !== abortRef.current) return;
        setError(err instanceof Error ? err.message : "Search failed");
        setStatus("error");
      }
    },
    [config.site],
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
  };
}
