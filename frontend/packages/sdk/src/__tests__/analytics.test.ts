import { describe, expect, it, vi } from "vitest";
import {
  createTuringAnalytics,
  TURING_ANALYTICS_EVENTS,
  type TuringAnalyticsEvent,
} from "../analytics";
import { createChatController } from "../controllers/chat";
import type { TuringClient } from "../client";

/**
 * T458 (Block Z) — canonical analytics event bus + chat-controller wiring.
 */
describe("createTuringAnalytics (T458)", () => {
  it("dispatches a fully-formed event to every sink with the context envelope", () => {
    const a = vi.fn();
    const b = vi.fn();
    const analytics = createTuringAnalytics({ sinks: [a, b], context: { site: "s1" } });

    analytics.emit(TURING_ANALYTICS_EVENTS.search, { query: "shoes", results: 12 });

    expect(a).toHaveBeenCalledOnce();
    expect(b).toHaveBeenCalledOnce();
    const event = a.mock.calls[0][0] as TuringAnalyticsEvent;
    expect(event.name).toBe("turing_search");
    expect(event.params).toEqual({ query: "shoes", results: 12 });
    expect(event.context).toEqual({ site: "s1" });
    expect(typeof event.timestamp).toBe("number");
  });

  it("strips undefined params before dispatch", () => {
    const sink = vi.fn();
    const analytics = createTuringAnalytics({ sinks: [sink] });
    analytics.emit(TURING_ANALYTICS_EVENTS.search, { query: "x", missing: undefined });
    const event = sink.mock.calls[0][0] as TuringAnalyticsEvent;
    expect(event.params).toEqual({ query: "x" });
    expect("missing" in event.params).toBe(false);
  });

  it("setContext stamps onto subsequent events only", () => {
    const sink = vi.fn();
    const analytics = createTuringAnalytics({ sinks: [sink] });
    analytics.emit(TURING_ANALYTICS_EVENTS.chatStart);
    analytics.setContext({ experimentKey: "exp", variantLabel: "B", personaId: "p1" });
    analytics.emit(TURING_ANALYTICS_EVENTS.chatMessageSent, { turn: 1 });

    expect((sink.mock.calls[0][0] as TuringAnalyticsEvent).context.experimentKey).toBeUndefined();
    const second = sink.mock.calls[1][0] as TuringAnalyticsEvent;
    expect(second.context).toMatchObject({ experimentKey: "exp", variantLabel: "B", personaId: "p1" });
  });

  it("isolates a throwing sink from the rest", () => {
    const bad = vi.fn(() => {
      throw new Error("boom");
    });
    const good = vi.fn();
    const analytics = createTuringAnalytics({ sinks: [bad, good] });
    expect(() => analytics.emit(TURING_ANALYTICS_EVENTS.search)).not.toThrow();
    expect(good).toHaveBeenCalledOnce();
  });

  it("addSink returns a remover", () => {
    const analytics = createTuringAnalytics();
    const sink = vi.fn();
    const remove = analytics.addSink(sink);
    analytics.emit(TURING_ANALYTICS_EVENTS.search);
    remove();
    analytics.emit(TURING_ANALYTICS_EVENTS.search);
    expect(sink).toHaveBeenCalledOnce();
  });
});

describe("createChatController — analytics wiring (T458)", () => {
  function sse(events: Array<{ content: string }>): Response {
    const body = events
      .map((e) => `data: ${JSON.stringify({ role: "assistant", ...e })}\n`)
      .join("");
    return new Response(body, { status: 200, headers: { "Content-Type": "text/event-stream" } });
  }

  it("emits chat_start once and message_sent per send, stamping the conversation id", async () => {
    const fetchRaw = vi
      .fn()
      .mockResolvedValue(sse([{ content: "hi there" }]));
    const client = { fetchRaw } as unknown as TuringClient;

    const events: TuringAnalyticsEvent[] = [];
    const analytics = createTuringAnalytics({ sinks: [(e) => events.push(e)] });

    const chat = createChatController(client, {
      agent: { id: "a1", llmInstanceId: "l1" },
      conversationId: "conv1",
      analytics,
    });

    await chat.send("hello");
    await chat.send("again");

    const names = events.map((e) => e.name);
    expect(names.filter((n) => n === "turing_chat_start")).toHaveLength(1);
    expect(names.filter((n) => n === "turing_chat_message_sent")).toHaveLength(2);

    const sent = events.filter((e) => e.name === "turing_chat_message_sent");
    expect(sent[0].params.turn).toBe(1);
    expect(sent[1].params.turn).toBe(2);
    expect(sent[0].context.conversationId).toBe("conv1");
    expect(sent[0].context.sessionId).toBe("conv1");
  });

  it("re-arms chat_start after reset()", async () => {
    const fetchRaw = vi.fn().mockResolvedValue(sse([{ content: "ok" }]));
    const client = { fetchRaw } as unknown as TuringClient;
    const events: TuringAnalyticsEvent[] = [];
    const analytics = createTuringAnalytics({ sinks: [(e) => events.push(e)] });

    const chat = createChatController(client, {
      agent: { id: "a1", llmInstanceId: "l1" },
      conversationId: "conv1",
      analytics,
    });

    await chat.send("hi");
    chat.reset();
    await chat.send("fresh start");

    expect(events.filter((e) => e.name === "turing_chat_start")).toHaveLength(2);
  });
});
