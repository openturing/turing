import type { TuringClient } from "../client";
import { fetchAutoComplete, type SearchParams } from "../api";
import { createStore, type Store } from "../store";
import type { TuringConfig } from "../types";

/**
 * Vanilla autocomplete controller — the framework-agnostic equivalent of the
 * React SDK's `useTuringAutoComplete`. Debounces calls to the Turing `/ac`
 * endpoint and exposes the suggestions over an observable {@link Store}.
 *
 * @example
 * ```js
 * const ac = createAutoComplete(client, { site: "my-site" }, 300);
 * ac.subscribe((s) => renderSuggestions(s.suggestions));
 * input.addEventListener("input", (e) => ac.fetch(e.target.value));
 * ```
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

export interface AutoCompleteState {
  suggestions: string[];
  isLoading: boolean;
}

export interface AutoCompleteController extends Store<AutoCompleteState> {
  /** Debounced fetch of suggestions for {@code query}. */
  fetch(query: string, extraParams?: Partial<SearchParams>): void;
  /** Clear suggestions and cancel any pending debounce/request. */
  clear(): void;
}

export function createAutoComplete(
  client: TuringClient,
  config: TuringConfig,
  debounceMs = 300,
): AutoCompleteController {
  const store = createStore<AutoCompleteState>({
    suggestions: [],
    isLoading: false,
  });

  let timer: ReturnType<typeof setTimeout> | undefined;
  let latestRequestId = 0;

  function fetch(query: string, extraParams?: Partial<SearchParams>): void {
    if (timer) clearTimeout(timer);

    if (!query || query.length < 2) {
      store.setState({ suggestions: [], isLoading: false });
      return;
    }

    store.setState({ isLoading: true });

    timer = setTimeout(async () => {
      const id = ++latestRequestId;
      try {
        const results = await fetchAutoComplete(client, config.site, {
          q: query,
          _setlocale: config.locale,
          sort: config.sort ?? "relevance",
          ...extraParams,
        });
        if (id === latestRequestId) {
          store.setState({ suggestions: results, isLoading: false });
        }
      } catch {
        if (id === latestRequestId) {
          store.setState({ suggestions: [], isLoading: false });
        }
      }
    }, debounceMs);
  }

  function clear(): void {
    if (timer) clearTimeout(timer);
    latestRequestId++;
    store.setState({ suggestions: [], isLoading: false });
  }

  return {
    getState: store.getState,
    setState: store.setState,
    subscribe: store.subscribe,
    fetch,
    clear,
  };
}
