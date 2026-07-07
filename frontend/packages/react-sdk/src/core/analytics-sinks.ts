/**
 * T459 (Block Z) — pluggable analytics sinks for the {@link ./analytics} bus.
 *
 * The bus emits **canonical, vendor-neutral** events; a sink maps them to a
 * concrete destination. GA4 is the *first adapter*, not a dependency — the SDK
 * stays zero-dep / EDS-friendly. {@link googleAnalyticsSink} auto-detects an
 * existing `window.gtag` / `window.dataLayer`, so a GTM-tagged or Adobe EDS host
 * lights up with **zero extra config**. {@link onEventSink} is the generic
 * escape hatch (Segment / Matomo / Plausible later) and {@link debugSink} traces
 * to the console during integration.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import type {
  TuringAnalyticsContext,
  TuringAnalyticsEvent,
  TuringAnalyticsSink,
  TuringAnalyticsValue,
} from "./analytics";

/** GA4 `gtag` function shape (the slice we use). */
type GtagFn = (command: "event", name: string, params?: Record<string, unknown>) => void;

interface AnalyticsGlobals {
  gtag?: GtagFn;
  dataLayer?: unknown[];
}

/**
 * Identity-field → GA4-param-name map. Reserved GA4 param names are avoided:
 * notably GA owns `session_id`, so our cross-surface id is sent as
 * `turing_session_id`.
 */
const CONTEXT_PARAM_NAMES: Partial<Record<keyof TuringAnalyticsContext, string>> = {
  site: "site",
  agentId: "agent_id",
  conversationId: "conversation_id",
  sessionId: "turing_session_id",
  experimentKey: "experiment_key",
  variantLabel: "variant_label",
  personaId: "persona_id",
  personaName: "persona_name",
};

/**
 * Flattens an event's context envelope + params into a single GA4-friendly
 * param bag. Known identity fields are renamed to GA convention; any extra
 * context keys (host-stamped cohort/campaign dims) pass through verbatim.
 * Explicit event params win over context on a name clash.
 */
function toFlatParams(event: TuringAnalyticsEvent): Record<string, Exclude<TuringAnalyticsValue, undefined>> {
  const out: Record<string, Exclude<TuringAnalyticsValue, undefined>> = {};
  for (const [key, value] of Object.entries(event.context)) {
    if (value === undefined) continue;
    const mapped = CONTEXT_PARAM_NAMES[key as keyof TuringAnalyticsContext] ?? key;
    out[mapped] = value;
  }
  // Event params override context (e.g. a future per-event session override).
  for (const [key, value] of Object.entries(event.params)) {
    out[key] = value;
  }
  return out;
}

export interface GoogleAnalyticsSinkOptions {
  /**
   * GA4 measurement id (`G-XXXXXXXX`). Optional: when the host already loaded
   * `gtag.js` / GTM the id is configured there, so the sink only needs to call
   * `gtag('event', …)`. Pass it only when you want it stamped on every event.
   */
  readonly measurementId?: string;
  /**
   * Transport selection. `"auto"` (default) prefers an existing `window.gtag`,
   * else falls back to `window.dataLayer` (GTM). `"gtag"` / `"dataLayer"` force
   * one. `"both"` pushes to whichever of the two is present.
   */
  readonly transport?: "auto" | "gtag" | "dataLayer" | "both";
  /** Inject a `gtag` (tests / non-window hosts). Defaults to `window.gtag`. */
  readonly gtag?: GtagFn;
  /** Inject a `dataLayer` array. Defaults to `window.dataLayer`. */
  readonly dataLayer?: unknown[];
}

function resolveGlobals(): AnalyticsGlobals {
  const w = globalThis as unknown as AnalyticsGlobals;
  return { gtag: typeof w.gtag === "function" ? w.gtag : undefined, dataLayer: w.dataLayer };
}

/**
 * GA4 / GTM sink. Maps each canonical event to a GA4 `gtag('event', name,
 * params)` call and/or a GTM `dataLayer.push({ event: name, ... })`, resolving
 * the transport **lazily at emit time** so it still works when `gtag.js` loads
 * after the bus is constructed. No-ops cleanly when neither transport exists.
 */
export function googleAnalyticsSink(options: GoogleAnalyticsSinkOptions = {}): TuringAnalyticsSink {
  const { measurementId, transport = "auto", gtag: injectedGtag, dataLayer: injectedDataLayer } = options;

  return (event: TuringAnalyticsEvent) => {
    const globals = resolveGlobals();
    const gtag = injectedGtag ?? globals.gtag;
    const dataLayer = injectedDataLayer ?? globals.dataLayer;

    const params = toFlatParams(event);
    if (measurementId) params.send_to = measurementId;
    // T460 — the abandonment event fires during tab hide / unload, when a
    // normal XHR would be cancelled. GA4 honours `transport_type: 'beacon'` to
    // flush via `navigator.sendBeacon`, which survives the teardown.
    if (event.name === "turing_chat_abandoned") {
      (params as Record<string, unknown>).transport_type = "beacon";
    }

    const useGtag = transport === "gtag" || transport === "both" || (transport === "auto" && !!gtag);
    const useDataLayer =
      transport === "dataLayer" ||
      transport === "both" ||
      (transport === "auto" && !gtag && !!dataLayer);

    if (useGtag && gtag) {
      gtag("event", event.name, params);
    }
    if (useDataLayer && dataLayer) {
      dataLayer.push({ event: event.name, ...params });
    }
  };
}

/**
 * Generic sink — hands every canonical event to a callback. The escape hatch
 * for Segment / Matomo / Plausible or any custom pipeline.
 */
export function onEventSink(fn: (event: TuringAnalyticsEvent) => void): TuringAnalyticsSink {
  return (event) => fn(event);
}

export interface DebugSinkOptions {
  /** Custom logger; defaults to `console.debug`. */
  readonly log?: (message: string, event: TuringAnalyticsEvent) => void;
  /** Console prefix. Defaults to `[Turing Analytics]`. */
  readonly prefix?: string;
}

/**
 * Console-tracing sink for integration. Logs `name` + flattened params so you
 * can confirm events fire (and what GA4 would receive) before wiring a real
 * destination.
 */
export function debugSink(options: DebugSinkOptions = {}): TuringAnalyticsSink {
  const { log, prefix = "[Turing Analytics]" } = options;
  return (event) => {
    if (log) {
      log(`${prefix} ${event.name}`, event);
      return;
    }
    if (typeof console !== "undefined") {
      console.debug(`${prefix} ${event.name}`, toFlatParams(event));
    }
  };
}
