import { useCallback, useEffect, useRef, useState } from "react";
import {
  applyFacetAction,
  fetchChat,
  fetchSearch,
  parseHrefToParams,
  postClick,
  postStructuredSearch,
  requestToSearchParams,
  searchParamsToRequest,
  type SearchParams,
} from "../core/api";
import { useTuringContext } from "../core/use-turing-context";
import { resolveDocuments, resolveGroups, resolveStructuredGroups } from "../core/resolve";
import { TURING_ANALYTICS_EVENTS, type TuringAnalytics } from "../core/analytics";
import { getOrCreateTurSession } from "../core/session";
import type {
  ResolvedDocument,
  ResolvedGroup,
  SearchStatus,
  TurChatResponse,
  TurSearchRequest,
  TurSearchResponse,
  TurStructuredFacet,
  TurStructuredFacetItem,
  TurStructuredSearchResponse,
} from "../core/types";

export interface UseTuringSearchReturn {
  /** Current status */
  status: SearchStatus;
  /**
   * The legacy link-bearing response. Populated only in GET mode
   * (`postNative: false`); `null` POST-native, where {@link structured} is
   * authoritative.
   */
  data: TurSearchResponse | null;
  /**
   * T710 — the link-free response, populated when the hook runs POST-native
   * (the default). Facets, pagination, locales and spell-check are data here,
   * never hrefs.
   */
  structured: TurStructuredSearchResponse | null;
  /**
   * T710 — the request that will be sent next. POST-native this is the single
   * source of truth: every toggle below is a change to this object.
   */
  request: TurSearchRequest;
  /** T710 — run a search from an explicit structured request. */
  searchRequest: (request: TurSearchRequest) => Promise<void>;
  /**
   * T710 — toggle a facet value from the structured response. Applies the
   * item's own `action` (ADD / REMOVE / REPLACE), so single-select AND fields
   * behave correctly, and resets to page 1.
   */
  toggleFacet: (item: TurStructuredFacetItem) => Promise<void>;
  /** T710 — clear every selection of one facet (replaces `cleanUpLink`). */
  clearFacet: (facet: TurStructuredFacet) => Promise<void>;
  /** T710 — clear every filter (replaces `cleanUpFacets`). */
  clearAllFilters: () => Promise<void>;
  /**
   * T710 — re-run using the spell-checker's suggestion, or force the original
   * term (`useOriginal: true`, the structured form of `nfpr=1`).
   */
  applySpellCheck: (useOriginal?: boolean) => Promise<void>;
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
  /**
   * T710 / §XLV — drive search over `POST /sn/{site}/search/query` with a JSON
   * body and the link-free `STRUCTURED` response, instead of `GET …/search`
   * with a query string. **Defaults to `true`.**
   *
   * <p>This is what makes a filter value containing `& = + # %` or a space
   * impossible to mis-parse: it never touches a query string. Facet,
   * pagination, locale and spell-check navigation all become changes to
   * {@link UseTuringSearchReturn.request}.
   *
   * <p>Set `false` for the legacy GET behaviour, where
   * {@link UseTuringSearchReturn.data} carries the link-bearing response and
   * `navigate(href)` is the way to move. The only reason to do so is a consumer
   * that still reads `data`; `documents` / `groups` / `status` / `error` /
   * `params` are identical in both modes.
   */
  readonly postNative?: boolean;
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
  const { analytics, postNative = true } = options;
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
  const [structured, setStructured] = useState<TurStructuredSearchResponse | null>(null);
  const [request, setRequest] = useState<TurSearchRequest>(() =>
    searchParamsToRequest(defaultParams),
  );
  // The request is read inside callbacks that must not re-create on every
  // response (they are dependencies of the derived facet/pagination hooks), so
  // it is mirrored in a ref — React queues state updates, it does not apply
  // them synchronously.
  const requestRef = useRef(request);
  requestRef.current = request;
  const structuredRef = useRef(structured);
  structuredRef.current = structured;

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

  /**
   * T710 — the POST-native path. Identical lifecycle to {@link executeSearch}
   * (stale-response guard, parallel chat call, analytics), but the query travels
   * as a JSON body and the response is the link-free shape.
   */
  const executeRequest = useCallback(
    async (searchRequest: TurSearchRequest) => {
      const requestId = ++abortRef.current;
      setStatus("loading");
      setError(null);
      setRequest(searchRequest);
      requestRef.current = searchRequest;
      setParams(requestToSearchParams(searchRequest));

      try {
        const q = searchRequest.q || "*";
        const shouldChat = q !== "*";

        const [searchResult, chatResult] = await Promise.all([
          postStructuredSearch(config.site, searchRequest),
          shouldChat
            ? fetchChat(config.site, { q, _setlocale: searchRequest.locale })
            : Promise.resolve(null),
        ]);

        if (requestId !== abortRef.current) return;

        const resolved = resolveDocuments(searchResult);
        setData(null);
        setStructured(searchResult);
        structuredRef.current = searchResult;
        setChatData(chatResult);
        setDocuments(resolved);
        setGroups(resolveStructuredGroups(searchResult));
        setStatus("success");
        // The server echoes the request with every default resolved — adopt it
        // so the next toggle starts from what actually ran, not what we guessed.
        const effective = searchResult.request ?? searchRequest;
        setRequest(effective);
        requestRef.current = effective;
        setParams(requestToSearchParams(effective));

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

  /** Re-runs the current request with `changes` applied, back on page 1. */
  const patchRequest = useCallback(
    (changes: Partial<TurSearchRequest>, resetPage = true) =>
      executeRequest({
        ...requestRef.current,
        ...changes,
        ...(resetPage ? { page: 1 } : {}),
      }),
    [executeRequest],
  );

  const search = useCallback(
    (p: SearchParams) => (postNative ? executeRequest(searchParamsToRequest(p)) : executeSearch(p)),
    [executeRequest, executeSearch, postNative],
  );

  const searchQuery = useCallback(
    (query: string) =>
      postNative
        ? patchRequest({ q: query })
        : executeSearch({ ...params, q: query, p: "1" }),
    [executeSearch, params, patchRequest, postNative],
  );

  const toggleFacet = useCallback(
    (item: TurStructuredFacetItem) =>
      patchRequest({ fq: applyFacetAction(requestRef.current.fq ?? [], item) }),
    [patchRequest],
  );

  const clearFacet = useCallback(
    (facet: TurStructuredFacet) => {
      const clear = new Set(facet.clearFilterQueries ?? facet.selectedFilterQueries ?? []);
      return patchRequest({
        fq: (requestRef.current.fq ?? []).filter((fq) => !clear.has(fq)),
      });
    },
    [patchRequest],
  );

  const clearAllFilters = useCallback(() => patchRequest({ fq: [] }), [patchRequest]);

  const applySpellCheck = useCallback(
    (useOriginal = false) => {
      const spellCheck = structuredRef.current?.spellCheck;
      const text = useOriginal ? spellCheck?.originalText : spellCheck?.correctedTextValue;
      if (!text) return Promise.resolve();
      return patchRequest({ q: text, autoCorrectionDisabled: useOriginal });
    },
    [patchRequest],
  );

  const navigate = useCallback(
    (href: string) => {
      const parsed = parseHrefToParams(href);
      return executeSearch(parsed);
    },
    [executeSearch],
  );

  const changeLocale = useCallback(
    (locale: string) =>
      postNative
        ? patchRequest({ locale })
        : executeSearch({ ...params, _setlocale: locale }),
    [executeSearch, params, patchRequest, postNative],
  );

  const changeSort = useCallback(
    (sort: string) =>
      postNative ? patchRequest({ sort }) : executeSearch({ ...params, sort }),
    [executeSearch, params, patchRequest, postNative],
  );

  const goToPage = useCallback(
    (page: number) =>
      postNative
        ? patchRequest({ page }, false)
        : executeSearch({ ...params, p: String(page) }),
    [executeSearch, params, patchRequest, postNative],
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
    structured,
    request,
    chat: chatData,
    documents,
    groups,
    error,
    params,
    search,
    searchRequest: executeRequest,
    searchQuery,
    navigate,
    toggleFacet,
    clearFacet,
    clearAllFilters,
    applySpellCheck,
    changeLocale,
    changeSort,
    goToPage,
    trackResultClick,
  };
}
