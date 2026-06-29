/**
 * T458 — canonical client-side analytics event bus (Block Z).
 *
 * Turing measures the conversion funnel **server-side** (`TurChatSessionEvent`
 * outcome + experiment/variant/persona, the A/B stack T67–T75). What this adds
 * is the **browser-owned** half: lead conversion, drop-off/abandonment, and
 * "which persona A/B variation was preferred", emitted at the lifecycle moments
 * only the client can see (a tab close is invisible to the server).
 *
 * <p>The bus is a zero-dependency emitter with a fixed, provider-agnostic
 * **event taxonomy** so dashboards/funnels stay portable across destinations.
 * It carries NO knowledge of Google Analytics or any vendor — those are
 * **pluggable sinks** (T459, {@link ./analytics-sinks}). When no `analytics`
 * option is passed to a controller the whole thing is absent, so every existing
 * embed is byte-for-byte unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

/** A GA4-friendly scalar parameter value. `undefined` keys are dropped before dispatch. */
export type TuringAnalyticsValue = string | number | boolean | undefined;

/**
 * The canonical, provider-agnostic event names. Sinks (GA4, Segment, …) map
 * these verbatim, so a funnel built on `turing_chat_*` is portable.
 */
export const TURING_ANALYTICS_EVENTS = {
  /** A conversation began (first user message of the session). */
  chatStart: "turing_chat_start",
  /** A user message was sent (carries the running turn count). */
  chatMessageSent: "turing_chat_message_sent",
  /** A flow node was reached — the client mirror of the T85 server funnel. */
  chatStep: "turing_chat_step",
  /** A lead/goal was captured (goal slot written, form submit, or server `CAPTURED`). */
  chatLeadCaptured: "turing_chat_lead_captured",
  /** A started conversation ended without conversion (tab close / idle). */
  chatAbandoned: "turing_chat_abandoned",
  /** The conversation was handed off to a human / external channel (T55/T62). */
  chatHandoff: "turing_chat_handoff",
  /** The A/B arm for this visitor resolved (experiment/variant/persona). */
  abVariantAssigned: "turing_ab_variant_assigned",
  /** A search query was executed (carries the result count). */
  search: "turing_search",
  /** A search returned zero results — the client-side content-gap signal (feeds T252). */
  searchNoResults: "turing_search_no_results",
  /** A search result was clicked (mirrors the `postClick` CTR). */
  searchResultClick: "turing_search_result_click",
  /** A query was reformulated after an earlier search in the same session. */
  searchRefined: "turing_search_refined",
} as const;

/** Union of the canonical event names. */
export type TuringAnalyticsEventName =
  (typeof TURING_ANALYTICS_EVENTS)[keyof typeof TURING_ANALYTICS_EVENTS];

/**
 * The common envelope stamped onto every event so sinks never re-derive it.
 * Identity fields use camelCase here; the GA4 sink (T459) snake_cases them to
 * GA convention (`session_id`, `experiment_key`, …). Extra keys are allowed so
 * a host can stamp campaign/cohort dims.
 */
export interface TuringAnalyticsContext {
  /** SN site name (search / site-mode chat). */
  site?: string;
  /** AI agent id (agent-mode chat). */
  agentId?: string;
  /** Active conversation id (== the `TUR_SESSION` value by default). */
  conversationId?: string;
  /** Cross-surface session id from the `TUR_SESSION` cookie — stitches search → chat. */
  sessionId?: string;
  /** T461 — A/B experiment key, stamped on every event once resolved. */
  experimentKey?: string;
  /** T461 — A/B variant label, stamped on every event once resolved. */
  variantLabel?: string;
  /** T461 — active persona id, stamped on every event once resolved. */
  personaId?: string;
  /** T461 — active persona display name. */
  personaName?: string;
  [key: string]: TuringAnalyticsValue;
}

/** A fully-formed analytics event handed to every sink. */
export interface TuringAnalyticsEvent {
  /** Canonical event name. */
  readonly name: TuringAnalyticsEventName;
  /** Event-specific parameters (already free of `undefined` values). */
  readonly params: Readonly<Record<string, Exclude<TuringAnalyticsValue, undefined>>>;
  /** The envelope at emit time (snapshot — sinks may read but not mutate it). */
  readonly context: Readonly<TuringAnalyticsContext>;
  /** Unix ms when the event was emitted. */
  readonly timestamp: number;
}

/**
 * A destination for canonical events. A sink may forward to GA4, push to a
 * `dataLayer`, log to the console, or call an arbitrary callback. Sinks must
 * never throw into the caller — the bus isolates each one.
 */
export type TuringAnalyticsSink = (event: TuringAnalyticsEvent) => void;

export interface TuringAnalyticsOptions {
  /** Destinations for every emitted event. May be empty (a pure no-op bus). */
  readonly sinks?: ReadonlyArray<TuringAnalyticsSink>;
  /** Seed envelope merged into every event (e.g. `{ site }` known at construction). */
  readonly context?: TuringAnalyticsContext;
}

/**
 * The analytics emitter returned by {@link createTuringAnalytics}. Controllers
 * receive this via their `analytics` option and call {@link emit} at lifecycle
 * points; the host wires {@link setContext} (or the controllers do, for the A/B
 * envelope) so attribution dims ride along on every subsequent event.
 */
export interface TuringAnalytics {
  /**
   * Emits a canonical event. `undefined` params are stripped; the current
   * context envelope is snapshotted in. Dispatch to sinks is best-effort —
   * a throwing sink is isolated and does not break the others or the caller.
   */
  emit(name: TuringAnalyticsEventName, params?: Record<string, TuringAnalyticsValue>): void;
  /** Shallow-merges `patch` into the context envelope stamped on future events. */
  setContext(patch: TuringAnalyticsContext): void;
  /** Current immutable context snapshot. */
  getContext(): Readonly<TuringAnalyticsContext>;
  /** Adds a sink at runtime. Returns a remover. */
  addSink(sink: TuringAnalyticsSink): () => void;
}

/** Drops `undefined`-valued keys so sinks (and GA4) only see real params. */
function compactParams(
  params: Record<string, TuringAnalyticsValue> | undefined,
): Record<string, Exclude<TuringAnalyticsValue, undefined>> {
  const out: Record<string, Exclude<TuringAnalyticsValue, undefined>> = {};
  if (!params) return out;
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined) out[k] = v;
  }
  return out;
}

/**
 * Creates a canonical analytics bus. Pure logic, zero dependencies, safe to
 * construct in SSR (it never touches `window`). Pass it to the chat / search /
 * slots controllers via their `analytics` option to light up client-side
 * conversion measurement; attach a {@link TuringAnalyticsSink} (e.g.
 * `googleAnalyticsSink()`) to route events to a destination.
 *
 * @example
 * ```js
 * const analytics = createTuringAnalytics({
 *   sinks: [googleAnalyticsSink(), debugSink()],
 *   context: { site: "my-site" },
 * });
 * const chat = createChatController(client, { site: "my-site", analytics });
 * ```
 */
export function createTuringAnalytics(options: TuringAnalyticsOptions = {}): TuringAnalytics {
  const sinks = new Set<TuringAnalyticsSink>(options.sinks ?? []);
  let context: TuringAnalyticsContext = { ...(options.context ?? {}) };

  function dispatch(event: TuringAnalyticsEvent): void {
    for (const sink of Array.from(sinks)) {
      try {
        sink(event);
      } catch {
        // A sink's failure must never break the others or the caller.
      }
    }
  }

  return {
    emit(name, params) {
      dispatch({
        name,
        params: compactParams(params),
        context: { ...context },
        timestamp: Date.now(),
      });
    },
    setContext(patch) {
      context = { ...context, ...patch };
    },
    getContext() {
      return { ...context };
    },
    addSink(sink) {
      sinks.add(sink);
      return () => {
        sinks.delete(sink);
      };
    },
  };
}
