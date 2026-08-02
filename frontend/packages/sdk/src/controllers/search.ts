import type { TuringClient } from "../client";
import {
  applyFacetAction,
  fetchChat,
  fetchSearch,
  parseHrefToParams,
  postClick,
  postStructuredSearch,
  type SearchParams,
} from "../api";
import { TURING_ANALYTICS_EVENTS, type TuringAnalytics } from "../analytics";
import { getOrCreateTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../session";
import { resolveDocuments, resolveGroups, resolveStructuredGroups } from "../resolve";
import { createStore, type Store } from "../store";
import type {
  ResolvedDocument,
  ResolvedGroup,
  SearchStatus,
  TuringConfig,
  TurChatResponse,
  TurSearchRequest,
  TurSearchResponse,
  TurStructuredFacet,
  TurStructuredFacetItem,
  TurStructuredSearchResponse,
} from "../types";

/**
 * Vanilla search controller — the framework-agnostic equivalent of the React
 * SDK's `useTuringSearch`. Manages the full search lifecycle (query,
 * pagination, facet navigation, locale/sort changes, AI chat) over an
 * observable {@link Store}.
 *
 * @example
 * ```js
 * const search = createSearchController(client, { site: "my-site", locale: "en_US" });
 * search.subscribe((state) => render(state.documents));
 * await search.searchQuery("hello");
 * ```
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

export interface SearchControllerState {
  status: SearchStatus;
  /**
   * The legacy link-bearing response. Populated only in GET mode
   * (`postNative: false`); `null` when the controller runs POST-native, where
   * {@link SearchControllerState.structured} is authoritative.
   */
  data: TurSearchResponse | null;
  /**
   * T709 — the link-free response. Populated when the controller runs
   * POST-native (the default). Facets, pagination, locales and spell-check all
   * live here as data, never as hrefs.
   */
  structured: TurStructuredSearchResponse | null;
  chat: TurChatResponse | null;
  documents: ResolvedDocument[];
  groups: ResolvedGroup[];
  error: string | null;
  /** Legacy GET params. Kept in sync in both modes for backwards compatibility. */
  params: SearchParams;
  /**
   * T709 — the request that will be sent next. In POST-native mode this is the
   * single source of truth: every toggle below is a change to this object.
   */
  request: TurSearchRequest;
}

export interface SearchController extends Store<SearchControllerState> {
  /** Execute a search with explicit params. */
  search(params: SearchParams): Promise<void>;
  /**
   * T709 — execute a search from an explicit structured request. The POST-native
   * counterpart of {@link SearchController.search}.
   */
  searchRequest(request: TurSearchRequest): Promise<void>;
  /** Execute a new text query (resets page to 1). */
  searchQuery(query: string): Promise<void>;
  /**
   * Navigate via a Turing href link (facet, pagination, spell-check).
   *
   * @deprecated T709 — this is the query-string round-trip the block exists to
   * remove. Use {@link SearchController.toggleFacet},
   * {@link SearchController.goToPage} or {@link SearchController.applySpellCheck}
   * instead; they carry no URL. Kept for GET-mode consumers and no-JS fallbacks.
   */
  navigate(href: string): Promise<void>;
  /**
   * T709 — toggle a facet value from the structured response. Applies the item's
   * own `action` (ADD / REMOVE / REPLACE), so single-select AND fields behave
   * correctly, and resets to page 1.
   */
  toggleFacet(item: TurStructuredFacetItem): Promise<void>;
  /** T709 — clear every selection of one facet (replaces `cleanUpLink`). */
  clearFacet(facet: TurStructuredFacet): Promise<void>;
  /** T709 — clear every filter (replaces `cleanUpFacets`). */
  clearAllFilters(): Promise<void>;
  /**
   * T709 — re-run using the spell-checker's suggestion, or force the original
   * term (`useOriginal: true`, the structured form of `nfpr=1`).
   */
  applySpellCheck(useOriginal?: boolean): Promise<void>;
  /** Change locale and re-run the current query. */
  changeLocale(locale: string): Promise<void>;
  /** Change sort and re-run the current query. */
  changeSort(sort: string): Promise<void>;
  /** Go to a specific page. */
  goToPage(page: number): Promise<void>;
  /**
   * T462 — record a result click. Mirrors `postClick` (server CTR) **and**
   * emits `turing_search_result_click` in one call, so a host wires click
   * tracking once and both the server analytics and GA4 funnel light up.
   */
  trackResultClick(documentId: string, position: number, term?: string): void;
}

export interface SearchControllerOptions {
  /**
   * T462 (Block Z) — canonical analytics bus. When set, the controller emits
   * `turing_search` / `turing_search_no_results` / `turing_search_refined` and
   * (via {@link SearchController.trackResultClick}) `turing_search_result_click`.
   * The bus context is stamped with the `TUR_SESSION` id so a later chat lead
   * stitches back to the originating search.
   */
  readonly analytics?: TuringAnalytics;
  /** Cookie used for cross-surface session stitching. Defaults to `TUR_SESSION`. */
  readonly sessionCookieName?: string;
  /**
   * T484 — whether a non-wildcard query should also fetch the AI chat answer
   * (`GET /sn/{site}/chat`) alongside the search results. Defaults to `true`
   * (the historic behaviour: search + chat in one round-trip). Set `false` for a
   * **search-only** surface — e.g. a faceted search box that must not incur an
   * LLM call (and its cost/rate-limit) on every query. When disabled,
   * {@link SearchControllerState.chat} stays `null`.
   */
  readonly chat?: boolean;
  /**
   * T709 / §XLV — drive search over `POST /sn/{site}/search/query` with a JSON
   * body and the link-free `STRUCTURED` response, instead of `GET …/search`
   * with a query string. **Defaults to `true`.**
   *
   * <p>This is what makes a filter value containing `& = + # %` or a space
   * impossible to mis-parse: it never touches a query string. Facet, pagination,
   * locale and spell-check navigation all become changes to
   * {@link SearchControllerState.request}.
   *
   * <p>Set `false` for the legacy GET behaviour, where
   * {@link SearchControllerState.data} carries the link-bearing response and
   * {@link SearchController.navigate} is the way to move. The only reason to do
   * so is a consumer that still reads `state.data` — nothing else differs, and
   * `documents` / `groups` / `status` / `error` are identical in both modes.
   */
  readonly postNative?: boolean;
}

/**
 * T709 — bridges the legacy GET params onto the POST body. Only used to seed
 * the first request from `initialParams` / `TuringConfig`; after that the
 * request is the source of truth and is never re-derived from a query string.
 *
 * @since 2026.3.4
 */
export function searchParamsToRequest(params: SearchParams): TurSearchRequest {
  return {
    q: params.q || "*",
    page: params.p ? Number(params.p) : 1,
    locale: params._setlocale,
    sort: params.sort,
    fq: params.fq ? [...params.fq] : [],
    targetingRules: params.tr ? [...params.tr] : [],
    rows: params.rows ? Number(params.rows) : undefined,
    group: params.group,
    autoCorrectionDisabled: params.nfpr ? params.nfpr !== "0" : undefined,
  };
}

/**
 * T709 — keeps the public `state.params` in step with the request, so a consumer
 * reading it (or building a shareable GET URL from it) still works POST-native.
 *
 * @since 2026.3.4
 */
export function requestToSearchParams(request: TurSearchRequest): SearchParams {
  return {
    q: request.q || "*",
    p: String(request.page ?? 1),
    _setlocale: request.locale,
    sort: request.sort,
    fq: request.fq?.length ? [...request.fq] : undefined,
    tr: request.targetingRules?.length ? [...request.targetingRules] : undefined,
    rows: request.rows === undefined ? undefined : String(request.rows),
    group: request.group,
    nfpr: nfprOf(request.autoCorrectionDisabled),
  };
}

function nfprOf(autoCorrectionDisabled: boolean | undefined): string | undefined {
  if (autoCorrectionDisabled === undefined) return undefined;
  return autoCorrectionDisabled ? "1" : "0";
}

export function createSearchController(
  client: TuringClient,
  config: TuringConfig,
  initialParams?: Partial<SearchParams>,
  options: SearchControllerOptions = {},
): SearchController {
  const {
    analytics,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
    chat: chatEnabled = true,
    postNative = true,
  } = options;
  const defaultParams: SearchParams = {
    q: "*",
    p: "1",
    _setlocale: config.locale,
    sort: config.sort ?? "relevance",
    ...initialParams,
  };

  const store = createStore<SearchControllerState>({
    status: "idle",
    data: null,
    structured: null,
    chat: null,
    documents: [],
    groups: [],
    error: null,
    params: defaultParams,
    request: searchParamsToRequest(defaultParams),
  });

  // T462 — stamp the cross-surface session id so search events stitch to a
  // later chat lead (both key off the same `TUR_SESSION` cookie). Minting here
  // means a search-first visitor's session is already established when chat
  // starts. No-op (null id) outside a browser.
  if (analytics) {
    analytics.setContext({ site: config.site, sessionId: getOrCreateTurSession({ name: sessionCookieName }) ?? undefined });
  }
  // Last real (non-wildcard) query, for refinement detection.
  let lastNonEmptyQuery: string | null = null;

  // Monotonic request id — the closure equivalent of `useRef(0)`. A late
  // response from a superseded request is dropped.
  let latestRequestId = 0;

  /** Emits the canonical search events after a successful result set. */
  function emitSearchEvents(query: string, resultCount: number): void {
    if (!analytics || !query || query === "*") return;
    if (lastNonEmptyQuery && lastNonEmptyQuery !== query) {
      analytics.emit(TURING_ANALYTICS_EVENTS.searchRefined, {
        from: lastNonEmptyQuery,
        to: query,
      });
    }
    analytics.emit(TURING_ANALYTICS_EVENTS.search, { query, results: resultCount });
    if (resultCount === 0) {
      analytics.emit(TURING_ANALYTICS_EVENTS.searchNoResults, { query });
    }
    lastNonEmptyQuery = query;
  }

  async function executeSearch(searchParams: SearchParams): Promise<void> {
    if (postNative) {
      return executeRequest(searchParamsToRequest(searchParams));
    }
    const requestId = ++latestRequestId;
    store.setState({ status: "loading", error: null, params: searchParams });

    try {
      const q = searchParams.q || "*";
      const shouldChat = chatEnabled && q !== "*";

      const [searchResult, chatResult] = await Promise.all([
        fetchSearch(client, config.site, searchParams),
        shouldChat
          ? fetchChat(client, config.site, { q, _setlocale: searchParams._setlocale })
          : Promise.resolve(null),
      ]);

      if (requestId !== latestRequestId) return;

      const documents = resolveDocuments(searchResult);
      store.setState({
        data: searchResult,
        chat: chatResult,
        documents,
        groups: resolveGroups(searchResult),
        status: "success",
      });

      // T462 — emit the search funnel events. `count` is the total hit count
      // (across pages); fall back to the resolved page when absent.
      const resultCount = searchResult?.queryContext?.count ?? documents.length;
      emitSearchEvents(q, resultCount);
    } catch (err) {
      if (requestId !== latestRequestId) return;
      store.setState({
        error: err instanceof Error ? err.message : "Search failed",
        status: "error",
      });
    }
  }

  /**
   * T709 — the POST-native path. Identical lifecycle to {@link executeSearch}
   * (stale-response guard, parallel chat call, analytics), but the query travels
   * as a JSON body and the response is the link-free shape.
   */
  async function executeRequest(searchRequest: TurSearchRequest): Promise<void> {
    const requestId = ++latestRequestId;
    store.setState({
      status: "loading",
      error: null,
      request: searchRequest,
      params: requestToSearchParams(searchRequest),
    });

    try {
      const q = searchRequest.q || "*";
      const shouldChat = chatEnabled && q !== "*";

      const [searchResult, chatResult] = await Promise.all([
        postStructuredSearch(client, config.site, searchRequest),
        shouldChat
          ? fetchChat(client, config.site, { q, _setlocale: searchRequest.locale })
          : Promise.resolve(null),
      ]);

      if (requestId !== latestRequestId) return;

      const documents = resolveDocuments(searchResult);
      store.setState({
        data: null,
        structured: searchResult,
        chat: chatResult,
        documents,
        groups: resolveStructuredGroups(searchResult),
        status: "success",
        // The server echoes the request with every default resolved — adopt it
        // so the next toggle starts from what actually ran, not from what we
        // guessed.
        request: searchResult.request ?? searchRequest,
        params: requestToSearchParams(searchResult.request ?? searchRequest),
      });

      const resultCount = searchResult?.queryContext?.count ?? documents.length;
      emitSearchEvents(q, resultCount);
    } catch (err) {
      if (requestId !== latestRequestId) return;
      store.setState({
        error: err instanceof Error ? err.message : "Search failed",
        status: "error",
      });
    }
  }

  /** Re-runs the current request with `changes` applied, back on page 1. */
  function patchRequest(changes: Partial<TurSearchRequest>, resetPage = true): Promise<void> {
    const current = store.getState().request;
    return executeRequest({ ...current, ...changes, ...(resetPage ? { page: 1 } : {}) });
  }

  return {
    getState: store.getState,
    setState: store.setState,
    subscribe: store.subscribe,
    search: (p) => executeSearch(p),
    searchRequest: (r) => executeRequest(r),
    searchQuery: (query) =>
      postNative
        ? patchRequest({ q: query })
        : executeSearch({ ...store.getState().params, q: query, p: "1" }),
    navigate: (href) => executeSearch(parseHrefToParams(href)),
    toggleFacet: (item) =>
      patchRequest({ fq: applyFacetAction(store.getState().request.fq ?? [], item) }),
    clearFacet: (facet) => {
      const clear = new Set(facet.clearFilterQueries ?? facet.selectedFilterQueries ?? []);
      return patchRequest({
        fq: (store.getState().request.fq ?? []).filter((fq) => !clear.has(fq)),
      });
    },
    clearAllFilters: () => patchRequest({ fq: [] }),
    applySpellCheck: (useOriginal = false) => {
      const spellCheck = store.getState().structured?.spellCheck;
      const text = useOriginal ? spellCheck?.originalText : spellCheck?.correctedTextValue;
      if (!text) return Promise.resolve();
      return patchRequest({ q: text, autoCorrectionDisabled: useOriginal });
    },
    changeLocale: (locale) =>
      postNative
        ? patchRequest({ locale })
        : executeSearch({ ...store.getState().params, _setlocale: locale }),
    changeSort: (sort) =>
      postNative
        ? patchRequest({ sort })
        : executeSearch({ ...store.getState().params, sort }),
    goToPage: (page) =>
      postNative
        ? patchRequest({ page }, false)
        : executeSearch({ ...store.getState().params, p: String(page) }),
    trackResultClick: (documentId, position, term) => {
      const params = store.getState().params;
      const query = term ?? params.q ?? "";
      // Server CTR (fire-and-forget; swallows its own errors).
      void postClick(client, config.site, {
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
  };
}
