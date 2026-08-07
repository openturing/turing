import type {
  TurFacetToggleTarget,
  TurQueryContext,
  TurSearchResponse,
  TurStructuredFacet,
  TurStructuredSearchResponse,
} from "./types";

/**
 * T881 / §LXX.3 — read a search response without caring which shape arrived.
 *
 * <p>This lives beside `resolveDocuments` / `resolveGroups` because it answers the
 * same question they do, for the parts of the response they do not cover: the
 * site icon, the locales, the applied-filter chips, the spell-check offer and
 * whether facets multi-select. It was written in `turing-app` (§LXVII, T865–T868)
 * after three components each reached into `store.data` and each broke the same
 * way POST-native — and it stayed reachable from that one app while the vanilla
 * SDK's `SearchController` fills both `data` and `structured` for every EDS block.
 *
 * <p>The load-bearing idea is that a *state* difference and an *action* difference
 * are resolved together: a legacy applied-filter chip navigates an href and a
 * structured one subtracts a filter query, and the component rendering the chip
 * does not learn which. So there is nothing left to reach into, and a fourth
 * consumer cannot repeat the mistake.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

/**
 * The least a store has to offer to be projected: both response slots, plus the
 * three actions the two shapes disagree about. Satisfied structurally by the
 * React SDK's `SearchStoreValue` and by the vanilla `SearchController`'s state
 * merged with its methods — the action return type is `unknown` precisely so a
 * `void`-returning and a `Promise<void>`-returning store both fit.
 */
export interface ProjectableSearchStore {
  data: TurSearchResponse | null;
  structured: TurStructuredSearchResponse | null;
  navigate: (href: string) => unknown;
  toggleFacet: (item: TurFacetToggleTarget) => unknown;
  applySpellCheck: (useOriginal?: boolean) => unknown;
}

/** A locale the site can be searched in. Both shapes agree on `locale`. */
export interface SearchLocaleView {
  locale: string;
}

/**
 * Spell-check as state plus intent. Legacy carries hrefs and structured carries
 * text, so the two actions are resolved here rather than at the call site.
 */
export interface SearchSpellCheckView {
  correctedText: boolean;
  usingCorrectedText: boolean;
  originalText: string;
  correctedTextValue: string;
  /** Re-run the search using the correction. */
  applyCorrection: () => void;
  /** Re-run the search forcing the original term (the structured form of `nfpr=1`). */
  applyOriginal: () => void;
}

/** One chip in the "Applied filters" row. */
export interface AppliedFilterView {
  /** Stable key for list rendering. */
  key: string;
  /** What to render — the facet item's label, falling back to the raw value. */
  label: string;
  /** Drop this filter and re-search. */
  remove: () => void;
}

export interface SearchView {
  /** Which response shape backs this view. `none` means nothing has arrived yet. */
  source: "structured" | "legacy" | "none";
  /** True once a response of either shape has arrived — a page's render gate. */
  ready: boolean;
  /**
   * The site's icon as an Iconify name, or `null` when the site configured none
   * and the caller should fall back to its own logo. Both shapes carry it since
   * T879 — legacy on `widget.icon`, structured at the top level.
   */
  siteIcon: string | null;
  locales: SearchLocaleView[];
  /**
   * Result counts, paging and default field names. Present and identically shaped
   * in both responses — the structured mapper carries the legacy bean straight
   * across.
   */
  queryContext: TurQueryContext | null;
  spellCheck: SearchSpellCheckView | null;
  appliedFilters: AppliedFilterView[];
  /**
   * Whether a facet field multi-selects (`facetItemType: OR`) and should therefore
   * draw checkboxes. Reading this from the legacy response only is what made every
   * facet look single-select POST-native.
   */
  multiSelectFacetItems: boolean;
}

const EMPTY_SPELL_CHECK_TEXT = "";

/** `field:value` → the facet item that produced it, for labels and actions. */
function facetItemIndex(facets: TurStructuredFacet[] | undefined) {
  const index = new Map<string, { label: string; field: string }>();
  for (const group of facets ?? []) {
    for (const item of group.items ?? []) {
      if (item.filterQuery && !index.has(item.filterQuery)) {
        index.set(item.filterQuery, { label: item.label || item.value, field: item.field });
      }
    }
  }
  return index;
}

/**
 * A facet is rendered only up to its configured item limit, so a filter query can
 * legitimately be applied without appearing in the rail. Splitting on the first
 * colon is safe here because only the *field* prefix is wanted — the value may
 * itself contain colons and is kept whole.
 */
function fieldOf(filterQuery: string): string {
  const colon = filterQuery.indexOf(":");
  return colon < 0 ? filterQuery : filterQuery.slice(0, colon);
}

function valueOf(filterQuery: string): string {
  const colon = filterQuery.indexOf(":");
  return colon < 0 ? filterQuery : filterQuery.slice(colon + 1);
}

function structuredView(store: ProjectableSearchStore): SearchView {
  const structured = store.structured!;
  const items = facetItemIndex(structured.facets);
  for (const [fq, meta] of facetItemIndex(structured.secondaryFacets)) {
    if (!items.has(fq)) items.set(fq, meta);
  }

  const spellCheck = structured.spellCheck;
  const facetItemType =
    structured.queryContext?.facetItemType ?? structured.request?.fqItemOperator ?? "";

  return {
    source: "structured",
    ready: true,
    // T879 — the structured response carries the icon on its own now.
    siteIcon: structured.icon ?? null,
    locales: structured.locales ?? [],
    queryContext: structured.queryContext ?? null,
    spellCheck: spellCheck?.correctedText
      ? {
          correctedText: true,
          usingCorrectedText: spellCheck.usingCorrectedText,
          originalText: spellCheck.originalText ?? EMPTY_SPELL_CHECK_TEXT,
          correctedTextValue: spellCheck.correctedTextValue ?? EMPTY_SPELL_CHECK_TEXT,
          applyCorrection: () => void store.applySpellCheck(false),
          applyOriginal: () => void store.applySpellCheck(true),
        }
      : null,
    appliedFilters: (structured.selectedFilterQueries ?? []).map((filterQuery) => {
      const item = items.get(filterQuery);
      return {
        key: filterQuery,
        label: item?.label ?? valueOf(filterQuery),
        remove: () =>
          void store.toggleFacet({
            field: item?.field ?? fieldOf(filterQuery),
            filterQuery,
            action: "REMOVE",
          }),
      };
    }),
    multiSelectFacetItems: facetItemType.toUpperCase() === "OR",
  };
}

function legacyView(store: ProjectableSearchStore): SearchView {
  const data = store.data!;
  const widget = data.widget;
  const spellCheck = widget?.spellCheck;

  return {
    source: "legacy",
    ready: true,
    siteIcon: widget?.icon ?? null,
    locales: widget?.locales ?? [],
    queryContext: data.queryContext ?? null,
    spellCheck: spellCheck?.correctedText
      ? {
          correctedText: true,
          usingCorrectedText: spellCheck.usingCorrectedText,
          originalText: spellCheck.original?.text ?? EMPTY_SPELL_CHECK_TEXT,
          correctedTextValue: spellCheck.corrected?.text ?? EMPTY_SPELL_CHECK_TEXT,
          applyCorrection: () => void store.navigate(spellCheck.corrected.link),
          applyOriginal: () => void store.navigate(spellCheck.original.link),
        }
      : null,
    appliedFilters: (widget?.facetToRemove?.facets ?? []).map((facet) => ({
      key: `${facet.label}-${facet.link}`,
      label: facet.label,
      remove: () => void store.navigate(facet.link),
    })),
    multiSelectFacetItems: (data.queryContext?.facetItemType ?? "").toUpperCase() === "OR",
  };
}

const NOT_READY: SearchView = {
  source: "none",
  ready: false,
  siteIcon: null,
  locales: [],
  queryContext: null,
  spellCheck: null,
  appliedFilters: [],
  multiSelectFacetItems: false,
};

/**
 * Projects whichever response the store holds onto one view model. Structured
 * wins when both are present, mirroring `useTuringFacets`' `structuredGroups ??
 * legacyGroups`.
 */
export function toSearchView(store: ProjectableSearchStore): SearchView {
  if (store.structured) return structuredView(store);
  if (store.data) return legacyView(store);
  return NOT_READY;
}
