import { useMemo } from "react";
import type {
  SearchStoreValue,
  TurQueryContext,
  TurStructuredFacet,
} from "@viglet/turing-react-sdk";
import { useSearchStore } from "@viglet/turing-react-sdk";

/**
 * T865-T867 / §LXVII — the one place `/sn/{siteName}` reads a search response.
 *
 * <p>T710 made `postNative: true` the provider default, and POST-native the
 * provider sets `store.data = null` and fills `store.structured` instead. Three
 * consumers in this app kept reading `store.data`: the page gated its whole
 * render on it (so it showed `SearchError` on every successful search), the facet
 * rail lost its "Applied filters" row, and every facet rendered as single-select.
 *
 * <p>The bug was one mistake repeated three times, so the fix is not three
 * `structured ?? data` expressions sprinkled across three components — it is this
 * module. Every field a component needs is resolved here once, from whichever
 * shape arrived, and the *actions* are resolved with it: a legacy applied-filter
 * chip navigates an href, a structured one subtracts a filter query, and the
 * component that renders the chip does not need to know which. Adding a consumer
 * therefore cannot repeat the mistake — there is nothing to reach into.
 */

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
  /** Stable React key. */
  key: string;
  /** What to render — the facet item's label, falling back to the raw value. */
  label: string;
  /** Drop this filter and re-search. */
  remove: () => void;
}

export interface SearchView {
  /** Which response shape backs this view. `none` means nothing has arrived yet. */
  source: "structured" | "legacy" | "none";
  /** True once a response of either shape has arrived — the page's render gate. */
  ready: boolean;
  /**
   * The site's icon as an Iconify name, or `null` when the site configured none
   * and the page should fall back to the Turing logo. Both shapes carry it since
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

function structuredView(store: SearchStoreValue): SearchView {
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
          applyCorrection: () => store.applySpellCheck(false),
          applyOriginal: () => store.applySpellCheck(true),
        }
      : null,
    appliedFilters: (structured.selectedFilterQueries ?? []).map((filterQuery) => {
      const item = items.get(filterQuery);
      return {
        key: filterQuery,
        label: item?.label ?? valueOf(filterQuery),
        remove: () =>
          store.toggleFacet({
            field: item?.field ?? fieldOf(filterQuery),
            filterQuery,
            action: "REMOVE",
          }),
      };
    }),
    multiSelectFacetItems: facetItemType.toUpperCase() === "OR",
  };
}

function legacyView(store: SearchStoreValue): SearchView {
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
          applyCorrection: () => store.navigate(spellCheck.corrected.link),
          applyOriginal: () => store.navigate(spellCheck.original.link),
        }
      : null,
    appliedFilters: (widget?.facetToRemove?.facets ?? []).map((facet) => ({
      key: `${facet.label}-${facet.link}`,
      label: facet.label,
      remove: () => store.navigate(facet.link),
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
 *
 * <p>Exported as a pure function so §LXVII.4's test can assert it against a real
 * `STRUCTURED` payload without React.
 */
export function toSearchView(store: SearchStoreValue): SearchView {
  if (store.structured) return structuredView(store);
  if (store.data) return legacyView(store);
  return NOT_READY;
}

export function useSearchView(): SearchView {
  const store = useSearchStore();
  return useMemo(() => toSearchView(store), [store]);
}
