import { afterEach, describe, expect, it, vi } from "vitest";
import { createAbandonmentWatcher, type AbandonmentReason } from "../analytics-lifecycle";
import { createTuringAnalytics, type TuringAnalyticsEvent } from "../analytics";
import { createChatController } from "../controllers/chat";
import type { TuringClient } from "../client";

/**
 * T460 (Block Z) — abandonment & engagement detection.
 */
describe("createAbandonmentWatcher (T460)", () => {
  afterEach(() => vi.useRealTimers());

  it("fires on page unload (pagehide)", () => {
    const fired: AbandonmentReason[] = [];
    const watcher = createAbandonmentWatcher((r) => fired.push(r));
    globalThis.dispatchEvent(new Event("pagehide"));
    expect(fired).toEqual(["unload"]);
    watcher.stop();
  });

  it("fires on idle timeout and ping resets the clock", () => {
    vi.useFakeTimers();
    const fired: AbandonmentReason[] = [];
    const watcher = createAbandonmentWatcher((r) => fired.push(r), { idleMs: 1000, onUnload: false });

    vi.advanceTimersByTime(800);
    watcher.ping(); // reset
    vi.advanceTimersByTime(800);
    expect(fired).toEqual([]); // not yet — ping pushed it out
    vi.advanceTimersByTime(300);
    expect(fired).toEqual(["idle"]);
    watcher.stop();
  });

  it("stop() removes listeners", () => {
    const fired: AbandonmentReason[] = [];
    const watcher = createAbandonmentWatcher((r) => fired.push(r));
    watcher.stop();
    globalThis.dispatchEvent(new Event("pagehide"));
    expect(fired).toEqual([]);
  });
});

describe("createChatController — abandonment wiring (T460)", () => {
  function sse(content: string): Response {
    return new Response(`data: ${JSON.stringify({ role: "assistant", content })}\n`, {
      status: 200,
      headers: { "Content-Type": "text/event-stream" },
    });
  }

  it("emits turing_chat_abandoned once after a started conversation", async () => {
    const client = { fetchRaw: vi.fn().mockResolvedValue(sse("hi")) } as unknown as TuringClient;
    const events: TuringAnalyticsEvent[] = [];
    const analytics = createTuringAnalytics({ sinks: [(e) => events.push(e)] });

    const chat = createChatController(client, {
      agent: { id: "a1", llmInstanceId: "l1" },
      conversationId: "conv1",
      analytics,
    });

    await chat.send("hello");
    globalThis.dispatchEvent(new Event("pagehide"));
    globalThis.dispatchEvent(new Event("pagehide")); // second must NOT re-fire

    const abandoned = events.filter((e) => e.name === "turing_chat_abandoned");
    expect(abandoned).toHaveLength(1);
    expect(abandoned[0].params).toMatchObject({ reason: "unload", turns: 1, last_step: 1 });
    chat.destroy();
  });

  it("does not fire before any user message", () => {
    const client = { fetchRaw: vi.fn() } as unknown as TuringClient;
    const events: TuringAnalyticsEvent[] = [];
    const analytics = createTuringAnalytics({ sinks: [(e) => events.push(e)] });
    createChatController(client, {
      agent: { id: "a1", llmInstanceId: "l1" },
      conversationId: "conv1",
      analytics,
    });
    globalThis.dispatchEvent(new Event("pagehide"));
    expect(events.filter((e) => e.name === "turing_chat_abandoned")).toHaveLength(0);
  });

  it("abandonment: false disables the watcher", async () => {
    const client = { fetchRaw: vi.fn().mockResolvedValue(sse("hi")) } as unknown as TuringClient;
    const events: TuringAnalyticsEvent[] = [];
    const analytics = createTuringAnalytics({ sinks: [(e) => events.push(e)] });
    const chat = createChatController(client, {
      agent: { id: "a1", llmInstanceId: "l1" },
      conversationId: "conv1",
      analytics,
      abandonment: false,
    });
    await chat.send("hi");
    globalThis.dispatchEvent(new Event("pagehide"));
    expect(events.filter((e) => e.name === "turing_chat_abandoned")).toHaveLength(0);
  });
});
