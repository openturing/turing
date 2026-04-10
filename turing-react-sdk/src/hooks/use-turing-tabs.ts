import { useCallback, useEffect, useMemo } from "react";
import { useSearchStore } from "../core/use-search-store";
import type { ResolvedDocument, ResolvedGroup } from "../core/types";

// ── Types ──

export interface TabDefinition {
  /** Display label */
  label: string;
  /**
   * Facet filter value to apply when this tab is active.
   * Format: "fieldName:value" (e.g. "templateName:cursos").
   * Null/undefined for an "All" tab that shows grouped results.
   */
  filter?: string | null;
  /**
   * Group field name (e.g. "templateName"). When set, results are grouped
   * and `rows` limits per group. Typically used for the "All" tab.
   *
   * This is an **implicit param** — it is sent to the API but does NOT
   * appear in the URL, keeping the URL clean.
   */
  group?: string | null;
  /** Results per page/group (implicit). Defaults to server default when not set. */
  rows?: number;
  /**
   * Default sort for this tab (implicit).
   * Applied when this tab becomes active. Explicit URL sort overrides this.
   */
  defaultSort?: string;
}

export interface EnrichedTab {
  /** Tab definition */
  definition: TabDefinition;
  /** Display label */
  label: string;
  /** Index in the items array */
  index: number;
  /** Whether this tab is currently active */
  isActive: boolean;
  /** Select this tab (updates URL params) */
  select: () => void;
  /** Number of results for this tab (from grouped data, if available) */
  count?: number;
}

export interface UseTuringTabsOptions {
  /**
   * The facet field name used to differentiate tabs.
   * The hook uses this to identify which fq[] entries belong to tab switching
   * vs. user-selected facets.
   */
  attribute: string;
  /** Tab definitions */
  items: readonly TabDefinition[];
}

export interface UseTuringTabsReturn {
  /** Index of the active tab */
  activeIndex: number;
  /** The active tab definition */
  activeTab: EnrichedTab;
  /** All tabs with action helpers */
  tabs: EnrichedTab[];
  /** Whether the active tab is a grouped/overview tab */
  isGrouped: boolean;
  /** Documents for the active tab (flat results or all grouped docs) */
  documents: ResolvedDocument[];
  /** Groups for grouped tabs */
  groups: ResolvedGroup[];
}

/**
 * Manages tab-based search navigation. Handles all the complexity of
 * switching fq[] filters, group params, rows, and sort when tabs change.
 *
 * **Implicit params**: `group`, `rows`, and `defaultSort` from the active
 * tab definition are registered as implicit params — they are sent to the
 * API but do NOT appear in the URL. This keeps URLs clean:
 * - Before: `?_setlocale=pt&group=templateName&rows=3`
 * - After:  `?_setlocale=pt`
 *
 * @example
 * ```tsx
 * const TABS = [
 *   { label: "All",     group: "templateName", rows: 3 },
 *   { label: "Courses", filter: "templateName:cursos" },
 *   { label: "News",    filter: "templateName:noticias" },
 *   { label: "Faculty", filter: "templateName:detalhe-de-docente", defaultSort: "title_str:asc" },
 * ];
 *
 * function SearchPage() {
 *   const { tabs, activeTab, isGrouped, documents, groups } = useTuringTabs({
 *     attribute: "templateName",
 *     items: TABS,
 *   });
 *
 *   return (
 *     <>
 *       <div className="tab-header">
 *         {tabs.map(tab => (
 *           <button
 *             key={tab.index}
 *             onClick={tab.select}
 *             className={tab.isActive ? "active" : ""}
 *           >
 *             {tab.label}
 *           </button>
 *         ))}
 *       </div>
 *
 *       {isGrouped ? (
 *         groups.map(g => <GroupSection key={g.name} group={g} />)
 *       ) : (
 *         documents.map(d => <Card key={d.url} doc={d} />)
 *       )}
 *     </>
 *   );
 * }
 * ```
 *
 * @since 2026.3.0
 */
export function useTuringTabs(options: UseTuringTabsOptions): UseTuringTabsReturn {
  const { attribute, items } = options;
  const store = useSearchStore();
  const { params, updateParams, documents, groups, setImplicitParams } = store;

  // Determine active tab from current fq[]
  const activeIndex = useMemo(() => {
    const currentFq = params.fq ?? [];
    const prefix = `${attribute}:`;

    for (let i = 0; i < items.length; i++) {
      const tab = items[i];
      if (tab.filter && currentFq.includes(tab.filter)) return i;
    }

    // If no filter matches and there's a group tab, it's the "all" tab
    const hasTabFilter = currentFq.some((f) => f.startsWith(prefix));
    if (!hasTabFilter) {
      const allTabIndex = items.findIndex((t) => !t.filter && t.group);
      if (allTabIndex >= 0) return allTabIndex;
    }

    return 0;
  }, [params.fq, attribute, items]);

  // Register implicit params for the active tab
  useEffect(() => {
    const activeDef = items[activeIndex];
    if (!activeDef) return;

    const implicit: Record<string, string | undefined> = {};

    if (activeDef.group) {
      implicit.group = activeDef.group;
      implicit.rows = activeDef.rows ? String(activeDef.rows) : "3";
    }

    if (activeDef.defaultSort) {
      implicit.sort = activeDef.defaultSort;
    }

    if (Object.keys(implicit).length > 0) {
      setImplicitParams("tabs", implicit);
    } else {
      setImplicitParams("tabs", null);
    }
  }, [activeIndex, items, setImplicitParams]);

  // Clean up on unmount
  useEffect(() => {
    return () => setImplicitParams("tabs", null);
  }, [setImplicitParams]);

  const selectTab = useCallback(
    (tabIndex: number) => {
      const tab = items[tabIndex];
      const currentFq = params.fq ?? [];
      const prefix = `${attribute}:`;

      // Keep all fq[] entries that are NOT tab-related
      const otherFq = currentFq.filter(
        (f) => !f.startsWith(prefix) && !f.startsWith("alphabet:"),
      );

      // Build new fq[]
      const nextFq = tab.filter ? [...otherFq, tab.filter] : otherFq;

      // Only update explicit URL params: fq and p
      // group/rows/sort are handled implicitly via the effect above
      updateParams({
        fq: nextFq.length > 0 ? nextFq : undefined,
        p: "1",
        // Clear explicit group/rows from URL (now implicit)
        group: undefined,
        rows: undefined,
      });
    },
    [items, params.fq, attribute, updateParams],
  );

  const tabs = useMemo<EnrichedTab[]>(() => {
    return items.map((def, i) => {
      // Try to find count from groups
      let count: number | undefined;
      if (groups.length > 0 && def.filter) {
        const value = def.filter.split(":").slice(1).join(":");
        const group = groups.find((g) => g.name === value);
        if (group) count = group.count;
      }

      return {
        definition: def,
        label: def.label,
        index: i,
        isActive: i === activeIndex,
        select: () => selectTab(i),
        count,
      };
    });
  }, [items, activeIndex, selectTab, groups]);

  const activeTab = tabs[activeIndex];
  const isGrouped = Boolean(items[activeIndex]?.group);

  return {
    activeIndex,
    activeTab,
    tabs,
    isGrouped,
    documents,
    groups,
  };
}
