import { afterEach, describe, expect, it, vi } from "vitest";

import {
  createTuringClient,
  fetchSearch,
  postStructuredSearch,
  TuringTimeoutError,
  TuringTransportError,
} from "../index";

/**
 * T1079 — a call can be bounded: the client's `timeoutMs` aborts a stalled
 * request with a recognisable error, and each search call takes the caller's own
 * `AbortSignal`.
 *
 * The fetch stub below never answers on its own; it settles only when the signal
 * it was given aborts, which is what a paused Turing looks like from the caller.
 */
const BASE = "https://turing.example/api";

function stalledFetch() {
  return vi.fn(
    (_url: string, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        const signal = init?.signal;
        if (!signal) return; // never settles: the hang this line was filed for
        signal.addEventListener("abort", () =>
          reject(new DOMException("The operation was aborted.", "AbortError")),
        );
      }),
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("timeoutMs", () => {
  it("rejects a stalled call with TuringTimeoutError once the limit elapses", async () => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", stalledFetch());
    const client = createTuringClient({ baseURL: BASE, timeoutMs: 8000 });

    const pending = postStructuredSearch(client, "vagas", { q: "*" }).catch((e: unknown) => e);
    await vi.advanceTimersByTimeAsync(8000);
    const err = await pending;

    expect(err).toBeInstanceOf(TuringTimeoutError);
    // a timeout is a transport failure: the same "Turing gave no answer" branch
    expect(err).toBeInstanceOf(TuringTransportError);
    expect((err as TuringTimeoutError).timeoutMs).toBe(8000);
    expect((err as TuringTimeoutError).url).toBe(`${BASE}/sn/vagas/search/query`);
    expect((err as Error).name).toBe("TuringTimeoutError");
  });

  it("leaves a call that answers in time alone", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify({ ok: true }), { status: 200 })),
    );
    const client = createTuringClient({ baseURL: BASE, timeoutMs: 8000 });

    await expect(client.get("/sn/vagas/search")).resolves.toEqual({ ok: true });
  });

  it("sets no limit when omitted, so fetch gets the caller's signal as before", async () => {
    const fetchMock = vi.fn(async () => new Response("[]", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const client = createTuringClient({ baseURL: BASE });

    await fetchSearch(client, "vagas", { q: "*" });

    const init = (fetchMock.mock.calls[0] as unknown[])[1] as RequestInit;
    expect(init.signal).toBeUndefined();
  });
});

describe("the caller's signal", () => {
  it("reaches fetch from fetchSearch, and its abort is rethrown as an AbortError", async () => {
    vi.stubGlobal("fetch", stalledFetch());
    const client = createTuringClient({ baseURL: BASE, timeoutMs: 60_000 });
    const controller = new AbortController();

    const pending = fetchSearch(client, "vagas", { q: "*" }, { signal: controller.signal }).catch(
      (e: unknown) => e,
    );
    controller.abort();
    const err = await pending;

    // cancellation is not a failure: neither a timeout nor a transport error
    expect(err).not.toBeInstanceOf(TuringTransportError);
    expect((err as Error).name).toBe("AbortError");
  });
});
