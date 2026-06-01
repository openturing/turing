import type { TuringClient } from "../client";
import {
  fetchChat,
  fetchSearch,
  parseHrefToParams,
  type SearchParams,
} from "../api";
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
}

export function createSearchController(
  client: TuringClient,
  config: TuringConfig,
  initialParams?: Partial<SearchParams>,
): SearchController {
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

  // Monotonic request id — the closure equivalent of `useRef(0)`. A late
  // response from a superseded request is dropped.
  let latestRequestId = 0;

  async function executeSearch(searchParams: SearchParams): Promise<void> {
    const requestId = ++latestRequestId;
    store.setState({ status: "loading", error: null, params: searchParams });

    try {
      const q = searchParams.q || "*";
      const shouldChat = q !== "*";

      const [searchResult, chatResult] = await Promise.all([
        fetchSearch(client, config.site, searchParams),
        shouldChat
          ? fetchChat(client, config.site, { q, _setlocale: searchParams._setlocale })
          : Promise.resolve(null),
      ]);

      if (requestId !== latestRequestId) return;

      store.setState({
        data: searchResult,
        chat: chatResult,
        documents: resolveDocuments(searchResult),
        groups: resolveGroups(searchResult),
        status: "success",
      });
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
  };
}
