import { useCallback, useRef, useState } from "react";
import { fetchAutoComplete, type SearchParams } from "../core/api";
import { useTuringContext } from "../core/use-turing-context";

export interface UseTuringAutoCompleteReturn {
  suggestions: string[];
  isLoading: boolean;
  fetch: (query: string, extraParams?: Partial<SearchParams>) => void;
  clear: () => void;
}

/**
 * Autocomplete hook with debouncing. Calls the Turing `/ac` endpoint.
 *
 * @example
 * ```tsx
 * const { suggestions, fetch, clear } = useTuringAutoComplete(300);
 *
 * <input onChange={(e) => fetch(e.target.value)} onBlur={() => clear()} />
 * {suggestions.map((s) => <div key={s}>{s}</div>)}
 * ```
 *
 * @since 2026.2.0
 */
export function useTuringAutoComplete(debounceMs = 300): UseTuringAutoCompleteReturn {
  const { config } = useTuringContext();
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  const requestRef = useRef(0);

  const doFetch = useCallback(
    (query: string, extraParams?: Partial<SearchParams>) => {
      if (timerRef.current) clearTimeout(timerRef.current);

      if (!query || query.length < 2) {
        setSuggestions([]);
        setIsLoading(false);
        return;
      }

      setIsLoading(true);

      timerRef.current = setTimeout(async () => {
        const id = ++requestRef.current;
        try {
          const results = await fetchAutoComplete(config.site, {
            q: query,
            _setlocale: config.locale,
            sort: config.sort ?? "relevance",
            ...extraParams,
          });
          if (id === requestRef.current) setSuggestions(results);
        } catch {
          if (id === requestRef.current) setSuggestions([]);
        } finally {
          if (id === requestRef.current) setIsLoading(false);
        }
      }, debounceMs);
    },
    [config.site, config.locale, config.sort, debounceMs],
  );

  const clear = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    requestRef.current++;
    setSuggestions([]);
    setIsLoading(false);
  }, []);

  return { suggestions, isLoading, fetch: doFetch, clear };
}
