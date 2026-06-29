import { describe, expect, it, vi } from "vitest";
import { createTuringAnalytics, TURING_ANALYTICS_EVENTS } from "../analytics";
import { debugSink, googleAnalyticsSink, onEventSink } from "../analytics-sinks";

/**
 * T459 (Block Z) — GA4/GTM + generic + debug sinks.
 */
describe("googleAnalyticsSink (T459)", () => {
  it("maps a canonical event to gtag('event', name, params) with renamed context", () => {
    const gtag = vi.fn();
    const analytics = createTuringAnalytics({
      sinks: [googleAnalyticsSink({ gtag })],
      context: { site: "s1", sessionId: "sess1", experimentKey: "exp", variantLabel: "B", personaId: "p1" },
    });

    analytics.emit(TURING_ANALYTICS_EVENTS.chatLeadCaptured, { goal_slot: "email" });

    expect(gtag).toHaveBeenCalledWith("event", "turing_chat_lead_captured", {
      site: "s1",
      // GA owns `session_id`, so our id is namespaced
      turing_session_id: "sess1",
      experiment_key: "exp",
      variant_label: "B",
      persona_id: "p1",
      goal_slot: "email",
    });
  });

  it("pushes to dataLayer when no gtag exists (GTM auto-detect)", () => {
    const dataLayer: unknown[] = [];
    const analytics = createTuringAnalytics({
      sinks: [googleAnalyticsSink({ dataLayer })],
      context: { site: "s1" },
    });
    analytics.emit(TURING_ANALYTICS_EVENTS.search, { query: "boots", results: 3 });

    expect(dataLayer).toHaveLength(1);
    expect(dataLayer[0]).toEqual({
      event: "turing_search",
      site: "s1",
      query: "boots",
      results: 3,
    });
  });

  it("stamps send_to when a measurementId is given", () => {
    const gtag = vi.fn();
    const analytics = createTuringAnalytics({
      sinks: [googleAnalyticsSink({ gtag, measurementId: "G-ABC123" })],
    });
    analytics.emit(TURING_ANALYTICS_EVENTS.chatStart);
    expect(gtag.mock.calls[0][2]).toMatchObject({ send_to: "G-ABC123" });
  });

  it("no-ops when neither gtag nor dataLayer is present", () => {
    const analytics = createTuringAnalytics({ sinks: [googleAnalyticsSink()] });
    expect(() => analytics.emit(TURING_ANALYTICS_EVENTS.search)).not.toThrow();
  });

  it("transport: 'both' fans out to gtag and dataLayer", () => {
    const gtag = vi.fn();
    const dataLayer: unknown[] = [];
    const analytics = createTuringAnalytics({
      sinks: [googleAnalyticsSink({ gtag, dataLayer, transport: "both" })],
    });
    analytics.emit(TURING_ANALYTICS_EVENTS.chatStart);
    expect(gtag).toHaveBeenCalledOnce();
    expect(dataLayer).toHaveLength(1);
  });
});

describe("onEventSink + debugSink (T459)", () => {
  it("onEventSink forwards every event", () => {
    const fn = vi.fn();
    const analytics = createTuringAnalytics({ sinks: [onEventSink(fn)] });
    analytics.emit(TURING_ANALYTICS_EVENTS.searchNoResults, { query: "zzz" });
    expect(fn).toHaveBeenCalledOnce();
    expect(fn.mock.calls[0][0].name).toBe("turing_search_no_results");
  });

  it("debugSink uses a custom logger when provided", () => {
    const log = vi.fn();
    const analytics = createTuringAnalytics({ sinks: [debugSink({ log })] });
    analytics.emit(TURING_ANALYTICS_EVENTS.chatHandoff, { channel: "whatsapp" });
    expect(log).toHaveBeenCalledOnce();
    expect(log.mock.calls[0][0]).toContain("turing_chat_handoff");
  });
});
