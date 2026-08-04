import { describe, expect, it } from "vitest";
import {
  lensHref,
  lensSwitcherVisible,
  readLensQuery,
  unknownLensParams,
} from "../lib/search-lens";

/**
 * T855 / §LXIV.4 (Block BQ) — one question survives the jump between the three
 * lenses even though their URL dialects differ: `/sn` and `/answer` carry filters
 * as repeated `fq=field:value`, `/ann` as bare repeated `field=value`.
 *
 * @since 2026.3.4
 */

describe("readLensQuery", () => {
  it("reads the fq dialect of the keyword and answer lenses", () => {
    const params = new URLSearchParams("q=warranty&_setlocale=en_US&fq=brand:Acme&fq=brand:Globex");
    for (const lens of ["keyword", "answer"] as const) {
      expect(readLensQuery(lens, params)).toEqual({
        q: "warranty",
        locale: "en_US",
        filters: { brand: ["Acme", "Globex"] },
      });
    }
  });

  it("reads the bare dialect of the vector lens", () => {
    const params = new URLSearchParams("q=warranty&_setlocale=en_US&brand=Acme&type=Laptop");
    expect(readLensQuery("vector", params)).toEqual({
      q: "warranty",
      locale: "en_US",
      filters: { brand: ["Acme"], type: ["Laptop"] },
    });
  });

  it("does not read the vector lens's paging as a filter", () => {
    const params = new URLSearchParams("q=warranty&topK=20&page=2&pageSize=20&brand=Acme");
    expect(readLensQuery("vector", params).filters).toEqual({ brand: ["Acme"] });
  });

  it("ignores an fq value with no field or no value", () => {
    const params = new URLSearchParams("q=x&fq=:Acme&fq=brand:&fq=brand:Acme");
    expect(readLensQuery("answer", params).filters).toEqual({ brand: ["Acme"] });
  });
});

describe("lensHref", () => {
  const query = { q: "warranty", locale: "en_US", filters: { brand: ["Acme", "Globex"] } };

  it("translates the fq dialect into the vector lens's bare params", () => {
    expect(lensHref("vector", "Sample", query)).toBe(
      "/ann/Sample?q=warranty&_setlocale=en_US&brand=Acme&brand=Globex",
    );
  });

  it("keeps the fq dialect for the keyword and answer lenses", () => {
    expect(lensHref("keyword", "Sample", query)).toBe(
      "/sn/Sample?q=warranty&_setlocale=en_US&fq=brand%3AAcme&fq=brand%3AGlobex",
    );
    expect(lensHref("answer", "Sample", query)).toBe(
      "/answer/Sample?q=warranty&_setlocale=en_US&fq=brand%3AAcme&fq=brand%3AGlobex",
    );
  });

  it("emits a bare route when the question is empty", () => {
    expect(lensHref("answer", "Sample", { q: "  ", filters: {} })).toBe("/answer/Sample");
  });

  it("round-trips a question through every lens without losing filters", () => {
    for (const lens of ["keyword", "vector", "answer"] as const) {
      const href = lensHref(lens, "Sample", query);
      const params = new URLSearchParams(href.slice(href.indexOf("?")));
      expect(readLensQuery(lens, params)).toEqual(query);
    }
  });
});

/** T891 / §LXIV.7 — the public keyword page opts in; the operator lenses don't ask. */
describe("the switcher's gate", () => {
  it("shows on both operator lenses unconditionally", () => {
    const bare = new URLSearchParams("q=x");
    expect(lensSwitcherVisible("vector", bare)).toBe(true);
    expect(lensSwitcherVisible("answer", bare)).toBe(true);
  });

  it("hides on the public keyword page until the opt-in param says otherwise", () => {
    expect(lensSwitcherVisible("keyword", new URLSearchParams("q=x"))).toBe(false);
    expect(lensSwitcherVisible("keyword", new URLSearchParams("q=x&_lens=1"))).toBe(true);
    // Any other value is not the opt-in.
    expect(lensSwitcherVisible("keyword", new URLSearchParams("q=x&_lens=true"))).toBe(false);
  });

  it("carries the opt-in into the keyword lens, and only there", () => {
    const query = { q: "warranty", filters: {} };
    expect(lensHref("keyword", "Sample", query, { switcher: true })).toContain("_lens=1");
    expect(lensHref("vector", "Sample", query, { switcher: true })).not.toContain("_lens");
    expect(lensHref("answer", "Sample", query, { switcher: true })).not.toContain("_lens");
    // Absent by default, so a plain link stays the plain public URL.
    expect(lensHref("keyword", "Sample", query)).toBe("/sn/Sample?q=warranty");
  });

  it("never reads the opt-in as a filter, in either dialect", () => {
    expect(readLensQuery("keyword", new URLSearchParams("q=x&_lens=1")).filters).toEqual({});
    expect(readLensQuery("vector", new URLSearchParams("q=x&_lens=1")).filters).toEqual({});
    expect(unknownLensParams("keyword", new URLSearchParams("q=x&_lens=1"))).toEqual([]);
  });
});

describe("unknownLensParams", () => {
  it("names what an fq-dialect lens ignored", () => {
    const params = new URLSearchParams("q=x&fq=brand:Acme&sort=relevance&p=2");
    expect(unknownLensParams("answer", params)).toEqual(["sort", "p"]);
  });

  it("reports nothing for the vector lens, where any param is a filter", () => {
    const params = new URLSearchParams("q=x&brand=Acme&sort=relevance");
    expect(unknownLensParams("vector", params)).toEqual([]);
  });
});
