import { useMemo } from "react";
import type { SearchStoreValue } from "../core/context";
import type { ClientToolHandler } from "../core/types";

/**
 * T443 / §XXIII.2 — co-browse the search UI.
 *
 * <p>Returns client-tool handlers that let the chat agent drive the host page's
 * REAL search state (query, facet checkboxes, sort, page) through the same search
 * store the visible UI renders from — so "filter to the red ones under R$50"
 * flips the actual facets and updates the URL. The handler names match the
 * backend's built-in co-browse client tools (advertised when the agent has
 * {@code coBrowseEnabled}).
 *
 * <p>Pass it the search store from {@code useTuringUrlSearch()} (URL-synced) and
 * spread {@link UseCoBrowseReturn.clientTools} into {@code useTuringChat}:
 *
 * <pre>
 *   const store = useTuringUrlSearch();
 *   const coBrowse = useCoBrowseSearch(store);
 *   const chat = useTuringChat({ agent, clientTools: coBrowse.clientTools });
 * </pre>
 *
 * <p>Facet mutations reuse the server-supplied toggle/clear links (the same path
 * {@code useTuringFacets} uses), so the backend's filter-query encoding stays the
 * single source of truth — the agent matches a facet by group name + visible
 * value label and the hook follows its {@code link}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export interface UseCoBrowseReturn {
  /** Spread into {@code useTuringChat}'s {@code clientTools} option. */
  clientTools: Record<string, ClientToolHandler>;
}

interface ToggleFacetArgs {
  field?: unknown;
  value?: unknown;
}

/** Find a facet's toggle link by group name + visible value label (case-insensitive). */
function findFacetLink(
  store: SearchStoreValue,
  field: string,
  value: string,
): string | undefined {
  const groups = store.data?.widget?.facet ?? [];
  const group = groups.find((g) => g.name?.toLowerCase() === field.toLowerCase());
  const item = group?.facets.find((f) => f.label?.toLowerCase() === value.toLowerCase());
  return item?.link;
}

export function useCoBrowseSearch(store: SearchStoreValue): UseCoBrowseReturn {
  const clientTools = useMemo<Record<string, ClientToolHandler>>(
    () => ({
      set_search_query: (args) => {
        const query = String((args as { query?: unknown })?.query ?? "").trim();
        store.updateParams({ q: query || "*", p: "1" });
        return { success: true, query };
      },
      toggle_facet: (args) => {
        const { field, value } = (args as ToggleFacetArgs) ?? {};
        if (typeof field !== "string" || typeof value !== "string") {
          return { success: false, message: "field and value are required" };
        }
        const link = findFacetLink(store, field, value);
        if (!link) {
          return { success: false, message: `Facet not found: ${field}=${value}` };
        }
        store.navigate(link);
        return { success: true, field, value };
      },
      clear_facets: () => {
        const cleanUp = store.data?.widget?.cleanUpFacets;
        const cleared = (store.params.fq ?? []).length;
        if (cleanUp) {
          store.navigate(cleanUp);
        } else {
          store.updateParams({ fq: undefined });
        }
        return { success: true, cleared };
      },
      set_sort: (args) => {
        const sort = String((args as { sort?: unknown })?.sort ?? "").trim();
        if (!sort) return { success: false, message: "sort is required" };
        store.setSort(sort);
        return { success: true, sort };
      },
      set_page: (args) => {
        const raw = (args as { page?: unknown })?.page;
        const page = Math.max(1, Math.trunc(Number(raw)) || 1);
        store.updateParams({ p: String(page) });
        return { success: true, page };
      },
    }),
    [store],
  );

  return { clientTools };
}
