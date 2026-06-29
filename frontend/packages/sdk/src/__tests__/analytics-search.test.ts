import { describe, expect, it, vi } from "vitest";
import { createTuringAnalytics, type TuringAnalyticsEvent } from "../analytics";
import { createSearchController } from "../controllers/search";
import type { TuringClient } from "../client";
import type { TurSearchResponse } from "../types";

/**
 * T462 (Block Z) — search / semantic-navigation conversion events.
 */
describe("createSearchController — analytics (T462)", () => {
  function searchResponse(count: number): TurSearchResponse {
    return {
      queryContext: { count } as TurSearchResponse["queryContext"],
      results: { document: [] },
      widget: {} as TurSearchResponse["widget"],
    };
  }

  function client(count: number): TuringClient {
    const get = vi.fn((url: string) => {
      if (url.includes("/search")) return Promise.resolve(searchResponse(count));
      if (url.includes("/chat")) return Promise.resolve(null);
      return Promise.resolve(null);
    });
    const post = vi.fn().mockResolvedValue(undefined);
    return { get, post } as unknown as TuringClient;
  }

  function setup(count: number) {
    const events: TuringAnalyticsEvent[] = [];
    const analytics = createTuringAnalytics({ sinks: [(e) => events.push(e)] });
    const c = client(count);
    const search = createSearchController(c, { site: "s1", locale: "en_US" }, undefined, { analytics });
    return { events, analytics, search, client: c };
  }

  it("emits turing_search with the total hit count", async () => {
    const { events, search } = setup(7);
    await search.searchQuery("boots");
    const ev = events.find((e) => e.name === "turing_search");
    expect(ev?.params).toMatchObject({ query: "boots", results: 7 });
  });

  it("emits turing_search_no_results on a zero-hit query", async () => {
    const { events, search } = setup(0);
    await search.searchQuery("zzzzz");
    expect(events.some((e) => e.name === "turing_search_no_results")).toBe(true);
  });

  it("emits turing_search_refined when the query changes", async () => {
    const { events, search } = setup(3);
    await search.searchQuery("shoes");
    await search.searchQuery("running shoes");
    const refined = events.filter((e) => e.name === "turing_search_refined");
    expect(refined).toHaveLength(1);
    expect(refined[0].params).toMatchObject({ from: "shoes", to: "running shoes" });
  });

  it("does not emit a search event for the wildcard browse query", async () => {
    const { events, search } = setup(99);
    await search.search({ q: "*" });
    expect(events.some((e) => e.name === "turing_search")).toBe(false);
  });

  it("trackResultClick mirrors postClick and emits the GA event", async () => {
    const { events, search, client: c } = setup(3);
    await search.searchQuery("boots");
    search.trackResultClick("doc-42", 2);

    expect((c.post as ReturnType<typeof vi.fn>)).toHaveBeenCalledWith(
      "/sn/s1/click",
      expect.objectContaining({ term: "boots", documentId: "doc-42", position: 2 }),
    );
    const click = events.find((e) => e.name === "turing_search_result_click");
    expect(click?.params).toMatchObject({ query: "boots", document_id: "doc-42", position: 2 });
  });
});
