import { useCallback, useEffect, useState } from "react";
import { fetchSortOptions } from "../core/api";
import { useTuringContext } from "../core/use-turing-context";
import type { TurSortOption } from "../core/types";

export interface UseTuringSortOptionsReturn {
  sortOptions: TurSortOption[];
  isLoading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
}

/**
 * Hook that fetches the available sort options for the current site.
 * Returns the built-in sorts (relevance, newest, oldest) plus any
 * custom sorts configured in the Turing admin console.
 *
 * @example
 * ```tsx
 * const { sortOptions, isLoading } = useTuringSortOptions();
 *
 * <select onChange={(e) => changeSort(e.target.value)}>
 *   {sortOptions.map((o) => (
 *     <option key={o.value} value={o.value}>{o.label}</option>
 *   ))}
 * </select>
 * ```
 *
 * @since 2026.2.0
 */
export function useTuringSortOptions(): UseTuringSortOptionsReturn {
  const { config } = useTuringContext();
  const [sortOptions, setSortOptions] = useState<TurSortOption[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const options = await fetchSortOptions(config.site);
      setSortOptions(options);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load sort options");
    } finally {
      setIsLoading(false);
    }
  }, [config.site]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { sortOptions, isLoading, error, refresh };
}
