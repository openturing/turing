import { useMemo } from "react";
import { toSearchView, useSearchStore } from "@viglet/turing-react-sdk";
import type { SearchView } from "@viglet/turing-react-sdk";

/**
 * T865-T867 / §LXVII — the one place `/sn/{siteName}` reads a search response.
 *
 * <p>T710 made `postNative: true` the provider default, and POST-native the
 * provider sets `store.data = null` and fills `store.structured` instead. Three
 * consumers in this app kept reading `store.data`: the page gated its whole
 * render on it (so it showed `SearchError` on every successful search), the facet
 * rail lost its "Applied filters" row, and every facet rendered as single-select.
 *
 * <p>T881 / §LXX.3 — the projection itself now lives in `@viglet/turing-sdk`
 * beside `resolveDocuments`, because "read a response without caring which shape
 * it is" is not a question about this app: the vanilla SDK's `SearchController`
 * fills both slots for every EDS block too. What is left here is the React
 * flavouring — subscribe to the store, memoise the projection.
 */

export type {
  SearchView,
  SearchLocaleView,
  SearchSpellCheckView,
  AppliedFilterView,
} from "@viglet/turing-react-sdk";
export { toSearchView } from "@viglet/turing-react-sdk";

export function useSearchView(): SearchView {
  const store = useSearchStore();
  return useMemo(() => toSearchView(store), [store]);
}
