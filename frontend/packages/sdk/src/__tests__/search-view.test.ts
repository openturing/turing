import { describe, expect, it, vi } from "vitest";
import { toSearchView } from "../search-view";
import type { ProjectableSearchStore } from "../search-view";
import type { TurSearchResponse, TurStructuredSearchResponse } from "../types";

/**
 * T881 / §LXX.3 — the two-shape projection, asserted without React.
 *
 * <p>It moved here from `turing-app` because it answers a question every consumer
 * of a search response has, and it was reachable from exactly one of them. The
 * cases below are the ones §LXVII and T879 were: which shape wins, whether the
 * *action* differences are resolved along with the state ones, and the icon —
 * the field whose absence is invisible in review because falling back to a logo
 * looks deliberate.
 */

function store(overrides: Partial<ProjectableSearchStore>): ProjectableSearchStore {
  return {
    data: null,
    structured: null,
    navigate: vi.fn(),
    toggleFacet: vi.fn(),
    applySpellCheck: vi.fn(),
    ...overrides,
  };
}

function structured(
  overrides: Partial<TurStructuredSearchResponse> = {},
): TurStructuredSearchResponse {
  return {
    request: { q: "*", page: 1 },
    queryContext: { facetItemType: "OR" } as TurStructuredSearchResponse["queryContext"],
    ...overrides,
  };
}

describe("toSearchView (T881)", () => {
  it("reports not-ready before any response has arrived", () => {
    const view = toSearchView(store({}));

    expect(view.source).toBe("none");
    expect(view.ready).toBe(false);
    expect(view.siteIcon).toBeNull();
  });

  // ── the site icon (T879 / §LXX.1) ──

  it("carries the site icon off the structured response", () => {
    expect(
      toSearchView(store({ structured: structured({ icon: "tabler:building-store" }) })).siteIcon,
    ).toBe("tabler:building-store");
  });

  it("leaves the icon null when the site configured none, so the logo shows", () => {
    expect(toSearchView(store({ structured: structured() })).siteIcon).toBeNull();
  });

  it("still reads the icon off the legacy widget", () => {
    const data = {
      widget: { icon: "tabler:book" },
      queryContext: { facetItemType: "OR" },
    } as unknown as TurSearchResponse;

    expect(toSearchView(store({ data })).siteIcon).toBe("tabler:book");
  });

  // ── which shape wins (§LXVII) ──

  it("prefers the structured response when both slots are filled", () => {
    const data = { widget: { icon: "tabler:book" } } as unknown as TurSearchResponse;

    expect(toSearchView(store({ data, structured: structured() })).source).toBe("structured");
  });

  // ── the action difference, resolved with the state one ──

  it("removes a structured applied filter by subtracting its filter query", () => {
    const toggleFacet = vi.fn();
    const view = toSearchView(
      store({
        toggleFacet,
        structured: structured({
          selectedFilterQueries: ["section:RAG & Chat"],
          facets: [
            {
              name: "section",
              multiValued: true,
              items: [
                {
                  field: "section",
                  value: "RAG & Chat",
                  filterQuery: "section:RAG & Chat",
                  label: "RAG & Chat",
                  count: 1,
                  selected: true,
                  action: "REMOVE",
                },
              ],
            },
          ],
        }),
      }),
    );

    expect(view.appliedFilters).toHaveLength(1);
    expect(view.appliedFilters[0].label).toBe("RAG & Chat");
    view.appliedFilters[0].remove();

    expect(toggleFacet).toHaveBeenCalledWith({
      field: "section",
      filterQuery: "section:RAG & Chat",
      action: "REMOVE",
    });
  });

  /**
   * A facet is only rendered up to its item limit, so an applied filter can be
   * absent from the rail. It still has to produce a usable chip.
   */
  it("labels an applied filter that is not in the rail from its own value", () => {
    const toggleFacet = vi.fn();
    const view = toSearchView(
      store({ toggleFacet, structured: structured({ selectedFilterQueries: ["author:Ada"] }) }),
    );
    view.appliedFilters[0].remove();

    expect(view.appliedFilters[0].label).toBe("Ada");
    expect(toggleFacet).toHaveBeenCalledWith({
      field: "author",
      filterQuery: "author:Ada",
      action: "REMOVE",
    });
  });

  it("removes a legacy applied filter by navigating its href", () => {
    const navigate = vi.fn();
    const data = {
      queryContext: { facetItemType: "AND" },
      widget: {
        facetToRemove: { facets: [{ label: "RAG & Chat", link: "/remove-rag" }] },
      },
    } as unknown as TurSearchResponse;

    const view = toSearchView(store({ navigate, data }));
    view.appliedFilters[0].remove();

    expect(navigate).toHaveBeenCalledWith("/remove-rag");
    // The legacy read of `facetItemType` is the one the rail single-selected on.
    expect(view.multiSelectFacetItems).toBe(false);
  });

  it("reads multi-select off the structured request when the context omits it", () => {
    const view = toSearchView(
      store({
        structured: {
          request: { q: "*", page: 1, fqItemOperator: "OR" },
          queryContext: {} as TurStructuredSearchResponse["queryContext"],
        },
      }),
    );

    expect(view.multiSelectFacetItems).toBe(true);
  });

  // ── spell-check: same offer, two ways to accept it ──

  it("applies a structured correction without a URL", () => {
    const applySpellCheck = vi.fn();
    const view = toSearchView(
      store({
        applySpellCheck,
        structured: structured({
          spellCheck: {
            correctedText: true,
            usingCorrectedText: false,
            originalText: "serch",
            correctedTextValue: "search",
          },
        }),
      }),
    );
    view.spellCheck!.applyCorrection();
    view.spellCheck!.applyOriginal();

    expect(view.spellCheck!.correctedTextValue).toBe("search");
    expect(applySpellCheck).toHaveBeenNthCalledWith(1, false);
    expect(applySpellCheck).toHaveBeenNthCalledWith(2, true);
  });

  it("applies a legacy correction by navigating the offered links", () => {
    const navigate = vi.fn();
    const data = {
      queryContext: {},
      widget: {
        spellCheck: {
          correctedText: true,
          usingCorrectedText: false,
          original: { text: "serch", link: "/orig" },
          corrected: { text: "search", link: "/corrected" },
        },
      },
    } as unknown as TurSearchResponse;

    const view = toSearchView(store({ navigate, data }));
    view.spellCheck!.applyCorrection();
    view.spellCheck!.applyOriginal();

    expect(navigate).toHaveBeenNthCalledWith(1, "/corrected");
    expect(navigate).toHaveBeenNthCalledWith(2, "/orig");
  });

  it("offers no spell-check when the server found nothing to correct", () => {
    expect(toSearchView(store({ structured: structured() })).spellCheck).toBeNull();
  });
});
