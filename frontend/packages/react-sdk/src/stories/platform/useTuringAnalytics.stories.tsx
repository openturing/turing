import type { Meta, StoryObj } from "@storybook/react-vite";
import { useCallback, useMemo, useRef, useState } from "react";
import {
  TURING_ANALYTICS_EVENTS,
  type TuringAnalyticsContext,
  type TuringAnalyticsEventName,
  type TuringAnalyticsValue,
} from "../../core/analytics";

/**
 * # useTuringAnalytics
 *
 * React entry point for Turing's **client-side analytics bus** (Block Z). It
 * returns a memoized, vendor-neutral event emitter that controllers
 * (`useTuringChat` / `useTuringSearch`) call at lifecycle moments only the
 * browser can see — lead conversion, drop-off/abandonment, and "which A/B
 * persona was preferred". The server already measures the funnel; this fills the
 * client half (a tab close is invisible to the backend).
 *
 * Events carry **canonical, provider-agnostic names** (`turing_search`,
 * `turing_chat_lead_captured`, …) so a funnel stays portable across
 * destinations. Destinations are **pluggable sinks**: `googleAnalyticsSink`
 * (GA4/GTM, auto-detects `window.gtag` / `window.dataLayer`), `onEventSink`
 * (Segment / Matomo / Plausible escape hatch), and `debugSink` (console trace).
 *
 * ## Key Features
 * - **Memoized bus** — created exactly once; stable across renders so it never
 *   drops accumulated A/B context or re-arms sinks.
 * - **GA4 auto-detect** — a GTM-tagged or Adobe EDS host lights up with zero
 *   extra config; pass `debug: true` to also trace to the console.
 * - **Canonical taxonomy** — fixed event names in `TURING_ANALYTICS_EVENTS`
 *   (`chatStart`, `chatLeadCaptured`, `search`, `searchResultClick`, …).
 * - **Context envelope** — `site`, `agentId`, `conversationId`, `sessionId` plus
 *   the A/B dims (`experimentKey` / `variantLabel` / `personaId`) ride along on
 *   every event; the surrounding `<TuringProvider>` site is seeded in.
 * - **Sink isolation** — a throwing sink never breaks the others or the caller.
 *
 * ## Usage
 * ```tsx
 * import { useTuringAnalytics, useTuringChat, useTuringSearch } from "@viglet/turing-react-sdk";
 *
 * // GA4 auto-detected; also push to Segment + trace to console while integrating.
 * const { analytics, emit } = useTuringAnalytics({
 *   googleAnalytics: true,                 // auto-detect window.gtag / dataLayer
 *   debug: true,                           // console.debug every event
 *   sinks: [onEventSink((e) => segment.track(e.name, e.params))],
 *   context: { campaign: "spring-promo" }, // extra dims stamped on every event
 * });
 *
 * // Wire the bus into the controllers — they emit at lifecycle points for you.
 * const chat = useTuringChat({ site: "store", analytics });
 * const search = useTuringSearch(undefined, { analytics });
 *
 * // …or emit manually from your own UI:
 * emit(TURING_ANALYTICS_EVENTS.searchResultClick, { query: "headphones", position: 2 });
 * ```
 *
 * ## When to use
 * - **Conversion + drop-off measurement** the server can't see (tab close, idle
 *   abandonment, GA4 `transport_type: 'beacon'` flush on unload).
 * - **GA4 / GTM dashboards** on a marketing site or EDS block — auto-detected.
 * - **A/B persona reporting** — stamp the resolved variant on every event so the
 *   funnel splits by arm.
 * - **Multi-destination fan-out** — GA4 + Segment + a custom pipeline at once.
 *
 * ## About this story
 * Fully self-contained: **no real hook, Provider, or network**. A local
 * "debug-sink" mirror reproduces the bus contract (`emit` snapshots params +
 * the context envelope + a timestamp). Buttons fire the **real** canonical
 * events; each lands in a live, timestamped console-style feed with its name and
 * pretty-printed payload — exactly what `debugSink()` would log and what GA4
 * would receive.
 */

/** Mirror of {@link TuringAnalyticsEvent} for the self-contained demo feed. */
interface CapturedEvent {
  readonly seq: number;
  readonly name: TuringAnalyticsEventName;
  readonly params: Readonly<Record<string, Exclude<TuringAnalyticsValue, undefined>>>;
  readonly context: Readonly<TuringAnalyticsContext>;
  readonly timestamp: number;
}

/** GA4-ish color coding so search vs chat vs A/B events read at a glance. */
function eventAccent(name: TuringAnalyticsEventName): string {
  if (name.startsWith("turing_search")) return "#0ea5e9"; // sky — discovery
  if (name === TURING_ANALYTICS_EVENTS.chatLeadCaptured) return "#22c55e"; // green — conversion
  if (name === TURING_ANALYTICS_EVENTS.chatAbandoned) return "#f43f5e"; // rose — drop-off
  if (name === TURING_ANALYTICS_EVENTS.abVariantAssigned) return "#a855f7"; // violet — A/B
  return "#4f46e5"; // indigo — chat
}

interface DemoButton {
  readonly label: string;
  readonly emoji: string;
  readonly name: TuringAnalyticsEventName;
  /** Built lazily so counters/queries stay fresh at click time. */
  readonly params: () => Record<string, TuringAnalyticsValue>;
}

const SAMPLE_QUERIES: ReadonlyArray<string> = [
  "noise-cancelling headphones",
  "wireless keyboard",
  "4k monitor",
  "mechanical switches",
];

function AnalyticsInspector({
  dark,
  site,
  withAbContext,
}: {
  dark: boolean;
  site: string;
  withAbContext: boolean;
}) {
  const [events, setEvents] = useState<ReadonlyArray<CapturedEvent>>([]);
  const seqRef = useRef(0);
  const turnRef = useRef(0);
  const queryRef = useRef(0);

  // The seed envelope — exactly what useTuringAnalytics merges from the
  // <TuringProvider> site plus the resolved A/B dims (T461).
  const context = useMemo<TuringAnalyticsContext>(
    () => ({
      site,
      conversationId: "conv_8f31",
      sessionId: "TUR_a1b2c3",
      ...(withAbContext
        ? {
            experimentKey: "persona_tone_v2",
            variantLabel: "consultative",
            personaId: "p_advisor",
            personaName: "Advisor",
          }
        : {}),
    }),
    [site, withAbContext],
  );

  /** The self-contained "debug sink" — mirrors createTuringAnalytics().emit. */
  const emit = useCallback(
    (name: TuringAnalyticsEventName, params: Record<string, TuringAnalyticsValue>) => {
      const compact: Record<string, Exclude<TuringAnalyticsValue, undefined>> = {};
      for (const [k, v] of Object.entries(params)) {
        if (v !== undefined) compact[k] = v;
      }
      seqRef.current += 1;
      const captured: CapturedEvent = {
        seq: seqRef.current,
        name,
        params: compact,
        context: { ...context },
        timestamp: Date.now(),
      };
      setEvents((prev) => [captured, ...prev].slice(0, 40));
    },
    [context],
  );

  const buttons = useMemo<ReadonlyArray<DemoButton>>(
    () => [
      {
        label: "Search",
        emoji: "🔎",
        name: TURING_ANALYTICS_EVENTS.search,
        params: () => {
          const q = SAMPLE_QUERIES[queryRef.current % SAMPLE_QUERIES.length];
          queryRef.current += 1;
          return { query: q, results: Math.floor(Math.random() * 40) + 1 };
        },
      },
      {
        label: "No results",
        emoji: "🚫",
        name: TURING_ANALYTICS_EVENTS.searchNoResults,
        params: () => ({ query: "qwertyzxcv", results: 0 }),
      },
      {
        label: "Result click",
        emoji: "👆",
        name: TURING_ANALYTICS_EVENTS.searchResultClick,
        params: () => ({
          query: SAMPLE_QUERIES[Math.max(0, (queryRef.current - 1) % SAMPLE_QUERIES.length)],
          position: Math.floor(Math.random() * 6) + 1,
          documentId: `doc_${Math.floor(Math.random() * 9000) + 1000}`,
        }),
      },
      {
        label: "Refine query",
        emoji: "✏️",
        name: TURING_ANALYTICS_EVENTS.searchRefined,
        params: () => ({ from: "headphones", to: "noise-cancelling headphones" }),
      },
      {
        label: "Chat start",
        emoji: "💬",
        name: TURING_ANALYTICS_EVENTS.chatStart,
        params: () => {
          turnRef.current = 0;
          return { agentId: "store-assistant" };
        },
      },
      {
        label: "Message sent",
        emoji: "✉️",
        name: TURING_ANALYTICS_EVENTS.chatMessageSent,
        params: () => {
          turnRef.current += 1;
          return { turn: turnRef.current };
        },
      },
      {
        label: "Flow step",
        emoji: "🪜",
        name: TURING_ANALYTICS_EVENTS.chatStep,
        params: () => ({ node: "collect_email", step: turnRef.current }),
      },
      {
        label: "Lead captured",
        emoji: "🎯",
        name: TURING_ANALYTICS_EVENTS.chatLeadCaptured,
        params: () => ({ goal: "demo_request", turns: turnRef.current }),
      },
      {
        label: "Handoff",
        emoji: "🤝",
        name: TURING_ANALYTICS_EVENTS.chatHandoff,
        params: () => ({ channel: "human_agent" }),
      },
      {
        label: "Abandoned",
        emoji: "🚪",
        name: TURING_ANALYTICS_EVENTS.chatAbandoned,
        params: () => ({ reason: "tab_close", turns: turnRef.current }),
      },
      {
        label: "A/B assigned",
        emoji: "🧪",
        name: TURING_ANALYTICS_EVENTS.abVariantAssigned,
        params: () => ({ experimentKey: "persona_tone_v2", variantLabel: "consultative" }),
      },
    ],
    [],
  );

  const fmtTime = (ts: number) =>
    new Date(ts).toLocaleTimeString(undefined, { hour12: false }) +
    "." +
    String(ts % 1000).padStart(3, "0");

  // Theme tokens — light card, dark "console" panel for the feed.
  const surface = dark ? "#0b1020" : "#ffffff";
  const panelBg = dark ? "#070b16" : "#0f172a";
  const subtleText = dark ? "#94a3b8" : "#64748b";
  const cardBorder = dark ? "#1e293b" : "#e2e8f0";
  const headText = dark ? "#e2e8f0" : "#0f172a";

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        width: "640px",
        maxWidth: "100%",
        background: surface,
        borderRadius: "16px",
        border: `1px solid ${cardBorder}`,
        overflow: "hidden",
      }}
    >
      {/* Header — Viglet gradient */}
      <div
        style={{
          padding: "16px 20px",
          background: "linear-gradient(90deg, #2563eb 0%, #4f46e5 100%)",
          color: "white",
        }}
      >
        <div style={{ fontSize: "15px", fontWeight: 700, display: "flex", alignItems: "center", gap: "8px" }}>
          📊 Turing Analytics — Event Inspector
        </div>
        <div style={{ fontSize: "12px", opacity: 0.9, marginTop: "2px" }}>
          Fire a canonical event → watch it stream into the debug sink below.
        </div>
      </div>

      {/* Context envelope readout */}
      <div
        style={{
          padding: "10px 20px",
          background: dark ? "#0e1426" : "#f8fafc",
          borderBottom: `1px solid ${cardBorder}`,
          fontSize: "11px",
          color: subtleText,
          display: "flex",
          flexWrap: "wrap",
          gap: "6px 14px",
        }}
      >
        <strong style={{ color: headText }}>context envelope:</strong>
        {Object.entries(context).map(([k, v]) => (
          <span key={k}>
            <code style={{ color: dark ? "#7dd3fc" : "#0369a1" }}>{k}</code>=
            <code style={{ color: headText }}>{String(v)}</code>
          </span>
        ))}
      </div>

      {/* Trigger buttons */}
      <div
        style={{
          padding: "14px 20px",
          display: "flex",
          flexWrap: "wrap",
          gap: "8px",
          borderBottom: `1px solid ${cardBorder}`,
        }}
      >
        {buttons.map((b) => (
          <button
            key={b.name}
            type="button"
            onClick={() => emit(b.name, b.params())}
            title={b.name}
            style={{
              padding: "7px 12px",
              borderRadius: "999px",
              border: `1px solid ${eventAccent(b.name)}`,
              background: dark ? "transparent" : "#ffffff",
              color: eventAccent(b.name),
              fontSize: "12px",
              fontWeight: 600,
              cursor: "pointer",
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
            }}
          >
            <span aria-hidden>{b.emoji}</span>
            {b.label}
          </button>
        ))}
      </div>

      {/* Toolbar */}
      <div
        style={{
          padding: "8px 20px",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          background: dark ? "#0e1426" : "#f1f5f9",
          fontSize: "11px",
          color: subtleText,
        }}
      >
        <span>
          <strong style={{ color: headText }}>{events.length}</strong> event
          {events.length === 1 ? "" : "s"} captured (newest first, capped at 40)
        </span>
        <button
          type="button"
          onClick={() => {
            setEvents([]);
            seqRef.current = 0;
          }}
          disabled={events.length === 0}
          style={{
            padding: "4px 10px",
            borderRadius: "6px",
            border: `1px solid ${cardBorder}`,
            background: "transparent",
            color: events.length === 0 ? subtleText : "#f43f5e",
            cursor: events.length === 0 ? "default" : "pointer",
            fontSize: "11px",
            fontWeight: 600,
          }}
        >
          Clear
        </button>
      </div>

      {/* Console feed (the debug sink) */}
      <div
        style={{
          background: panelBg,
          padding: "12px",
          maxHeight: "320px",
          overflowY: "auto",
          fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
          fontSize: "12px",
        }}
      >
        {events.length === 0 ? (
          <div style={{ color: "#64748b", padding: "20px", textAlign: "center" }}>
            No events yet — click a button above to emit one.
          </div>
        ) : (
          events.map((e) => (
            <div
              key={e.seq}
              style={{
                padding: "8px 10px",
                marginBottom: "6px",
                borderRadius: "8px",
                background: "rgba(255,255,255,0.03)",
                borderLeft: `3px solid ${eventAccent(e.name)}`,
              }}
            >
              <div style={{ display: "flex", alignItems: "baseline", gap: "8px" }}>
                <span style={{ color: "#475569" }}>{fmtTime(e.timestamp)}</span>
                <span style={{ color: eventAccent(e.name), fontWeight: 700 }}>{e.name}</span>
              </div>
              <pre
                style={{
                  margin: "4px 0 0",
                  color: "#cbd5e1",
                  whiteSpace: "pre-wrap",
                  wordBreak: "break-word",
                }}
              >
                {JSON.stringify(e.params, null, 2)}
              </pre>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

const meta: Meta<typeof AnalyticsInspector> = {
  title: "Platform/useTuringAnalytics",
  component: AnalyticsInspector,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Live event inspector for the Turing analytics bus. Self-contained simulation: a local `debugSink` mirror reproduces `createTuringAnalytics().emit` (compact params + context-envelope snapshot + timestamp). Buttons fire the real canonical events from `TURING_ANALYTICS_EVENTS`; each streams into the console feed exactly as `debugSink()` would log it and as `googleAnalyticsSink()` would forward it to GA4. No real hook, Provider, or network.",
      },
    },
  },
  argTypes: {
    dark: {
      description: "Render the card in dark mode (the console feed is always dark).",
      control: { type: "boolean" },
    },
    site: {
      description: "SN site name seeded into the context envelope (from <TuringProvider> in real usage).",
      control: { type: "text" },
    },
    withAbContext: {
      description: "Stamp the resolved A/B dims (experimentKey / variantLabel / persona) on every event (T461).",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof AnalyticsInspector>;

export const LightInspector: Story = {
  name: "📊 Event inspector (light)",
  args: { dark: false, site: "atlas-store", withAbContext: false },
};

export const DarkConsole: Story = {
  name: "📊 Event inspector (dark console)",
  args: { dark: true, site: "atlas-store", withAbContext: false },
};

export const WithAbContext: Story = {
  name: "🧪 With A/B context envelope (T461)",
  args: { dark: true, site: "atlas-store", withAbContext: true },
};
