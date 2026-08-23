import { describe, expect, it, vi } from "vitest";

import {
  createTuringClient,
  TuringHttpError,
  TuringTransportError,
} from "../index";

/**
 * T1005 — the status and the failure kind are fields on a thrown type now, not
 * substrings of a message.
 *
 * Telling "the tenant is unreachable" from "the tenant said 404" from "the body
 * was not readable" used to need a regex over `error.message`, an
 * `instanceof TypeError` check and an `error.cause !== undefined` check. These
 * assert the three apart without reading a message, which is the whole point:
 * a message is the one part of an error that is free to be reworded.
 */
const BASE = "https://turing.example/api";

function clientWithFetch(impl: typeof fetch) {
  vi.stubGlobal("fetch", impl);
  return createTuringClient({ baseURL: BASE });
}

describe("TuringHttpError", () => {
  it("carries the status as a number, so a caller branches on it rather than on the message", async () => {
    const client = clientWithFetch(
      vi.fn(async () =>
        new Response("not found", { status: 404, statusText: "Not Found" }),
      ) as unknown as typeof fetch,
    );

    const err = await client.get("/sn/site/search").catch((e: unknown) => e);

    expect(err).toBeInstanceOf(TuringHttpError);
    expect((err as TuringHttpError).status).toBe(404);
    expect((err as TuringHttpError).name).toBe("TuringHttpError");
  });

  it("is not a TuringTransportError — the two call for different handling", async () => {
    const client = clientWithFetch(
      vi.fn(async () => new Response("", { status: 503 })) as unknown as typeof fetch,
    );

    const err = await client.get("/sn/site/search").catch((e: unknown) => e);

    expect(err).toBeInstanceOf(TuringHttpError);
    expect(err).not.toBeInstanceOf(TuringTransportError);
    expect((err as TuringHttpError).status).toBe(503);
  });

  it("keeps a human-readable message, but nothing has to parse it", async () => {
    const client = clientWithFetch(
      vi.fn(async () =>
        new Response("", { status: 401, statusText: "Unauthorized" }),
      ) as unknown as typeof fetch,
    );

    const err = (await client
      .get("/sn/site/search")
      .catch((e: unknown) => e)) as TuringHttpError;

    expect(err.message).toContain("401");
    // The status is readable without the message, which is the contract: a
    // rewording above must not be able to break a consumer.
    expect(err.status).toBe(401);
  });
});

describe("TuringTransportError", () => {
  it("is thrown when the request never completes, and keeps the cause", async () => {
    const refused = new TypeError("Failed to fetch");
    const client = clientWithFetch(
      vi.fn(async () => {
        throw refused;
      }) as unknown as typeof fetch,
    );

    const err = await client.get("/sn/site/search").catch((e: unknown) => e);

    expect(err).toBeInstanceOf(TuringTransportError);
    expect(err).not.toBeInstanceOf(TuringHttpError);
    expect((err as TuringTransportError).cause).toBe(refused);
    expect((err as TuringTransportError).url).toBe(`${BASE}/sn/site/search`);
  });

  it("does NOT swallow the caller's own abort", async () => {
    // Cancellation is not a failure. A search-as-you-type controller aborts on
    // every keystroke and catches AbortError to tell "I cancelled this" from
    // "this broke"; wrapping it would break that.
    const abort = new DOMException("The operation was aborted.", "AbortError");
    const client = clientWithFetch(
      vi.fn(async () => {
        throw abort;
      }) as unknown as typeof fetch,
    );

    const err = await client.get("/sn/site/search").catch((e: unknown) => e);

    expect(err).toBe(abort);
    expect(err).not.toBeInstanceOf(TuringTransportError);
  });
});

describe("both types", () => {
  it("are exported from the package root so a consumer can instanceof them", () => {
    expect(typeof TuringHttpError).toBe("function");
    expect(typeof TuringTransportError).toBe("function");
    // Real Errors, so an unhandled one still logs a stack.
    expect(new TuringHttpError(404, "Not Found", BASE)).toBeInstanceOf(Error);
    expect(new TuringTransportError(BASE, new Error("boom"))).toBeInstanceOf(Error);
  });
});
