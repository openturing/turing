import { useContext } from "react";
import { SearchStoreContext, type SearchStoreValue } from "./context";

/**
 * Returns the shared search store from TuringProvider.
 * Requires `urlSync` to be configured on the provider.
 *
 * All derived hooks (useTuringFacets, useTuringPagination, useTuringTabs, etc.)
 * use this internally to access the shared search state.
 *
 * @since 2026.3.0
 */
export function useSearchStore(): SearchStoreValue {
  const store = useContext(SearchStoreContext);
  if (!store) {
    throw new Error(
      "useSearchStore requires <TuringProvider urlSync={...}>. " +
      "Pass urlSync to the provider or use useTuringSearch() for manual mode."
    );
  }
  return store;
}
