import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { parseHrefToParams } from "../api";
import { resetDeprecationNotices } from "../deprecation";
import { createSearchController } from "../controllers/search";
import type { TuringClient } from "../client";
import type { TurSearchResponse } from "../types";

/**
 * T713 / §XLV — the query-string round-trip is deprecated, not deleted.
 *
 * The server-rendered links stay (deep-linking / SEO / no-JS is a protected
 * non-goal), and so does `postNative: false` for consumers that read
 * `state.data`. What is on notice is the SDK *parsing* one of those URLs back
 * into params — `parseHrefToParams`, and `navigate(href)` through it.
 *
 * These cases pin two things a future removal depends on: the notice fires
 * (once), and it fires from exactly one place, so a caller sees one line rather
 * than one per facet chip.
 */
describe("href round-trip deprecation (T713)", () => {
  let warn: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    resetDeprecationNotices();
    warn = vi.spyOn(console, "warn").mockImplementation(() => {});
  });

  afterEach(() => {
    warn.mockRestore();
  });

  it("warns once, no matter how many times the href is parsed", () => {
    parseHrefToParams("/api/sn/s/search?q=a&fq[]=x:1");
    parseHrefToParams("/api/sn/s/search?q=b&fq[]=y:2");
    parseHrefToParams("/api/sn/s/search?q=c");

    expect(warn).toHaveBeenCalledTimes(1);
    expect(String(warn.mock.calls[0][0])).toContain("parseHrefToParams");
  });

  it("names the structured replacements so the notice is actionable", () => {
    parseHrefToParams("/api/sn/s/search?q=a");

    const message = String(warn.mock.calls[0][0]);
    expect(message).toContain("toggleFacet");
    expect(message).toContain("goToPage");
    expect(message).toContain("STRUCTURED");
  });

  it("still parses correctly — this is a notice, not a removal", () => {
    const params = parseHrefToParams(
      "/api/sn/s/search?q=chat&p=2&fq[]=section:RAG+%26+Chat&_setlocale=en_US",
    );

    expect(params.q).toBe("chat");
    expect(params.p).toBe("2");
    expect(params.fq).toEqual(["section:RAG & Chat"]);
    expect(params._setlocale).toBe("en_US");
  });

  it("navigate(href) emits the same single notice, not a second one", async () => {
    const client = {
      get: vi.fn(async () => ({ data: legacyResponse() })),
      post: vi.fn(),
    } as unknown as TuringClient;
    const search = createSearchController(
      client,
      { site: "s", locale: "en_US" },
      undefined,
      { chat: false, postNative: false },
    );

    await search.navigate("/api/sn/s/search?q=chat&fq[]=section:RAG+%26+Chat");
    // …and it actually navigated: the parsed params reached the GET call.
    expect(search.getState().params.fq).toEqual(["section:RAG & Chat"]);

    await search.navigate("/api/sn/s/search?q=chat&p=2");
    expect(search.getState().params.p).toBe("2");

    expect(warn).toHaveBeenCalledTimes(1);
  });

  it("stays silent on the POST-native path", async () => {
    const client = {
      get: vi.fn(),
      post: vi.fn(async () => ({
        data: {
          request: { q: "*", page: 1, fq: [], locale: "en_US" },
          queryContext: { count: 0 },
        },
      })),
    } as unknown as TuringClient;
    const search = createSearchController(client, { site: "s", locale: "en_US" }, undefined, {
      chat: false,
    });

    await search.searchQuery("chat");
    await search.clearAllFilters();
    await search.goToPage(2);

    expect(warn).not.toHaveBeenCalled();
  });
});

function legacyResponse(): TurSearchResponse {
  return {
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
    },
    results: { document: [] },
  } as unknown as TurSearchResponse;
}
