import type { TuringClient } from "../client";
import {
  fetchChat,
  fetchSearch,
  parseHrefToParams,
  postClick,
  type SearchParams,
} from "../api";
import { TURING_ANALYTICS_EVENTS, type TuringAnalytics } from "../analytics";
import { getOrCreateTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../session";
import { resolveDocuments, resolveGroups } from "../resolve";
import { createStore, type Store } from "../store";
import type {
  ResolvedDocument,
  ResolvedGroup,
  SearchStatus,
  TuringConfig,
  TurChatResponse,
  TurSearchResponse,
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
  data: TurSearchResponse | null;
  chat: TurChatResponse | null;
  documents: ResolvedDocument[];
  groups: ResolvedGroup[];
  error: string | null;
  params: SearchParams;
}

export interface SearchController extends Store<SearchControllerState> {
  /** Execute a search with explicit params. */
  search(params: SearchParams): Promise<void>;
  /** Execute a new text query (resets page to 1). */
  searchQuery(query: string): Promise<void>;
  /** Navigate via a Turing href link (facet, pagination, spell-check). */
  navigate(href: string): Promise<void>;
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
}

export function createSearchController(
  client: TuringClient,
  config: TuringConfig,
  initialParams?: Partial<SearchParams>,
  options: SearchControllerOptions = {},
): SearchController {
  const { analytics, sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME, chat: chatEnabled = true } = options;
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
    chat: null,
    documents: [],
    groups: [],
    error: null,
    params: defaultParams,
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

  return {
    getState: store.getState,
    setState: store.setState,
    subscribe: store.subscribe,
    search: (p) => executeSearch(p),
    searchQuery: (query) =>
      executeSearch({ ...store.getState().params, q: query, p: "1" }),
    navigate: (href) => executeSearch(parseHrefToParams(href)),
    changeLocale: (locale) =>
      executeSearch({ ...store.getState().params, _setlocale: locale }),
    changeSort: (sort) => executeSearch({ ...store.getState().params, sort }),
    goToPage: (page) =>
      executeSearch({ ...store.getState().params, p: String(page) }),
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
