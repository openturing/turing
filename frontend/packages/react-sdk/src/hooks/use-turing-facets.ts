import { useCallback, useMemo } from "react";
import { useSearchStore } from "../core/use-search-store";
import type { TurFacetGroup, TurFacetItem } from "../core/types";

// ── Types ──

export interface FacetAction {
  /** Toggle this facet on/off */
  toggle: () => void;
}

export type EnrichedFacetItem = TurFacetItem & FacetAction;

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
  /** Facet groups with toggle/clear actions attached */
  facetGroups: EnrichedFacetGroup[];
  /** Whether any facet across all groups is selected */
  hasSelected: boolean;
  /** Clear all selected facets */
  clearAll: () => void;
  /** Raw facet data from API */
  raw: TurFacetGroup[];
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
 * @since 2026.3.0
 */
export function useTuringFacets(): UseTuringFacetsReturn {
  const store = useSearchStore();
  const rawGroups = store.data?.widget?.facet ?? [];
  const cleanUpLink = store.data?.widget?.cleanUpFacets;

  const { navigate } = store;

  const clearAll = useCallback(() => {
    if (cleanUpLink) navigate(cleanUpLink);
  }, [cleanUpLink, navigate]);

  const facetGroups = useMemo<EnrichedFacetGroup[]>(() => {
    return rawGroups.map((group) => {
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
    });
  }, [rawGroups, navigate]);

  const hasSelected = facetGroups.some((g) => g.hasSelected);

  return { facetGroups, hasSelected, clearAll, raw: rawGroups };
}
