import { describe, expect, it, vi } from "vitest";
import { createAnswerController } from "../controllers/answer";
import { consumeAnswerStream } from "../api";
import type { TuringClient } from "../client";

/**
 * T837 / §LXII.6 — the vanilla answer controller consumes the T833 `/answer`
 * stream's REAL named SSE frames (`event: <name>\ndata: …`) into an observable
 * store: sources before tokens, then citations / follow-ups / trace, and a
 * mid-stream `error` event degrading fail-open.
 */
describe("createAnswerController (T837)", () => {
  /** Encodes one named SSE frame; multi-line data is split across `data:` lines. */
  function frame(event: string, data: string): string {
    const dataLines = data
      .split("\n")
      .map((line) => `data: ${line}`)
      .join("\n");
    return `event: ${event}\n${dataLines}\n\n`;
  }

  function sse(frames: string[]): Response {
    return new Response(frames.join(""), {
      status: 200,
      headers: { "Content-Type": "text/event-stream" },
    });
  }

  const fullAnswer = () =>
    sse([
      frame(
        "sources",
        JSON.stringify([
          { sourceId: "a", title: "A", url: "ua", rank: 1 },
          { sourceId: "b", title: "B", url: "ub", rank: 2 },
        ]),
      ),
      frame("token", "Hello "),
      frame("token", "world"),
      frame(
        "citations",
        JSON.stringify([
          { rank: 1, sourceId: "a", title: "A" },
          { rank: 2, sourceId: "b", title: "B" },
        ]),
      ),
      frame("followups", JSON.stringify(["What about X?", "And Y?"])),
      frame("trace", JSON.stringify({ sourceCount: 2, answerLength: 11 })),
    ]);

  it("streams sources, tokens, citations, follow-ups and trace into state", async () => {
    const fetchRaw = vi.fn().mockResolvedValueOnce(fullAnswer());
    const client = { fetchRaw } as unknown as TuringClient;
    const controller = createAnswerController(client, { site: "site1", locale: "en" });

    await controller.ask("hello");
    const state = controller.getState();

    expect(state.status).toBe("success");
    expect(state.isStreaming).toBe(false);
    expect(state.answer).toBe("Hello world");
    expect(state.sources.map((s) => s.rank)).toEqual([1, 2]);
    expect(state.citations.map((c) => c.rank)).toEqual([1, 2]);
    expect(state.followups).toEqual(["What about X?", "And Y?"]);
    expect(state.trace).toEqual({ sourceCount: 2, answerLength: 11 });
    // GET the answer endpoint, no CSRF (safe), with the locale query param.
    expect(fetchRaw).toHaveBeenCalledWith(
      expect.stringContaining("/sn/site1/answer?q=hello"),
      expect.objectContaining({ method: "GET" }),
      false,
    );
    expect(fetchRaw.mock.calls[0][0]).toContain("locale=en");
  });

  it("notifies subscribers as the answer streams", async () => {
    const fetchRaw = vi.fn().mockResolvedValueOnce(fullAnswer());
    const client = { fetchRaw } as unknown as TuringClient;
    const controller = createAnswerController(client, { site: "site1" });
    const seen: string[] = [];
    controller.subscribe((s) => seen.push(s.answer));

    await controller.ask("hello");

    expect(seen).toContain("Hello world");
  });

  it("degrades fail-open on a mid-stream error event, keeping partial answer", async () => {
    const fetchRaw = vi.fn().mockResolvedValueOnce(
      sse([frame("token", "partial "), frame("error", JSON.stringify({ message: "provider down" }))]),
    );
    const client = { fetchRaw } as unknown as TuringClient;
    const controller = createAnswerController(client, { site: "site1" });

    await controller.ask("x");
    const state = controller.getState();

    expect(state.status).toBe("error");
    expect(state.error).toBe("provider down");
    expect(state.answer).toBe("partial ");
  });

  it("ignores a blank or wildcard query without hitting the network", async () => {
    const fetchRaw = vi.fn();
    const client = { fetchRaw } as unknown as TuringClient;
    const controller = createAnswerController(client, { site: "site1" });

    await controller.ask("   ");
    await controller.ask("*");

    expect(fetchRaw).not.toHaveBeenCalled();
    expect(controller.getState().status).toBe("idle");
  });

  it("steers the answer within the active filter set (T838)", async () => {
    const fetchRaw = vi.fn().mockResolvedValueOnce(fullAnswer());
    const client = { fetchRaw } as unknown as TuringClient;
    const controller = createAnswerController(client, { site: "site1" });

    await controller.ask("cheapest model", {
      filters: { vendor: ["Anthropic"], context: [">=200000"] },
    });

    // Each field:value pair rides as a repeated fq= query param.
    const url = fetchRaw.mock.calls[0][0] as string;
    expect(url).toContain("fq=vendor%3AAnthropic");
    expect(url).toContain("fq=context%3A%3E%3D200000");
  });

  it("re-checks the freshness of the answer's cited sources (T840)", async () => {
    const fetchRaw = vi.fn().mockResolvedValueOnce(fullAnswer());
    const post = vi.fn().mockResolvedValueOnce([
      { sourceId: "a", status: "STALE", reason: "reindexed after the answer" },
      { sourceId: "b", status: "FRESH" },
    ]);
    const client = { fetchRaw, post } as unknown as TuringClient;
    const controller = createAnswerController(client, { site: "site1" });

    await controller.ask("hello");
    expect(controller.getState().answeredAt).toBeTruthy();

    await controller.checkFreshness();

    expect(post).toHaveBeenCalledWith(
      "/sn/site1/answer/freshness",
      expect.objectContaining({ q: "hello" }),
    );
    // Only the STALE source is flagged.
    expect(controller.getState().staleSources).toEqual(["a"]);
  });

  it("collects structured answer artifacts (T841)", async () => {
    const fetchRaw = vi.fn().mockResolvedValueOnce(
      sse([
        frame("token", "Comparison: "),
        frame("artifact", JSON.stringify({ callId: "c1", name: "comparison_table", args: "{}" })),
      ]),
    );
    const client = { fetchRaw } as unknown as TuringClient;
    const controller = createAnswerController(client, { site: "site1" });

    await controller.ask("compare A and B");

    expect(controller.getState().artifacts).toEqual([
      { callId: "c1", name: "comparison_table", args: "{}" },
    ]);
  });

  it("reset() clears state back to idle", async () => {
    const fetchRaw = vi.fn().mockResolvedValueOnce(fullAnswer());
    const client = { fetchRaw } as unknown as TuringClient;
    const controller = createAnswerController(client, { site: "site1" });

    await controller.ask("hello");
    controller.reset();

    expect(controller.getState()).toMatchObject({ status: "idle", answer: "", sources: [], followups: [] });
  });
});

describe("consumeAnswerStream (T837)", () => {
  it("preserves a multi-line token split across data lines", async () => {
    const body = "event: token\ndata: line1\ndata: line2\n\n";
    const response = new Response(body, { status: 200 });

    const result = await consumeAnswerStream(response);

    expect(result.answer).toBe("line1\nline2");
  });
});
