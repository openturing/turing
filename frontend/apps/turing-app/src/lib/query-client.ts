import { QueryClient } from '@tanstack/react-query';

/**
 * Singleton React Query client for the whole turing-app shell. Defaults are
 * tuned for typical admin-console usage:
 *
 * - `staleTime: 30s` — list/detail pages don't have to refetch on every focus.
 * - `gcTime: 5min` — keep cached entries around long enough to feel snappy
 *   when navigating back, but don't bloat memory.
 * - `retry: 1` — one quick retry on transient network failure; backend already
 *   has the resilience pipeline (circuit breaker + retry) for upstream calls.
 * - `refetchOnWindowFocus: false` — admin pages rarely need this and it
 *   surprises users when the table refreshes mid-edit.
 */
export const turingQueryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
});
