import { afterEach, describe, expect, it, vi } from "vitest";

import { createTuringClient, postNlFilters, type TurNLFilters } from "../index";

/**
 * T1085 — a site turns a visitor's description into its own search's `q` and
 * `fq` with one POST.
 */
const BASE = "https://turing.example/api";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("postNlFilters", () => {
  it("posts the text and locale, and returns the q and fq a search takes", async () => {
    const parsed: TurNLFilters = {
      available: true,
      text: "meio periodo em sao paulo",
      q: "",
      filters: [
        {
          field: "city",
          values: ["São Paulo"],
          rangeStart: null,
          rangeEnd: null,
          sourceWords: ["sao paulo"],
          confidence: 1,
          grounded: true,
        },
      ],
      fq: ["city:São Paulo"],
      dropped: [],
    };
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(parsed), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const client = createTuringClient({ baseURL: BASE });

    const result = await postNlFilters(client, "vagas", "meio periodo em sao paulo", "pt_BR");

    expect(result.fq).toEqual(["city:São Paulo"]);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(new URL(url).pathname).toBe("/api/sn/vagas/nl-filters");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({
      text: "meio periodo em sao paulo",
      locale: "pt_BR",
    });
  });
});
