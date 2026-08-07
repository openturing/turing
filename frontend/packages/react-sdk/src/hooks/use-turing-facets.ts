import { useCallback, useMemo } from "react";
import { useSearchStore } from "../core/use-search-store";
import type { TurFacetGroup, TurFacetItem, TurStructuredFacet } from "../core/types";

// ── Types ──

export interface FacetAction {
  /** Toggle this facet on/off */
  toggle: () => void;
}

export type EnrichedFacetItem = Omit<TurFacetItem, "link"> &
  FacetAction & {
    /**
     * T860 — the server-rendered href for this facet value, present **only** in
     * legacy (`postNative: false`) mode. The link-free response has no href, so
     * this is `undefined` POST-native.
     *
     * It used to be typed `string` and filled with `""` in that case, which made
     * `<a href={facet.link}>` compile into a link to the current page — a silent,
     * plausible-looking bug. Use {@link FacetAction.toggle}; reach for `link`
     * only to render a crawlable anchor, and handle its absence.
     */
    link?: string;
  };

export interface EnrichedFacetGroup {
  /** Group name */
  name: string;
  /** Localized label */
  label: string;
  /** Whether the group supports multiple selections */
  multivalued: boolean;
  /** Facet items with action helpers */
  facets: EnrichedFacetItem[];
  /** Whether any facet in this group is selected */
  hasSelected: boolean;
  /** Clear all selections in this group */
  clear: () => void;
}

export interface UseTuringFacetsReturn {
  /** Primary facet groups with toggle/clear actions attached */
  facetGroups: EnrichedFacetGroup[];
  /**
   * T936 — the groups the site configured as *secondary*, enriched exactly like
   * {@link facetGroups}. A **second list** rather than a `secondary` flag on one
   * list, because returning them in `facetGroups` would make every existing
   * consumer render a rail it never asked for; opting in is a render decision
   * and belongs to the page.
   *
   * Empty on a site that configures no secondary facet, in either transport.
   */
  secondaryFacetGroups: EnrichedFacetGroup[];
  /** Whether any facet across both rails is selected */
  hasSelected: boolean;
  /** Clear all selected facets, on both rails */
  clearAll: () => void;
  /** Raw primary facet data from API */
  raw: TurFacetGroup[];
  /** T936 — raw secondary facet data from API */
  rawSecondary: TurFacetGroup[];
}

/**
 * Provides facet data with action helpers attached to each item.
 * Reads from the shared search store (requires `urlSync` on TuringProvider).
 *
 * @example
 * ```tsx
 * function FacetSidebar() {
 *   const { facetGroups, hasSelected, clearAll } = useTuringFacets();
 *
 *   return (
 *     <aside>
 *       <button onClick={clearAll} disabled={!hasSelected}>Clear all</button>
 *       {facetGroups.map(group => (
 *         <div key={group.name}>
 *           <h3>{group.label}</h3>
 *           {group.facets.map(facet => (
 *             <label key={facet.label}>
 *               <input
 *                 type="checkbox"
 *                 checked={facet.selected}
 *                 onChange={facet.toggle}
 *               />
 *               {facet.label} ({facet.count})
 *             </label>
 *           ))}
 *         </div>
 *       ))}
 *     </aside>
 *   );
 * }
 * ```
 *
 * T936 — a site can configure a *secondary* rail; those groups arrive on
 * {@link UseTuringFacetsReturn.secondaryFacetGroups} and are yours to render
 * (or not) separately. They are never mixed into `facetGroups`.
 *
 * @since 2026.3.0
 */
export function useTuringFacets(): UseTuringFacetsReturn {
  const store = useSearchStore();
  const structuredFacets = store.structured?.facets;
  // T936 — `undefined` on a site with no secondary rail, which is what keeps the
  // structured/legacy switch below reading `structuredFacets` and not this one.
  const structuredSecondary = store.structured?.secondaryFacets;
  const rawGroups = store.data?.widget?.facet ?? [];
  const rawSecondary = store.data?.widget?.secondaryFacet ?? [];
  const cleanUpLink = store.data?.widget?.cleanUpFacets;

  const { navigate, toggleFacet, clearFacet, clearAllFilters } = store;

  // T710 — POST-native, "clear all" is a filter-query subtraction, not a URL.
  const clearAll = useCallback(() => {
    if (structuredFacets) clearAllFilters();
    else if (cleanUpLink) navigate(cleanUpLink);
  }, [structuredFacets, clearAllFilters, cleanUpLink, navigate]);

  /**
   * T710 — the link-free projection. Each item keeps the same public shape
   * ({@link EnrichedFacetItem}) so a consumer's JSX is unchanged; only what
   * `toggle`/`clear` do underneath differs — a structured action instead of a
   * parsed href.
   *
   * T860 — `link` is *omitted*, not emptied. There is no href here, and saying
   * so is the point: an empty string would still satisfy `<a href={…}>` and
   * quietly link to the current page.
   */
  const enrichStructured = useCallback(
    (groups: TurStructuredFacet[]): EnrichedFacetGroup[] =>
      groups.map((group: TurStructuredFacet) => {
        const facets: EnrichedFacetItem[] = group.items.map((item) => ({
          count: item.count,
          label: item.label,
          selected: item.selected,
          toggle: () => toggleFacet(item),
        }));
        return {
          name: group.name,
          label: group.label ?? group.name,
          multivalued: group.multiValued,
          facets,
          hasSelected: facets.some((f) => f.selected),
          clear: () => clearFacet(group),
        };
      }),
    [toggleFacet, clearFacet],
  );

  const enrichLegacy = useCallback(
    (groups: TurFacetGroup[]): EnrichedFacetGroup[] =>
      groups.map((group) => {
        const facets: EnrichedFacetItem[] = group.facets.map((facet) => ({
          ...facet,
          toggle: () => navigate(facet.link),
        }));

        const hasSelected = facets.some((f) => f.selected);

        return {
          name: group.name,
          label: group.label.text,
          multivalued: group.multivalued,
          facets,
          hasSelected,
          clear: () => {
            if (group.cleanUpLink) navigate(group.cleanUpLink);
          },
        };
      }),
    [navigate],
  );

  const structuredGroups = useMemo<EnrichedFacetGroup[] | null>(
    () => (structuredFacets ? enrichStructured(structuredFacets) : null),
    [structuredFacets, enrichStructured],
  );

  const legacyGroups = useMemo<EnrichedFacetGroup[]>(
    () => enrichLegacy(rawGroups),
    [rawGroups, enrichLegacy],
  );

  /**
   * T936 — the secondary rail follows whichever transport answered, so a site
   * with only secondary facets configured still lands on the structured arm:
   * `structuredFacets` being defined (even empty) is what says the response was
   * link-free, not whether it had any primary group.
   */
  const secondaryFacetGroups = useMemo<EnrichedFacetGroup[]>(
    () =>
      structuredFacets
        ? enrichStructured(structuredSecondary ?? [])
        : enrichLegacy(rawSecondary),
    [structuredFacets, structuredSecondary, rawSecondary, enrichStructured, enrichLegacy],
  );

  const facetGroups = structuredGroups ?? legacyGroups;
  const hasSelected =
    facetGroups.some((g) => g.hasSelected) ||
    secondaryFacetGroups.some((g) => g.hasSelected);

  return {
    facetGroups,
    secondaryFacetGroups,
    hasSelected,
    clearAll,
    raw: rawGroups,
    rawSecondary,
  };
}
