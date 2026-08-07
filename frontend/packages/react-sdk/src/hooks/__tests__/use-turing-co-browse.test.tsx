import { renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { useCoBrowseSearch } from "../use-turing-co-browse";
import type { SearchStoreValue } from "../../core/context";
import type { TurSearchResponse, TurStructuredSearchResponse } from "../../core/types";

/**
 * T884 / §LXX.6 — the agent can reach a secondary facet.
 *
 * <p>`toggle_facet` looked the target up in `structured.facets` and stopped, then
 * fell through to a legacy lookup that read `widget.facet` and was primary-only
 * too. So a field the site configured as a *secondary* facet answered
 * "Facet not found" in both transports — while the visible rail rendered it and
 * the user, who cannot tell primary from secondary, saw the agent refuse.
 */

function facetGroup(name: string, label: string, value: string) {
  return {
    name,
    multiValued: true,
    items: [
      {
        field: name,
        value,
        filterQuery: `${name}:${value}`,
        label,
        count: 3,
        selected: false,
        action: "ADD" as const,
      },
    ],
  };
}

function makeStore(overrides: Partial<SearchStoreValue>): SearchStoreValue {
  return {
    structured: null,
    data: null,
    params: {},
    toggleFacet: vi.fn(),
    navigate: vi.fn(),
    clearAllFilters: vi.fn(),
    updateParams: vi.fn(),
    setSort: vi.fn(),
    ...overrides,
  } as unknown as SearchStoreValue;
}

function toolsFor(store: SearchStoreValue) {
  return renderHook(() => useCoBrowseSearch(store)).result.current.clientTools;
}

describe("toggle_facet (T884)", () => {
  it("toggles a primary structured facet", () => {
    const store = makeStore({
      structured: {
        facets: [facetGroup("section", "RAG & Chat", "RAG & Chat")],
      } as unknown as TurStructuredSearchResponse,
    });

    expect(toolsFor(store).toggle_facet({ field: "section", value: "RAG & Chat" })).toEqual({
      success: true,
      field: "section",
      value: "RAG & Chat",
    });
    expect(store.toggleFacet).toHaveBeenCalledWith(
      expect.objectContaining({ filterQuery: "section:RAG & Chat" }),
    );
  });

  it("toggles a SECONDARY structured facet instead of reporting it missing", () => {
    const store = makeStore({
      structured: {
        facets: [facetGroup("section", "RAG & Chat", "RAG & Chat")],
        secondaryFacets: [facetGroup("author", "Ada", "Ada")],
      } as unknown as TurStructuredSearchResponse,
    });

    expect(toolsFor(store).toggle_facet({ field: "author", value: "Ada" })).toEqual({
      success: true,
      field: "author",
      value: "Ada",
    });
    expect(store.toggleFacet).toHaveBeenCalledWith(
      expect.objectContaining({ filterQuery: "author:Ada" }),
    );
  });

  it("follows a SECONDARY facet's href on the legacy transport", () => {
    const store = makeStore({
      data: {
        widget: {
          facet: [{ name: "section", facets: [{ label: "RAG & Chat", link: "/l1" }] }],
          secondaryFacet: [{ name: "author", facets: [{ label: "Ada", link: "/author-ada" }] }],
        },
      } as unknown as TurSearchResponse,
    });

    expect(toolsFor(store).toggle_facet({ field: "author", value: "Ada" })).toEqual({
      success: true,
      field: "author",
      value: "Ada",
    });
    expect(store.navigate).toHaveBeenCalledWith("/author-ada");
  });

  it("still reports a facet that is in neither rail", () => {
    const store = makeStore({
      structured: {
        facets: [facetGroup("section", "RAG & Chat", "RAG & Chat")],
      } as unknown as TurStructuredSearchResponse,
    });

    expect(toolsFor(store).toggle_facet({ field: "author", value: "Ada" })).toEqual({
      success: false,
      message: "Facet not found: author=Ada",
    });
    expect(store.toggleFacet).not.toHaveBeenCalled();
  });

  it("rejects a call with no field or value", () => {
    const store = makeStore({});

    expect(toolsFor(store).toggle_facet({ field: "author" })).toEqual({
      success: false,
      message: "field and value are required",
    });
  });
});
