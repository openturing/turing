import { describe, expect, it, vi } from "vitest";
import { createSearchController } from "../controllers/search";
import type { TuringClient } from "../client";
import type { TurSearchResponse } from "../types";

/**
 * T484 — the `chat` option on {@link createSearchController}. A search-only
 * surface (e.g. the marketing-site faceted hero) must not incur an LLM chat
 * call (cost + rate-limit) on every query.
 */
describe("createSearchController — chat option (T484)", () => {
  function searchResponse(): TurSearchResponse {
    return {
      queryContext: { count: 3 } as TurSearchResponse["queryContext"],
      results: { document: [] },
      widget: {} as TurSearchResponse["widget"],
    };
  }

  function makeClient() {
    const get = vi.fn((url: string) => {
      if (url.includes("/chat")) return Promise.resolve({ text: "hi", enabled: true });
      return Promise.resolve(searchResponse());
    });
    const post = vi.fn().mockResolvedValue(undefined);
    return { get, post } as unknown as TuringClient;
  }

  const chatCalls = (c: TuringClient) =>
    (c.get as ReturnType<typeof vi.fn>).mock.calls.filter(([url]) =>
      String(url).includes("/chat"),
    );

  it("fetches chat by default for a non-wildcard query", async () => {
    const c = makeClient();
    const search = createSearchController(c, { site: "s1", locale: "en_US" });
    await search.searchQuery("boots");
    expect(chatCalls(c)).toHaveLength(1);
    expect(search.getState().chat).not.toBeNull();
  });

  it("skips chat entirely when chat:false (search-only)", async () => {
    const c = makeClient();
    const search = createSearchController(c, { site: "s1", locale: "en_US" }, undefined, {
      chat: false,
    });
    await search.searchQuery("boots");
    expect(chatCalls(c)).toHaveLength(0);
    expect(search.getState().chat).toBeNull();
    // search itself still ran
    expect((c.get as ReturnType<typeof vi.fn>).mock.calls.some(([u]) => String(u).includes("/search"))).toBe(true);
  });

  it("never fetches chat for the wildcard browse query, regardless of the option", async () => {
    const c = makeClient();
    const search = createSearchController(c, { site: "s1", locale: "en_US" });
    await search.search({ q: "*" });
    expect(chatCalls(c)).toHaveLength(0);
  });
});
