import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import axios from "axios";
import { MemoryRouter, Route, Routes, useSearchParams } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { TurSearchResponse, TurStructuredSearchResponse } from "@viglet/turing-react-sdk";
import { TuringProvider } from "@viglet/turing-react-sdk";
import { SearchContent } from "../components/search-content";
import SearchPage from "../pages/search.page";

/**
 * T868 / §LXVII.4 — the search page rendered against the **real** provider.
 *
 * <p>The T835 suite next door mocks `useSearchStore` wholesale and hands
 * `SearchContent` a `data` object. That fixture stopped being producible when T710
 * made `postNative: true` the default: POST-native the provider sets `data = null`
 * and fills `structured`. So the suite stayed green while `/sn/{siteName}` rendered
 * its error state on every single search (T865), lost its applied-filters row
 * (T866) and served dead facet chips (T867) — three bugs no mocked-store test could
 * ever see.
 *
 * <p>This file mounts the real `TuringProvider` and stubs the **network** instead:
 * the shape under test is the shape the server ships. The boundary is `axios`, not
 * `fetch` — the React SDK deliberately stays on the host's axios instance so its
 * interceptors keep applying (the vanilla SDK is the `fetch`-based one).
 */

const SITE = "wknd";
const SECTION_FQ = "section:RAG & Chat";
const AUTHOR_FQ = "author:Ada";

/**
 * A `STRUCTURED` response as the T708 mapper actually emits it: no `widget`, no
 * href anywhere, `selectedFilterQueries` at the top level, and (T869) `selected` +
 * `action` on the document's chips.
 */
function structuredResponse(
  overrides: Partial<TurStructuredSearchResponse> = {},
): TurStructuredSearchResponse {
  return {
    request: { q: "*", page: 1, rows: 10, fq: [SECTION_FQ], fqItemOperator: "OR" },
    queryContext: {
      count: 1,
      defaultFields: {
        date: "publication_date",
        description: "abstract",
        image: "image",
        text: "text",
        title: "title",
        url: "url",
      },
      index: SITE,
      limit: 10,
      offset: 0,
      page: 1,
      pageCount: 1,
      pageEnd: 1,
      pageStart: 1,
      query: { queryString: "*", sort: "relevance", locale: "en_US" },
      responseTime: 4,
      facetType: "OR",
      facetItemType: "OR",
    },
    results: {
      document: [
        {
          elevate: false,
          fields: { id: "doc-1", title: "Grounding a chat on your own content", url: "/a" },
          metadata: [
            {
              field: "author",
              value: "Ada",
              filterQuery: AUTHOR_FQ,
              text: "Ada",
              selected: false,
              action: "ADD",
            },
          ],
        },
      ],
    },
    facets: [
      {
        name: "section",
        label: "Section",
        multiValued: true,
        selectedFilterQueries: [SECTION_FQ],
        clearFilterQueries: [SECTION_FQ],
        items: [
          {
            field: "section",
            value: "RAG & Chat",
            filterQuery: SECTION_FQ,
            label: "RAG & Chat",
            count: 1,
            selected: true,
            action: "REMOVE",
          },
          {
            field: "section",
            value: "Deploy",
            filterQuery: "section:Deploy",
            label: "Deploy",
            count: 3,
            selected: false,
            action: "ADD",
          },
        ],
      },
    ],
    selectedFilterQueries: [SECTION_FQ],
    locales: [{ locale: "en_US", selected: true }],
    ...overrides,
  };
}

const mockAxios = vi.mocked(axios, true);

function stubNetwork(response: TurStructuredSearchResponse = structuredResponse()) {
  mockAxios.post.mockImplementation((url: string) => {
    if (url.includes("/search/query")) return Promise.resolve({ data: response });
    return Promise.resolve({ data: {} });
  });
  mockAxios.get.mockImplementation((url: string) => {
    if (url.includes("/chat/enabled")) return Promise.resolve({ data: { enabled: false } });
    if (url.includes("/search/sort-options")) {
      return Promise.resolve({ data: [{ value: "relevance", label: "Relevance" }] });
    }
    if (url.includes("/chat")) return Promise.resolve({ data: null });
    return Promise.resolve({ data: [] });
  });
}

/** The real route, the real provider, the real store — only the network is stubbed. */
function renderSearchPage(url = `/sn/${SITE}`) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/sn/:siteName" element={<SearchPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("/sn/{siteName} against the real POST-native provider (T868)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    stubNetwork();
  });

  it("posts a STRUCTURED search and renders results instead of the error state", async () => {
    renderSearchPage();

    expect(
      await screen.findByText("Grounding a chat on your own content"),
    ).toBeInTheDocument();
    // T865 — the regression that shipped: `!store.data` sent every search here.
    expect(screen.queryByText("search.noContent")).not.toBeInTheDocument();

    const [url, body] = mockAxios.post.mock.calls[0];
    expect(url).toBe(`/sn/${SITE}/search/query`);
    expect(body).toMatchObject({ responseFormat: "STRUCTURED" });
  });

  it("renders the applied-filters row from selectedFilterQueries (T866)", async () => {
    renderSearchPage();

    expect(await screen.findByText("search.appliedFilters")).toBeInTheDocument();
    // Labelled from the matching facet item, not from the raw `field:value`.
    expect(screen.getByRole("button", { name: /RAG & Chat/ })).toBeInTheDocument();
    expect(screen.getByText("search.clearAll")).toBeInTheDocument();
  });

  it("removing an applied filter drops just that filter query (T866)", async () => {
    renderSearchPage(`/sn/${SITE}?fq[]=${encodeURIComponent(SECTION_FQ)}`);
    await screen.findByText("search.appliedFilters");

    const chip = screen
      .getAllByRole("button", { name: /RAG & Chat/ })
      .find((el) => el.closest("aside"))!;
    fireEvent.click(chip);

    await waitFor(() => {
      const last = mockAxios.post.mock.calls.at(-1)![1] as { fq?: string[] };
      expect(last.fq ?? []).not.toContain(SECTION_FQ);
    });
  });

  it("draws facet checkboxes when the site multi-selects facet items (T866)", async () => {
    renderSearchPage();
    fireEvent.click(await screen.findByText("Section"));

    // `facetItemType: OR` used to be read off `store.data.queryContext`, so every
    // facet rendered as single-select POST-native.
    expect(await screen.findByRole("checkbox", { name: "Deploy" })).toBeInTheDocument();
  });

  it("does not draw checkboxes when the site single-selects (T866)", async () => {
    const response = structuredResponse();
    response.queryContext.facetItemType = "AND";
    stubNetwork(response);

    renderSearchPage();
    fireEvent.click(await screen.findByText("Section"));
    await screen.findByText("Deploy");

    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("clicking a result's facet chip filters the search again (T867)", async () => {
    renderSearchPage();
    await screen.findByText("Grounding a chat on your own content");

    fireEvent.click(screen.getByRole("button", { name: "Ada" }));

    await waitFor(() => {
      const last = mockAxios.post.mock.calls.at(-1)![1] as { fq?: string[] };
      expect(last.fq).toContain(AUTHOR_FQ);
    });
  });

  it("an already-applied chip removes its filter rather than re-adding it (T869)", async () => {
    const response = structuredResponse();
    response.results!.document[0].metadata[0].selected = true;
    response.results!.document[0].metadata[0].action = "REMOVE";
    response.request.fq = [SECTION_FQ, AUTHOR_FQ];
    response.selectedFilterQueries = [SECTION_FQ, AUTHOR_FQ];
    stubNetwork(response);

    renderSearchPage(
      `/sn/${SITE}?fq[]=${encodeURIComponent(SECTION_FQ)}&fq[]=${encodeURIComponent(AUTHOR_FQ)}`,
    );
    await screen.findByText("Grounding a chat on your own content");

    // `author:Ada` is applied, so the sidebar shows a chip for it too — this is
    // the one on the result card.
    const chip = screen
      .getAllByRole("button", { name: "Ada" })
      .find((el) => el.closest("article"))!;
    expect(chip).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(chip);

    await waitFor(() => {
      const last = mockAxios.post.mock.calls.at(-1)![1] as { fq?: string[] };
      expect(last.fq ?? []).not.toContain(AUTHOR_FQ);
    });
  });

  it("still renders the error state when the search actually fails", async () => {
    mockAxios.post.mockRejectedValue(new Error("boom"));

    renderSearchPage();

    expect(await screen.findByText("search.noContent")).toBeInTheDocument();
  });
});

// ── The legacy transport, still supported ──

/**
 * T865 kept `store.data` as the fallback rather than deleting it, so `postNative:
 * false` has to keep working. Same page, same components, the other shape.
 */
function legacyResponse(): TurSearchResponse {
  return {
    queryContext: {
      count: 1,
      defaultFields: {
        date: "publication_date",
        description: "abstract",
        image: "image",
        text: "text",
        title: "title",
        url: "url",
      },
      index: SITE,
      limit: 10,
      offset: 0,
      page: 1,
      pageCount: 1,
      pageEnd: 1,
      pageStart: 1,
      query: { queryString: "*", sort: "relevance", locale: "en_US" },
      responseTime: 4,
      facetType: "OR",
      facetItemType: "OR",
    },
    results: {
      document: [
        {
          elevate: false,
          fields: { id: "doc-1", title: "Grounding a chat on your own content", url: "/a" },
          metadata: [{ href: `/api/sn/${SITE}/search?q=*&fq[]=author:Ada`, text: "Ada" }],
        },
      ],
    },
    widget: {
      icon: "tabler:book",
      facet: [
        {
          name: "section",
          label: { lang: "en_US", text: "Section" },
          description: "",
          type: "OR",
          multivalued: true,
          cleanUpLink: `/api/sn/${SITE}/search?q=*`,
          facets: [
            { count: 1, label: "RAG & Chat", link: "/l1", selected: true },
            { count: 3, label: "Deploy", link: "/l2", selected: false },
          ],
        },
      ],
      facetToRemove: {
        name: "section",
        label: { lang: "en_US", text: "Section" },
        description: "",
        type: "OR",
        multivalued: true,
        cleanUpLink: "",
        facets: [{ count: 1, label: "RAG & Chat", link: "/remove-rag", selected: true }],
      },
      similar: [],
      spellCheck: {
        correctedText: false,
        usingCorrectedText: false,
        original: { link: "", text: "" },
        corrected: { link: "", text: "" },
      },
      locales: [{ locale: "en_US", link: "/en" }],
      cleanUpFacets: `/api/sn/${SITE}/search?q=*`,
    },
  };
}

function LegacySearch() {
  const [searchParams, setSearchParams] = useSearchParams();
  return (
    <TuringProvider
      config={{ site: SITE, sort: "relevance" }}
      urlSync={{
        searchParams,
        setSearchParams: (s) => setSearchParams(s),
        postNative: false,
      }}
    >
      <SearchContent siteName={SITE} />
    </TuringProvider>
  );
}

describe("/sn/{siteName} with the legacy transport (postNative: false)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAxios.post.mockResolvedValue({ data: {} });
    mockAxios.get.mockImplementation((url: string) => {
      if (url.includes("/chat/enabled")) return Promise.resolve({ data: { enabled: false } });
      if (url.includes("/search/sort-options")) return Promise.resolve({ data: [] });
      if (url.includes("/chat")) return Promise.resolve({ data: null });
      if (url.includes("/search?")) return Promise.resolve({ data: legacyResponse() });
      return Promise.resolve({ data: [] });
    });
  });

  it("renders results, the applied-filters row and a navigable chip from `data`", async () => {
    render(
      <MemoryRouter initialEntries={[`/sn/${SITE}`]}>
        <LegacySearch />
      </MemoryRouter>,
    );

    expect(
      await screen.findByText("Grounding a chat on your own content"),
    ).toBeInTheDocument();
    expect(screen.getByText("search.appliedFilters")).toBeInTheDocument();
    // The legacy chip is still an href, and still navigates.
    expect(screen.getByTitle("Ada")).toBeInTheDocument();
  });
});
