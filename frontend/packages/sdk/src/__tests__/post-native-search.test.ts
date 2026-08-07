import { describe, expect, it, vi } from "vitest";
import { applyFacetAction, postSearch, postStructuredSearch } from "../api";
import {
  createSearchController,
  requestToSearchParams,
  searchParamsToRequest,
} from "../controllers/search";
import type { TuringClient } from "../client";
import type {
  TurSearchResponse,
  TurStructuredFacetItem,
  TurStructuredSearchResponse,
} from "../types";

/**
 * T709 / §XLV — the vanilla SDK goes POST-native.
 *
 * The bug class this closes: a facet value containing `&` (or `= + # %`, or a
 * space) used to make four query-string hops — server builds an href, the SDK
 * parses it with `URLSearchParams`, re-serializes it, the server decodes it.
 * A JSON body has no query-string grammar, so the value simply cannot be
 * re-read as structure.
 */
describe("POST-native search (T709)", () => {
  const RESERVED = "section:RAG & Chat";

  function structuredResponse(
    overrides: Partial<TurStructuredSearchResponse> = {},
  ): TurStructuredSearchResponse {
    return {
      request: { q: "*", page: 1, fq: [], locale: "en_US", sort: "relevance" },
      queryContext: {
        count: 3,
        defaultFields: {
          url: "url",
          title: "title",
          description: "abstract",
          date: "date",
          image: "image",
          text: "text",
        },
      } as TurStructuredSearchResponse["queryContext"],
      results: { document: [] },
      ...overrides,
    };
  }

  /**
   * Echoes the received body back as `request`, which is what the server does —
   * a static echo would silently undo every toggle the controller makes.
   */
  function makeClient(response = structuredResponse()) {
    const get = vi.fn(() => Promise.resolve(null));
    const post = vi.fn((url: string, body: unknown) =>
      url.includes("/search/query")
        ? Promise.resolve({
            ...response,
            request: { ...response.request, ...(body as object) },
          })
        : Promise.resolve(undefined),
    );
    return { get, post } as unknown as TuringClient;
  }

  const postCalls = (c: TuringClient) => (c.post as ReturnType<typeof vi.fn>).mock.calls;

  // ── api-level ──

  it("posts the request as a JSON body to /search/query", async () => {
    const c = makeClient();
    await postSearch(c, "wknd", { q: "hello", fq: [RESERVED] });

    const [url, body] = postCalls(c)[0];
    expect(url).toBe("/sn/wknd/search/query");
    expect(body).toMatchObject({ q: "hello", fq: [RESERVED] });
  });

  /**
   * The payoff of the T707 finding: `/api/sn/**` is CSRF-exempt server-side, so
   * the SDK must not pay for a `/csrf` priming round-trip on every search.
   */
  it("skips CSRF priming (the endpoint is CSRF-exempt)", async () => {
    const c = makeClient();
    await postSearch(c, "wknd", { q: "hello" });

    expect(postCalls(c)[0][2]).toMatchObject({ csrf: false });
  });

  it("forces responseFormat STRUCTURED on the structured helper", async () => {
    const c = makeClient();
    await postStructuredSearch(c, "wknd", { q: "hello" });

    expect(postCalls(c)[0][1]).toMatchObject({ responseFormat: "STRUCTURED" });
  });

  /** The reserved characters travel verbatim — no encoding hop exists. */
  it("carries reserved characters in a filter value untouched", async () => {
    const c = makeClient();
    const fq = [RESERVED, "path:a=b+c#d%e", "title:Deploy & Operate"];
    await postSearch(c, "wknd", { fq });

    expect(postCalls(c)[0][1]).toMatchObject({ fq });
  });

  // ── applyFacetAction ──

  const facetItem = (
    action: TurStructuredFacetItem["action"],
    filterQuery = RESERVED,
  ): TurStructuredFacetItem => ({
    field: "section",
    value: filterQuery.split(":")[1],
    filterQuery,
    label: "label",
    count: 1,
    selected: action === "REMOVE",
    action,
  });

  it("ADD appends the filter query", () => {
    expect(applyFacetAction(["type:page"], facetItem("ADD"))).toEqual([
      "type:page",
      RESERVED,
    ]);
  });

  it("ADD is idempotent", () => {
    expect(applyFacetAction([RESERVED], facetItem("ADD"))).toEqual([RESERVED]);
  });

  it("REMOVE drops only that filter query", () => {
    expect(applyFacetAction([RESERVED, "type:page"], facetItem("REMOVE"))).toEqual([
      "type:page",
    ]);
  });

  /** A single-select (AND facet-item mode) field swaps its own value. */
  it("REPLACE drops the field's other selections", () => {
    const result = applyFacetAction(
      ["section:Old", "type:page"],
      facetItem("REPLACE", "section:New"),
    );
    expect(result).toEqual(["type:page", "section:New"]);
  });

  it("does not mutate the input list", () => {
    const input = ["type:page"];
    applyFacetAction(input, facetItem("ADD"));
    expect(input).toEqual(["type:page"]);
  });

  // ── SearchParams <-> TurSearchRequest bridges (shared with the React SDK) ──

  it("maps legacy GET params onto the POST body", () => {
    const request = searchParamsToRequest({
      q: "boots",
      p: "3",
      _setlocale: "pt_BR",
      sort: "newest",
      fq: [RESERVED],
      tr: ["rule-a"],
      rows: "20",
      group: "type",
      nfpr: "1",
    });

    expect(request).toMatchObject({
      q: "boots",
      page: 3,
      locale: "pt_BR",
      sort: "newest",
      fq: [RESERVED],
      targetingRules: ["rule-a"],
      rows: 20,
      group: "type",
      autoCorrectionDisabled: true,
    });
  });

  it("round-trips through the request and back", () => {
    const params = {
      q: "boots",
      p: "3",
      _setlocale: "pt_BR",
      sort: "newest",
      fq: [RESERVED],
      rows: "20",
    };

    expect(requestToSearchParams(searchParamsToRequest(params))).toMatchObject(params);
  });

  it("defaults an empty param set the same way the GET path does", () => {
    const request = searchParamsToRequest({ q: "" });
    expect(request.q).toBe("*");
    expect(request.page).toBe(1);
    expect(request.fq).toEqual([]);
    expect(request.autoCorrectionDisabled).toBeUndefined();
  });

  it("maps nfpr=0 to autoCorrectionDisabled=false, not undefined", () => {
    expect(searchParamsToRequest({ q: "*", nfpr: "0" }).autoCorrectionDisabled).toBe(false);
    expect(requestToSearchParams({ autoCorrectionDisabled: false }).nfpr).toBe("0");
  });

  // ── controller ──

  it("drives search over POST by default", async () => {
    const c = makeClient();
    const search = createSearchController(c, { site: "wknd", locale: "en_US" }, undefined, {
      chat: false,
    });
    await search.searchQuery("boots");

    expect(postCalls(c)[0][0]).toBe("/sn/wknd/search/query");
    expect((c.get as ReturnType<typeof vi.fn>).mock.calls).toHaveLength(0);
    expect(search.getState().structured).not.toBeNull();
    expect(search.getState().data).toBeNull();
  });

  it("adopts the server's echoed request so the next toggle starts from truth", async () => {
    // The server resolves defaults the client did not send (here: a site sort of
    // `newest` and a targeting-rule-derived filter). The controller must adopt
    // that, not keep what it guessed.
    const resolved = structuredResponse({
      request: { q: "boots", page: 1, fq: [RESERVED], locale: "en_US", sort: "newest" },
    });
    const c = {
      get: vi.fn(() => Promise.resolve(null)),
      post: vi.fn(() => Promise.resolve(resolved)),
    } as unknown as TuringClient;
    const search = createSearchController(c, { site: "wknd", locale: "en_US" }, undefined, {
      chat: false,
    });
    await search.searchQuery("boots");

    expect(search.getState().request.sort).toBe("newest");
    expect(search.getState().request.fq).toEqual([RESERVED]);
    // `params` stays in sync for consumers that still read it.
    expect(search.getState().params.sort).toBe("newest");
    expect(search.getState().params.fq).toEqual([RESERVED]);
  });

  it("toggleFacet sends the value in the body and resets to page 1", async () => {
    const c = makeClient();
    const search = createSearchController(c, { site: "wknd", locale: "en_US" }, undefined, {
      chat: false,
    });
    await search.goToPage(4);
    await search.toggleFacet(facetItem("ADD"));

    const body = postCalls(c).at(-1)![1] as Record<string, unknown>;
    expect(body.fq).toEqual([RESERVED]);
    expect(body.page).toBe(1);
  });

  it("clearFacet subtracts that facet's filter queries", async () => {
    const c = makeClient();
    const search = createSearchController(c, { site: "wknd", locale: "en_US" }, undefined, {
      chat: false,
    });
    await search.toggleFacet(facetItem("ADD"));
    await search.toggleFacet(facetItem("ADD", "type:page"));
    await search.clearFacet({
      name: "section",
      multiValued: true,
      clearFilterQueries: [RESERVED],
      items: [],
    });

    expect((postCalls(c).at(-1)![1] as Record<string, unknown>).fq).toEqual(["type:page"]);
  });

  it("clearAllFilters empties fq", async () => {
    const c = makeClient();
    const search = createSearchController(c, { site: "wknd", locale: "en_US" }, undefined, {
      chat: false,
    });
    await search.toggleFacet(facetItem("ADD"));
    await search.clearAllFilters();

    expect((postCalls(c).at(-1)![1] as Record<string, unknown>).fq).toEqual([]);
  });

  it("goToPage keeps the filters and does not reset the page", async () => {
    const c = makeClient();
    const search = createSearchController(c, { site: "wknd", locale: "en_US" }, undefined, {
      chat: false,
    });
    await search.toggleFacet(facetItem("ADD"));
    await search.goToPage(3);

    const body = postCalls(c).at(-1)![1] as Record<string, unknown>;
    expect(body.page).toBe(3);
    expect(body.fq).toEqual([RESERVED]);
  });

  it("applySpellCheck uses the suggestion, or forces the original term", async () => {
    const c = makeClient(
      structuredResponse({
        spellCheck: {
          correctedText: true,
          usingCorrectedText: false,
          originalText: "serch",
          correctedTextValue: "search",
        },
      }),
    );
    const search = createSearchController(c, { site: "wknd", locale: "en_US" }, undefined, {
      chat: false,
    });
    await search.searchQuery("serch");

    await search.applySpellCheck();
    let body = postCalls(c).at(-1)![1] as Record<string, unknown>;
    expect(body.q).toBe("search");
    expect(body.autoCorrectionDisabled).toBe(false);

    await search.applySpellCheck(true);
    body = postCalls(c).at(-1)![1] as Record<string, unknown>;
    expect(body.q).toBe("serch");
    expect(body.autoCorrectionDisabled).toBe(true);
  });

  it("changeLocale and changeSort travel in the body", async () => {
    const c = makeClient();
    const search = createSearchController(c, { site: "wknd", locale: "en_US" }, undefined, {
      chat: false,
    });
    await search.changeLocale("pt_BR");
    expect((postCalls(c).at(-1)![1] as Record<string, unknown>).locale).toBe("pt_BR");

    await search.changeSort("newest");
    expect((postCalls(c).at(-1)![1] as Record<string, unknown>).sort).toBe("newest");
  });

  // ── legacy escape hatch ──

  it("postNative:false keeps the GET path and the link-bearing response", async () => {
    const legacy: TurSearchResponse = {
      queryContext: {
        count: 1,
        defaultFields: {
          url: "url",
          title: "title",
          description: "abstract",
          date: "date",
          image: "image",
          text: "text",
        },
      } as TurSearchResponse["queryContext"],
      results: { document: [] },
      widget: {} as TurSearchResponse["widget"],
    };
    // The url parameter is declared because the assertion below reads it: a
    // zero-arg mock types `mock.calls` as an empty tuple, so `calls[0][0]` is
    // an error the runtime never reports (T918).
    const get = vi.fn((_url: string) => Promise.resolve(legacy));
    const post = vi.fn(() => Promise.resolve(undefined));
    const c = { get, post } as unknown as TuringClient;

    const search = createSearchController(c, { site: "wknd", locale: "en_US" }, undefined, {
      chat: false,
      postNative: false,
    });
    await search.searchQuery("boots");

    expect(get.mock.calls[0][0]).toContain("/sn/wknd/search?");
    expect(post.mock.calls).toHaveLength(0);
    expect(search.getState().data).toBe(legacy);
    expect(search.getState().structured).toBeNull();
  });

  // ── T880 / §LXX.2 ──

  /**
   * The mapper has set `similar` and `spotlights` since T709, but the type
   * declared neither — no `similar` field at all, and `spotlights: unknown[]`.
   * Both reads below are the assertion: they do not compile if the fields are
   * undeclared or untyped, so no cast may appear in this test.
   */
  it("exposes similar and spotlights as read-through typed fields", async () => {
    const response = structuredResponse({
      similar: [{ id: "doc-2", title: "Neighbour", type: "Page", url: "/n" }],
      spotlights: [{ id: "sp-1", position: 1, title: "Pinned", url: "/pinned" }],
    });
    const c = makeClient(response);

    const search = createSearchController(c, { site: "wknd", locale: "en_US" });
    await search.searchQuery("boots");

    const structured = search.getState().structured!;
    expect(structured.similar?.[0].title).toBe("Neighbour");
    expect(structured.spotlights?.[0].position).toBe(1);
    // T938 — `url`, not `link`: the spotlight is projected like the rest of the
    // response, so nothing in it is spelled the way a navigation href is.
    expect(structured.spotlights?.[0].url).toBe("/pinned");
  });

  // ── T937 / §XLV.7 ──

  /**
   * `TurWidget.similar` was declared `string` while the server has always sent a
   * `List<TurSESimilarResult>` there, so the legacy transport carried a value no
   * typed consumer could iterate. `.map` below is the assertion: it does not
   * compile against a `string`, and no cast may appear in this test.
   */
  it("types the legacy widget's similar as the same documents the structured arm carries", async () => {
    const legacy: TurSearchResponse = {
      queryContext: { count: 1 } as TurSearchResponse["queryContext"],
      results: { document: [] },
      widget: {
        similar: [{ id: "doc-2", title: "Neighbour", type: "Page", url: "/n" }],
      } as TurSearchResponse["widget"],
    };
    const get = vi.fn((_url: string) => Promise.resolve(legacy));
    const c = { get, post: vi.fn() } as unknown as TuringClient;

    const search = createSearchController(c, { site: "wknd", locale: "en_US" }, undefined, {
      chat: false,
      postNative: false,
    });
    await search.searchQuery("boots");

    const widget = search.getState().data!.widget;
    expect(widget.similar.map((doc) => doc.url)).toEqual(["/n"]);
  });

  // ── T945 / §XLV.10 ──

  /**
   * The react-sdk README's "Two response shapes" passage is the source a new
   * consumer reads before writing a line, and it used to say both ways of
   * reaching through `data` render nothing. Only the optional-chained one does;
   * the plain read throws, because `data` is `null` and the access never gets as
   * far as `widget`.
   *
   * <p>That distinction is the whole point of the passage — one shape fails on
   * the first render and the other is the silent one worth warning about — and it
   * rests on a fact about the controller rather than about JavaScript: if
   * POST-native ever left `data` as an empty object instead of `null`, the loud
   * half would go quiet and the README would be wrong again, unnoticed.
   */
  it("leaves data null POST-native, so the two documented reads fail differently", async () => {
    const c = makeClient();
    const search = createSearchController(c, { site: "wknd", locale: "en_US" }, undefined, {
      chat: false,
    });
    await search.searchQuery("boots");

    const state = search.getState();
    expect(state.data).toBeNull();

    // `data.widget.facet.map(…)` — loud, and never reaches `widget`.
    expect(() => (state.data as unknown as TurSearchResponse).widget.facet).toThrow(TypeError);

    // `data?.widget?.facet.map(…)` — the whole chain short-circuits to undefined,
    // which is an empty rail and no error: the one that ships.
    expect(state.data?.widget?.facet.map((group) => group.name)).toBeUndefined();
  });
});
