import { useMemo } from "react";
import type { SearchStoreValue } from "../core/context";
import type { ClientToolHandler } from "../core/types";
import type { TurStructuredFacetItem } from "@viglet/turing-sdk";

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

/** The agent names a facet by its visible label, so every match is folded. */
function sameName(a: string | undefined, b: string): boolean {
  return a?.toLowerCase() === b.toLowerCase();
}

/**
 * T713 — find a structured facet item by group name + visible value label
 * (case-insensitive). This is the POST-native lookup and the only one that
 * works in the default mode: `store.data` is `null` there, so the previous
 * href-based lookup made `toggle_facet` / `clear_facets` fail outright.
 *
 * <p>T884 — searches the secondary rail too. A field configured as a secondary
 * facet is on `structured.secondaryFacets`, so looking only at `facets` answered
 * "Facet not found" for it — and the agent has no way to tell a secondary facet
 * from a primary one, because the user cannot either.
 */
function findStructuredFacetItem(
  store: SearchStoreValue,
  field: string,
  value: string,
): TurStructuredFacetItem | undefined {
  const groups = [
    ...(store.structured?.facets ?? []),
    ...(store.structured?.secondaryFacets ?? []),
  ];
  return groups
    .filter((g) => sameName(g.name, field))
    .flatMap((g) => g.items ?? [])
    .find((item) => sameName(item.label, value) || sameName(item.value, value));
}

/**
 * Legacy (`postNative: false`) fallback: the same lookup against the
 * link-bearing response, secondary rail included (T884).
 *
 * @deprecated T713 — goes away with `navigate(href)` in the next major.
 */
function findFacetLink(
  store: SearchStoreValue,
  field: string,
  value: string,
): string | undefined {
  const widget = store.data?.widget;
  const groups = [...(widget?.facet ?? []), ...(widget?.secondaryFacet ?? [])];
  return groups
    .filter((g) => sameName(g.name, field))
    .flatMap((g) => g.facets ?? [])
    .find((f) => sameName(f.label, value))?.link;
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
        // POST-native first — `store.structured` is what the default mode fills.
        const item = findStructuredFacetItem(store, field, value);
        if (item) {
          store.toggleFacet(item);
          return { success: true, field, value };
        }
        const link = findFacetLink(store, field, value);
        if (!link) {
          return { success: false, message: `Facet not found: ${field}=${value}` };
        }
        store.navigate(link);
        return { success: true, field, value };
      },
      clear_facets: () => {
        const cleared = (store.params.fq ?? []).length;
        if (store.structured) {
          store.clearAllFilters();
          return { success: true, cleared };
        }
        const cleanUp = store.data?.widget?.cleanUpFacets;
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
