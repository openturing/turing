import { describe, expect, it, vi } from "vitest";
import { createTuringAnalytics, type TuringAnalyticsEvent } from "../analytics";
import { createChatController } from "../controllers/chat";
import type { TuringClient } from "../client";

/**
 * T461 (Block Z) — funnel steps, conversion, and A/B attribution.
 *
 * Site mode: after each turn the controller reads `/chat/state` (+ `/chat/slots`
 * when goal slots are configured) and emits `turing_chat_step`,
 * `turing_ab_variant_assigned` (stamping the bus context), and
 * `turing_chat_lead_captured`.
 */
describe("createChatController — conversion & attribution (T461)", () => {
  function sse(content: string): Response {
    return new Response(`data: ${JSON.stringify({ role: "assistant", content })}\n`, {
      status: 200,
      headers: { "Content-Type": "text/event-stream" },
    });
  }

  /** Builds a site-mode client whose GET routes by URL. */
  function siteClient(opts: {
    state?: Partial<Record<string, unknown>>;
    slots?: Record<string, string>;
  }) {
    const get = vi.fn((url: string) => {
      if (url.includes("/chat/enabled")) return Promise.resolve({ enabled: true });
      if (url.includes("/chat/state")) {
        return Promise.resolve({
          conversationId: "conv1",
          flowId: "f1",
          flowName: "Lead Flow",
          currentNodeId: "node-A",
          guardrailMethod: null,
          experimentKey: null,
          variantLabel: null,
          personaId: null,
          ...opts.state,
        });
      }
      if (url.includes("/chat/slots")) return Promise.resolve({ slots: opts.slots ?? {} });
      return Promise.resolve(null);
    });
    const fetchRaw = vi.fn().mockResolvedValue(sse("hello"));
    return { get, fetchRaw } as unknown as TuringClient;
  }

  const flush = () => new Promise((r) => setTimeout(r, 0));

  it("emits a step event when the flow cursor advances", async () => {
    const client = siteClient({ state: { currentNodeId: "node-A" } });
    const events: TuringAnalyticsEvent[] = [];
    const analytics = createTuringAnalytics({ sinks: [(e) => events.push(e)] });
    const chat = createChatController(client, { site: "s1", conversationId: "conv1", analytics });

    await chat.send("hi");
    await flush();

    const steps = events.filter((e) => e.name === "turing_chat_step");
    expect(steps).toHaveLength(1);
    expect(steps[0].params).toMatchObject({ node_id: "node-A", flow_name: "Lead Flow", step: 1 });
  });

  it("emits ab_variant_assigned once and stamps experiment/variant/persona on later events", async () => {
    const client = siteClient({
      state: { experimentKey: "exp1", variantLabel: "B", personaId: "p1" },
    });
    const events: TuringAnalyticsEvent[] = [];
    const analytics = createTuringAnalytics({ sinks: [(e) => events.push(e)] });
    const chat = createChatController(client, { site: "s1", conversationId: "conv1", analytics });

    await chat.send("first");
    await flush();
    await chat.send("second");
    await flush();

    const assigned = events.filter((e) => e.name === "turing_ab_variant_assigned");
    expect(assigned).toHaveLength(1);
    expect(assigned[0].context).toMatchObject({ experimentKey: "exp1", variantLabel: "B", personaId: "p1" });

    // every subsequent event now carries the stamp via the bus context
    const lastSent = events.filter((e) => e.name === "turing_chat_message_sent").at(-1)!;
    expect(lastSent.context).toMatchObject({ experimentKey: "exp1", variantLabel: "B", personaId: "p1" });
  });

  it("emits lead_captured when a goal slot is written", async () => {
    const client = siteClient({ slots: { email: "a@b.com" } });
    const events: TuringAnalyticsEvent[] = [];
    const analytics = createTuringAnalytics({ sinks: [(e) => events.push(e)] });
    const chat = createChatController(client, {
      site: "s1",
      conversationId: "conv1",
      analytics,
      goalSlots: ["email", "phone"],
    });

    await chat.send("my email is a@b.com");
    await flush();

    const leads = events.filter((e) => e.name === "turing_chat_lead_captured");
    expect(leads).toHaveLength(1);
    expect(leads[0].params).toMatchObject({ reason: "goal_slot", goal_slot: "email" });
  });

  it("a captured lead suppresses abandonment", async () => {
    const client = siteClient({ slots: { email: "a@b.com" } });
    const events: TuringAnalyticsEvent[] = [];
    const analytics = createTuringAnalytics({ sinks: [(e) => events.push(e)] });
    const chat = createChatController(client, {
      site: "s1",
      conversationId: "conv1",
      analytics,
      goalSlots: ["email"],
    });

    await chat.send("a@b.com");
    await flush();
    globalThis.dispatchEvent(new Event("pagehide"));

    expect(events.filter((e) => e.name === "turing_chat_lead_captured")).toHaveLength(1);
    expect(events.filter((e) => e.name === "turing_chat_abandoned")).toHaveLength(0);
    chat.destroy();
  });
});
