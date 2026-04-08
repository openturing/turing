import { useContext } from "react";
import { SearchStoreContext, type SearchStoreValue } from "../core/context";

export type UseTuringUrlSearchReturn = SearchStoreValue;

/**
 * Returns the shared search store from TuringProvider.
 *
 * **New usage (v2026.3+):** When `urlSync` is configured on the provider,
 * this hook simply returns the shared store — no arguments needed.
 *
 * **Legacy usage:** Pass searchParams + setSearchParams for backward compatibility
 * (the arguments are ignored when the store is available).
 *
 * @example
 * ```tsx
 * // New: provider handles URL sync
 * function SearchPage() {
 *   const turing = useTuringUrlSearch();
 *   // turing.documents, turing.submitSearch(), turing.navigate(), etc.
 * }
 *
 * // Legacy: still works for backward compat
 * const turing = useTuringUrlSearch(searchParams, setSearchParams);
 * ```
 *
 * @since 2026.2.0
 */
export function useTuringUrlSearch(
  _searchParams?: URLSearchParams,
  _setSearchParams?: (params: string) => void,
): UseTuringUrlSearchReturn {
  const store = useContext(SearchStoreContext);
  if (!store) {
    throw new Error(
      "useTuringUrlSearch requires <TuringProvider urlSync={...}>. " +
      "Configure urlSync on the provider to use this hook."
    );
  }
  return store;
}
