import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useTuringFacets } from "../use-turing-facets";
import type { SearchStoreValue } from "../../core/context";
import type { TurSearchResponse, TurStructuredSearchResponse } from "../../core/types";

/**
 * T936 / §XLV.6 — the secondary rail reaches the hook.
 *
 * <p>`useTuringFacets` read `structured.facets` and `widget.facet` and neither
 * `secondaryFacets` nor `widget.secondaryFacet`, so a rail the site configured
 * was invisible to every SDK consumer in both transports. It now arrives as its
 * own list — never mixed into `facetGroups`, because that would change what an
 * existing consumer renders.
 */

const mockStore = vi.fn<() => SearchStoreValue>();

vi.mock("../../core/use-search-store", () => ({
  useSearchStore: () => mockStore(),
}));

function structuredGroup(name: string, label: string, selected = false) {
  return {
    name,
    multiValued: true,
    items: [
      {
        field: name,
        value: label,
        filterQuery: `${name}:${label}`,
        label,
        count: 3,
        selected,
        action: selected ? ("REMOVE" as const) : ("ADD" as const),
      },
    ],
  };
}

function legacyGroup(name: string, label: string, selected = false) {
  return {
    name,
    label: { lang: "en", text: name },
    multivalued: true,
    cleanUpLink: `/clean-${name}`,
    facets: [{ label, link: `/${name}-${label}`, count: 3, selected }],
  };
}

function makeStore(overrides: Partial<SearchStoreValue>): SearchStoreValue {
  return {
    structured: null,
    data: null,
    params: {},
    toggleFacet: vi.fn(),
    clearFacet: vi.fn(),
    navigate: vi.fn(),
    clearAllFilters: vi.fn(),
    updateParams: vi.fn(),
    setSort: vi.fn(),
    ...overrides,
  } as unknown as SearchStoreValue;
}

function render(store: SearchStoreValue) {
  mockStore.mockReturnValue(store);
  return renderHook(() => useTuringFacets()).result.current;
}

beforeEach(() => {
  mockStore.mockReset();
});

describe("useTuringFacets secondary rail (T936)", () => {
  it("exposes the structured secondary groups without mixing them into facetGroups", () => {
    const store = makeStore({
      structured: {
        facets: [structuredGroup("section", "RAG & Chat")],
        secondaryFacets: [structuredGroup("author", "Ada")],
      } as unknown as TurStructuredSearchResponse,
    });

    const { facetGroups, secondaryFacetGroups } = render(store);

    expect(facetGroups.map((g) => g.name)).toEqual(["section"]);
    expect(secondaryFacetGroups.map((g) => g.name)).toEqual(["author"]);

    secondaryFacetGroups[0].facets[0].toggle();
    expect(store.toggleFacet).toHaveBeenCalledWith(
      expect.objectContaining({ filterQuery: "author:Ada" }),
    );
  });

  it("exposes the legacy secondary groups with their hrefs", () => {
    const store = makeStore({
      data: {
        widget: {
          facet: [legacyGroup("section", "RAG & Chat")],
          secondaryFacet: [legacyGroup("author", "Ada")],
        },
      } as unknown as TurSearchResponse,
    });

    const { facetGroups, secondaryFacetGroups, rawSecondary } = render(store);

    expect(facetGroups.map((g) => g.name)).toEqual(["section"]);
    expect(secondaryFacetGroups.map((g) => g.name)).toEqual(["author"]);
    expect(rawSecondary).toHaveLength(1);

    secondaryFacetGroups[0].facets[0].toggle();
    expect(store.navigate).toHaveBeenCalledWith("/author-Ada");
  });

  it("reports hasSelected when only a secondary facet is selected", () => {
    const store = makeStore({
      structured: {
        facets: [structuredGroup("section", "RAG & Chat")],
        secondaryFacets: [structuredGroup("author", "Ada", true)],
      } as unknown as TurStructuredSearchResponse,
    });

    const { facetGroups, hasSelected } = render(store);

    expect(facetGroups.some((g) => g.hasSelected)).toBe(false);
    expect(hasSelected).toBe(true);
  });

  it("stays on the structured arm when only the secondary rail is configured", () => {
    const store = makeStore({
      structured: {
        facets: [],
        secondaryFacets: [structuredGroup("author", "Ada")],
      } as unknown as TurStructuredSearchResponse,
      data: {
        widget: { facet: [legacyGroup("section", "RAG & Chat")] },
      } as unknown as TurSearchResponse,
    });

    const { facetGroups, secondaryFacetGroups } = render(store);

    expect(facetGroups).toEqual([]);
    expect(secondaryFacetGroups.map((g) => g.name)).toEqual(["author"]);
  });

  it("is empty on a site that configures no secondary rail", () => {
    const store = makeStore({
      structured: {
        facets: [structuredGroup("section", "RAG & Chat")],
      } as unknown as TurStructuredSearchResponse,
    });

    expect(render(store).secondaryFacetGroups).toEqual([]);
  });
});
