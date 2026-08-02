import { describe, expect, it, vi } from "vitest";
import { createAbandonmentWatcher } from "../analytics-lifecycle";
import { createChatController } from "../controllers/chat";
import { createTuringAnalytics, type TuringAnalyticsEvent } from "../analytics";
import type { TuringClient } from "../client";

/**
 * T862 — the SSR half of T460's abandonment watcher, deliberately left on the
 * suite's default `node` environment.
 *
 * `createAbandonmentWatcher` documents that it "returns a no-op watcher outside
 * a browser (SSR), so callers don't need their own environment check". Moving
 * the whole suite to jsdom would have made that claim untestable — every file
 * would have had a DOM — so the two lifecycle files opt *in* to jsdom per file
 * and this one stays where there is no `document` and no `globalThis`
 * `EventTarget`.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.7
 */
describe("createAbandonmentWatcher — no browser (T862)", () => {
  it("returns a no-op watcher instead of throwing", () => {
    expect(typeof document).toBe("undefined");
    expect(typeof globalThis.addEventListener).not.toBe("function");

    const fired: string[] = [];
    const watcher = createAbandonmentWatcher((r) => fired.push(r), { idleMs: 1000 });

    // Both handles are callable and idempotent — a caller written for the
    // browser needs no environment guard of its own.
    expect(() => {
      watcher.ping();
      watcher.stop();
      watcher.stop();
    }).not.toThrow();
    expect(fired).toEqual([]);
  });

  it("never arms the idle timer, so an SSR render cannot emit abandonment", () => {
    vi.useFakeTimers();
    try {
      const fired: string[] = [];
      const watcher = createAbandonmentWatcher((r) => fired.push(r), { idleMs: 1000 });
      vi.advanceTimersByTime(10_000);
      expect(fired).toEqual([]);
      watcher.stop();
    } finally {
      vi.useRealTimers();
    }
  });

  it("a chat controller still runs a full turn with no DOM", async () => {
    const sse = new Response(`data: ${JSON.stringify({ role: "assistant", content: "hi" })}\n`, {
      status: 200,
      headers: { "Content-Type": "text/event-stream" },
    });
    const client = { fetchRaw: vi.fn().mockResolvedValue(sse) } as unknown as TuringClient;
    const events: TuringAnalyticsEvent[] = [];
    const analytics = createTuringAnalytics({ sinks: [(e) => events.push(e)] });

    const chat = createChatController(client, {
      agent: { id: "a1", llmInstanceId: "l1" },
      conversationId: "conv1",
      analytics,
    });

    await chat.send("hello");
    expect(events.filter((e) => e.name === "turing_chat_abandoned")).toHaveLength(0);
    chat.destroy();
  });
});
