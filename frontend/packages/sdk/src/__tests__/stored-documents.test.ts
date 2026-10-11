import { afterEach, describe, expect, it, vi } from "vitest";

import { createTuringClient, fetchStoredDocuments } from "../index";

/**
 * T1077 — a detail page reads documents by id, whole, through one GET that
 * carries every candidate id.
 */
const BASE = "https://turing.example/api";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("fetchStoredDocuments", () => {
  it("sends every id, and the locale, as query parameters", async () => {
    const docs = [{ id: "fx-1", text: "whole text" }];
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(docs), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const client = createTuringClient({ baseURL: BASE });

    const result = await fetchStoredDocuments(client, "vagas", ["x-fx-1", "fx-1"], "pt_BR");

    expect(result).toEqual(docs);
    const url = new URL((fetchMock.mock.calls[0] as unknown[])[0] as string);
    expect(url.pathname).toBe("/api/sn/vagas/search/document");
    expect(url.searchParams.getAll("id")).toEqual(["x-fx-1", "fx-1"]);
    expect(url.searchParams.get("locale")).toBe("pt_BR");
  });
});
