import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Checkbox } from "@/components/ui/checkbox";
import type { EnrichedFacetGroup } from "@viglet/turing-react-sdk";
import { useTuringFacets } from "@viglet/turing-react-sdk";
import { IconFilter, IconX } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useSearchView } from "../lib/search-view";

/**
 * T936 — one rail's worth of accordion. Extracted so the secondary groups
 * render exactly like the primary ones instead of a second, drifting copy.
 */
function FacetRail({
  groups,
  multiSelectFacetItems,
}: {
  groups: EnrichedFacetGroup[];
  multiSelectFacetItems: boolean;
}) {
  const { t } = useTranslation();

  return (
    <Accordion type="multiple" className="space-y-2">
      {groups.map((group) => (
        <AccordionItem
          key={`${group.label}-${group.name}`}
          value={`facet-${group.label}`}
          className="border border-border/60 rounded-xl overflow-hidden data-[state=open]:border-border"
        >
          <div className="relative">
            <AccordionTrigger
              className={`px-4 py-3 text-sm font-semibold hover:no-underline ${
                group.hasSelected ? "pr-20" : ""
              }`}
            >
              {group.label}
            </AccordionTrigger>
            {group.hasSelected && (
              <button
                type="button"
                onClick={group.clear}
                aria-label={`${t("search.clearFacet")} ${group.label}`}
                className="absolute right-10 top-1/2 -translate-y-1/2 text-xs text-blue-500 hover:text-blue-600 font-medium cursor-pointer"
              >
                {t("search.clearFacet")}
              </button>
            )}
          </div>
          <AccordionContent className="px-2 pb-2">
            <div className="space-y-0.5">
              {group.facets.map((facet) => (
                <button
                  key={facet.label}
                  type="button"
                  onClick={facet.toggle}
                  className="w-full flex items-center justify-between gap-2 px-2.5 py-2 rounded-lg text-sm hover:bg-accent transition-colors cursor-pointer"
                >
                  <span className="flex items-center gap-2 min-w-0">
                    {multiSelectFacetItems && (
                      <Checkbox
                        checked={Boolean(facet.selected)}
                        aria-label={facet.label}
                        className="pointer-events-none shrink-0"
                      />
                    )}
                    <span className="truncate">{facet.label}</span>
                  </span>
                  <span className="text-xs text-muted-foreground tabular-nums shrink-0">
                    {facet.count.toLocaleString()}
                  </span>
                </button>
              ))}
            </div>
          </AccordionContent>
        </AccordionItem>
      ))}
    </Accordion>
  );
}

export function FacetSidebar() {
  const { t } = useTranslation();
  // T936 — the secondary rail is its own list on the hook, so a site that
  // configures one no longer has it silently dropped here.
  const { facetGroups, secondaryFacetGroups, hasSelected, clearAll } = useTuringFacets();
  // T866 / §LXVII.2 — both of these used to come from `store.data`, which is null
  // POST-native: the applied-filters row (and its Clear all) disappeared, and
  // every facet drew as if the site were single-select.
  const { appliedFilters, multiSelectFacetItems } = useSearchView();

  return (
    <aside className="w-64 shrink-0 space-y-3">
      {/* Applied Filters */}
      {appliedFilters.length > 0 && (
        <div className="rounded-xl border border-blue-500/30 bg-blue-500/5 overflow-hidden">
          <div className="px-4 py-3 flex items-center justify-between">
            <span className="text-sm font-semibold flex items-center gap-2">
              <IconFilter className="size-4 text-blue-500" />
              {t("search.appliedFilters")}
            </span>
            <button
              type="button"
              onClick={clearAll}
              disabled={!hasSelected}
              className="text-xs text-blue-500 hover:text-blue-600 font-medium cursor-pointer"
            >
              {t("search.clearAll")}
            </button>
          </div>
          <div className="px-2 pb-2 flex flex-wrap gap-1.5">
            {appliedFilters.map((filter) => (
              <button
                key={filter.key}
                type="button"
                onClick={filter.remove}
                className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-blue-500/10 text-blue-700 dark:text-blue-300 text-xs font-medium hover:bg-blue-500/20 transition-colors cursor-pointer"
              >
                {filter.label}
                <IconX className="size-3" />
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Facet Groups */}
      <FacetRail groups={facetGroups} multiSelectFacetItems={multiSelectFacetItems} />

      {secondaryFacetGroups.length > 0 && (
        <div className="space-y-2 pt-1">
          <span className="px-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            {t("search.secondaryFilters")}
          </span>
          <FacetRail
            groups={secondaryFacetGroups}
            multiSelectFacetItems={multiSelectFacetItems}
          />
        </div>
      )}
    </aside>
  );
}
