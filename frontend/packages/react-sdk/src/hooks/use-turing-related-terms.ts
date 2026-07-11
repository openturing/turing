import { useCallback, useEffect, useRef, useState } from "react";
import { fetchRelatedTerms } from "../core/api";
import { useTuringContext } from "../core/use-turing-context";
import type { TurRelatedTermSuggestion } from "../core/types";

export interface UseTuringRelatedTermsOptions {
  /**
   * Query to look up. When set, the hook auto-fetches on mount and whenever the
   * query changes. Omit to drive lookups imperatively via
   * {@link UseTuringRelatedTermsReturn.lookup}.
   */
  readonly query?: string;
  /** Locale path segment (e.g. {@code "en"}); falls back to the provider locale. */
  readonly locale?: string;
  /** Disables the auto-lookup when {@code false}. Defaults to {@code true}. */
  readonly enabled?: boolean;
}

export interface UseTuringRelatedTermsReturn {
  /** The related-concept suggestions, one per recognised term. */
  readonly suggestions: TurRelatedTermSuggestion[];
  /** Convenience: every related label across all recognised terms, de-duplicated. */
  readonly relatedLabels: string[];
  /** Whether a lookup is in flight. */
  readonly loading: boolean;
  /** Error message from the last failed lookup, or {@code null}. */
  readonly error: string | null;
  /** Imperatively run a related-terms lookup for a given query. */
  readonly lookup: (query: string) => Promise<void>;
}

/**
 * T678 — fetches controlled-vocabulary "related concepts" for a query (the
 * microthesaurus {@code RELATED}/RT links). A sibling of {@code useTuringSpellCheck}:
 * spell-check fixes a typo, this proposes conceptually-related searches a search
 * box can render as "you may also be interested in" chips.
 *
 * @example
 * ```tsx
 * const { relatedLabels } = useTuringRelatedTerms({ query, locale: "en" });
 * {relatedLabels.map((label) => (
 *   <button key={label} onClick={() => search(label)}>{label}</button>
 * ))}
 * ```
 *
 * @since 2026.3.4
 */
export function useTuringRelatedTerms(
  options: UseTuringRelatedTermsOptions = {},
): UseTuringRelatedTermsReturn {
  const { config } = useTuringContext();
  const { query, locale, enabled = true } = options;
  const resolvedLocale = locale ?? config.locale ?? "en";

  const [suggestions, setSuggestions] = useState<TurRelatedTermSuggestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestIdRef = useRef(0);

  const lookup = useCallback(
    async (q: string): Promise<void> => {
      if (!q) {
        setSuggestions([]);
        return;
      }
      const requestId = ++requestIdRef.current;
      setLoading(true);
      setError(null);
      try {
        const res = await fetchRelatedTerms(config.site, resolvedLocale, q);
        if (requestId !== requestIdRef.current) return;
        setSuggestions(res ?? []);
        setLoading(false);
      } catch (err) {
        if (requestId !== requestIdRef.current) return;
        setError(err instanceof Error ? err.message : "Related-terms lookup failed");
        setSuggestions([]);
        setLoading(false);
      }
    },
    [config.site, resolvedLocale],
  );

  useEffect(() => {
    if (!enabled || !query) return;
    void lookup(query);
  }, [enabled, query, lookup]);

  const relatedLabels = Array.from(
    new Set(suggestions.flatMap((s) => s.related)),
  );

  return { suggestions, relatedLabels, loading, error, lookup };
}
