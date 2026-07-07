import type { Meta, StoryObj } from "@storybook/react-vite";
import { useEffect, useRef, useState } from "react";
import type { TurDocument } from "../../core/types";
import { creatureDocuments, resolveDocsMock } from "../__mocks__/fixtures";

/**
 * # useTuringProactiveCopilot
 *
 * An **ambient / proactive** copilot. Unlike a reactive chat hook (where the
 * user types and the agent answers), this hook surfaces *unsolicited* offers:
 * the agent watches the conversation's ambient interaction signals — clicks,
 * dwell time, the page the user is lingering on — and, when it spots an
 * opportunity, pushes the **next thing the user probably wants to ask**.
 *
 * Under the hood it subscribes to the server's proactive SSE stream
 * (`GET /sn/{site}/chat/proactive/stream`). The host writes ambient signals as
 * `signal.<kind>` slots (from clicks/dwell); the agent — when it has
 * `proactiveEnabled` — replies on the `proactive-suggestion` event. The server
 * throttles to **one offer per window**, so the hook holds at most one pending
 * `suggestion` at a time. Call `dismiss()` once the user accepts or ignores it.
 *
 * ## Key Features
 * - **Anticipatory, not reactive**: the offer arrives without the user typing
 *   anything — driven by what they're browsing.
 * - **Single pending offer**: server-throttled to one suggestion per window;
 *   `suggestion` is `TuringProactiveSuggestion | null`.
 * - **`suggestedPrompt` is ready to send**: accept → pipe `suggestedPrompt`
 *   straight into your chat composer; the `message` is the human-friendly nudge.
 * - **`dismiss()`** clears the current offer (after accept or ignore).
 * - **Signal-aware**: each suggestion carries the `signal` + `count` that
 *   triggered it, so you can explain *why* the copilot is offering it.
 * - **Cheap to gate**: pass `enabled: false` (e.g. while a panel is closed) to
 *   tear down the EventSource; falls back to the `TUR_SESSION` cookie for the
 *   conversation id.
 *
 * ## Usage
 * ```tsx
 * const { suggestion, dismiss } = useTuringProactiveCopilot({
 *   conversationId: convId, // optional — defaults to the TUR_SESSION cookie
 *   enabled: panelOpen,     // optional — defaults to true
 * });
 *
 * if (!suggestion) return null;
 * return (
 *   <ProactiveBanner
 *     onAccept={() => {
 *       chat.send(suggestion.suggestedPrompt);
 *       dismiss();
 *     }}
 *     onClose={dismiss}
 *   >
 *     {suggestion.message}
 *   </ProactiveBanner>
 * );
 * ```
 *
 * ## When to use
 * - **Reduce dead-ends**: a visitor lingering on a product/creature page often
 *   doesn't know what to ask — surface the obvious next question for them.
 * - **Guided discovery**: "Compare with similar…", "Show weaknesses",
 *   "Summon lore" — turn passive browsing into a conversation.
 * - **Conversion nudges**: offer the high-intent prompt at the moment of dwell.
 *
 * Do **not** use it as a chat transport — it only *offers*; sending the
 * accepted `suggestedPrompt` is still the job of your chat hook.
 *
 * ## About this story
 * Fully self-contained — **no real hook, Provider, or network**. It simulates
 * the ambient loop: pick a creature (that's the "page you're viewing"), the
 * faux signal counter ticks up as you "dwell", and after a short window a
 * proactive `TuringProactiveSuggestion` fades in. Switch creatures to watch the
 * copilot recompute and refresh its offers. Accepting an offer logs the
 * `suggestedPrompt` that a real chat hook would send, then dismisses it.
 */

/* ── Local mirror of the hook's public type (story is self-contained) ── */
interface TuringProactiveSuggestion {
  conversationId: string;
  signal: string;
  count: number;
  message: string;
  suggestedPrompt: string;
}

const GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";
const CONVERSATION_ID = "story-conv-7f3a";

/** The three anticipatory offers, themed for Mythical Creatures. */
function buildSuggestions(creatureTitle: string): TuringProactiveSuggestion[] {
  return [
    {
      conversationId: CONVERSATION_ID,
      signal: "signal.dwell",
      count: 1,
      message: `⚔️ Compare ${creatureTitle} with similar beasts`,
      suggestedPrompt: `Compare ${creatureTitle} with other creatures that share its element and danger level.`,
    },
    {
      conversationId: CONVERSATION_ID,
      signal: "signal.scroll",
      count: 2,
      message: `🛡️ Show ${creatureTitle}'s weaknesses`,
      suggestedPrompt: `What are the known weaknesses and counters for ${creatureTitle}?`,
    },
    {
      conversationId: CONVERSATION_ID,
      signal: "signal.dwell",
      count: 3,
      message: `📜 Summon the lore of ${creatureTitle}`,
      suggestedPrompt: `Tell me the origin myths and legendary tales of ${creatureTitle}.`,
    },
  ];
}

const resolvedCreatures = resolveDocsMock(creatureDocuments);

interface ProactiveDemoProps {
  /** Milliseconds of "dwell" before the copilot surfaces its first offer. */
  dwellWindowMs: number;
  /** Render the dark, on-page-overlay variant. */
  dark: boolean;
}

function ProactiveCopilotDemo({ dwellWindowMs, dark }: ProactiveDemoProps) {
  const [activeIdx, setActiveIdx] = useState(0);
  const [dwellMs, setDwellMs] = useState(0);
  const [suggestion, setSuggestion] = useState<TuringProactiveSuggestion | null>(null);
  const [offerCursor, setOfferCursor] = useState(0);
  const [accepted, setAccepted] = useState<string | null>(null);
  const tickRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const active = resolvedCreatures[activeIdx];
  const raw = active.raw as TurDocument;
  const offers = buildSuggestions(active.title);

  // Ambient loop: as the user "dwells" on a creature, tick the signal counter;
  // once we cross the dwell window, surface the next throttled offer.
  useEffect(() => {
    // Switching context resets the ambient state — the copilot recomputes.
    setDwellMs(0);
    setSuggestion(null);
    setOfferCursor(0);
    setAccepted(null);

    tickRef.current = setInterval(() => {
      setDwellMs((ms) => ms + 200);
    }, 200);
    return () => {
      if (tickRef.current) clearInterval(tickRef.current);
    };
  }, [activeIdx]);

  // Edge-detect: every full dwell window, emit the next offer (server throttles
  // to one per window). Holds at most one pending suggestion at a time.
  useEffect(() => {
    if (suggestion) return; // one pending offer at a time
    if (dwellMs > 0 && dwellMs % dwellWindowMs === 0 && offerCursor < offers.length) {
      setSuggestion(offers[offerCursor]);
    }
  }, [dwellMs, dwellWindowMs, offerCursor, offers, suggestion]);

  function dismiss() {
    setSuggestion(null);
    setOfferCursor((c) => c + 1);
  }

  function accept() {
    if (!suggestion) return;
    // A real app pipes this into the chat composer: chat.send(suggestedPrompt)
    setAccepted(suggestion.suggestedPrompt);
    dismiss();
  }

  const progressPct = Math.min(100, ((dwellMs % dwellWindowMs) / dwellWindowMs) * 100);
  const allOffered = offerCursor >= offers.length && !suggestion;

  const panelBg = dark ? "#0a0a0f" : "#ffffff";
  const panelBorder = dark ? "#1e1e2e" : "#e2e8f0";
  const cardBg = dark ? "#16161f" : "#f8fafc";
  const textColor = dark ? "#e2e8f0" : "#0f172a";
  const mutedColor = dark ? "#94a3b8" : "#64748b";

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        width: "620px",
        background: panelBg,
        border: `1px solid ${panelBorder}`,
        borderRadius: "16px",
        overflow: "hidden",
        color: textColor,
      }}
    >
      {/* Header */}
      <div style={{ padding: "14px 18px", background: GRADIENT, color: "white" }}>
        <div style={{ fontSize: "13px", fontWeight: 700, letterSpacing: "0.02em" }}>
          ✨ Proactive Copilot
        </div>
        <div style={{ fontSize: "11px", opacity: 0.85, marginTop: "2px" }}>
          Anticipatory assistance · watching ambient signals · convId {CONVERSATION_ID}
        </div>
      </div>

      <div style={{ display: "flex" }}>
        {/* Context column — the "page" the user is viewing */}
        <div
          style={{
            width: "200px",
            borderRight: `1px solid ${panelBorder}`,
            padding: "12px",
            display: "flex",
            flexDirection: "column",
            gap: "6px",
          }}
        >
          <div
            style={{
              fontSize: "10px",
              fontWeight: 700,
              textTransform: "uppercase",
              letterSpacing: "0.06em",
              color: mutedColor,
              marginBottom: "2px",
            }}
          >
            Bestiary — click to view
          </div>
          {resolvedCreatures.map((c, i) => {
            const isActive = i === activeIdx;
            return (
              <button
                key={c.url}
                type="button"
                onClick={() => setActiveIdx(i)}
                style={{
                  textAlign: "left",
                  padding: "8px 10px",
                  borderRadius: "8px",
                  border: isActive ? "none" : `1px solid ${panelBorder}`,
                  background: isActive ? GRADIENT : "transparent",
                  color: isActive ? "white" : textColor,
                  fontSize: "12px",
                  fontWeight: isActive ? 700 : 500,
                  cursor: "pointer",
                }}
              >
                {c.title}
              </button>
            );
          })}
        </div>

        {/* Main column — the ambient state + the proactive offer */}
        <div style={{ flex: 1, padding: "16px", minHeight: "280px" }}>
          {/* Currently viewing */}
          <div
            style={{
              background: cardBg,
              border: `1px solid ${panelBorder}`,
              borderRadius: "12px",
              padding: "12px 14px",
              marginBottom: "14px",
            }}
          >
            <div style={{ fontSize: "11px", color: mutedColor, marginBottom: "4px" }}>
              You are viewing
            </div>
            <div style={{ fontSize: "16px", fontWeight: 700 }}>{active.title}</div>
            <div style={{ fontSize: "12px", color: mutedColor, marginTop: "4px", lineHeight: 1.4 }}>
              {active.description}
            </div>
            <div style={{ fontSize: "11px", color: mutedColor, marginTop: "8px" }}>
              <code>danger_level: {String(raw.fields.danger_level)}</code>{" · "}
              <code>origin: {String(raw.fields.origin)}</code>
            </div>
          </div>

          {/* Dwell / signal meter */}
          <div style={{ marginBottom: "14px" }}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                fontSize: "11px",
                color: mutedColor,
                marginBottom: "4px",
              }}
            >
              <span>
                ambient signal · <code>signal.dwell × {offerCursor + (suggestion ? 1 : 0)}</code>
              </span>
              <span>{allOffered ? "all offers made" : "watching…"}</span>
            </div>
            <div
              style={{
                height: "6px",
                borderRadius: "999px",
                background: dark ? "#1e1e2e" : "#e2e8f0",
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  height: "100%",
                  width: `${allOffered ? 100 : progressPct}%`,
                  background: GRADIENT,
                  transition: "width 0.2s linear",
                }}
              />
            </div>
          </div>

          {/* The proactive suggestion — fades in when surfaced */}
          {suggestion ? (
            <div
              key={`${activeIdx}-${suggestion.signal}-${suggestion.count}`}
              style={{
                border: `1px solid ${dark ? "#312e81" : "#c7d2fe"}`,
                borderRadius: "12px",
                padding: "14px",
                background: dark ? "#11112a" : "#eef2ff",
                animation: "proactiveIn 0.45s cubic-bezier(0.16, 1, 0.3, 1)",
              }}
            >
              <div style={{ fontSize: "10px", color: "#6366f1", fontWeight: 700, marginBottom: "6px" }}>
                COPILOT SUGGESTS — triggered by <code>{suggestion.signal}</code> (count {suggestion.count})
              </div>
              <div style={{ fontSize: "15px", fontWeight: 700, marginBottom: "4px" }}>
                {suggestion.message}
              </div>
              <div style={{ fontSize: "12px", color: mutedColor, marginBottom: "12px" }}>
                Would send: <code>{suggestion.suggestedPrompt}</code>
              </div>
              <div style={{ display: "flex", gap: "8px" }}>
                <button
                  type="button"
                  onClick={accept}
                  style={{
                    padding: "8px 16px",
                    borderRadius: "999px",
                    border: "none",
                    background: GRADIENT,
                    color: "white",
                    fontSize: "12px",
                    fontWeight: 700,
                    cursor: "pointer",
                  }}
                >
                  Accept & send
                </button>
                <button
                  type="button"
                  onClick={dismiss}
                  style={{
                    padding: "8px 16px",
                    borderRadius: "999px",
                    border: `1px solid ${panelBorder}`,
                    background: "transparent",
                    color: mutedColor,
                    fontSize: "12px",
                    fontWeight: 600,
                    cursor: "pointer",
                  }}
                >
                  Dismiss
                </button>
              </div>
            </div>
          ) : (
            <div
              style={{
                border: `1px dashed ${panelBorder}`,
                borderRadius: "12px",
                padding: "18px 14px",
                textAlign: "center",
                fontSize: "12px",
                color: mutedColor,
              }}
            >
              {allOffered
                ? "No more proactive offers for this page — switch creatures to reset."
                : "Linger here… the copilot will offer something shortly. 🐉"}
            </div>
          )}

          {/* Accept feedback — what a real chat hook would receive */}
          {accepted && (
            <div style={{ marginTop: "12px", fontSize: "11px", color: mutedColor }}>
              ✓ <strong>chat.send()</strong> would receive: <code>{accepted}</code>
            </div>
          )}
        </div>
      </div>

      <style>{`
        @keyframes proactiveIn {
          from { opacity: 0; transform: translateY(8px) scale(0.98); }
          to   { opacity: 1; transform: translateY(0) scale(1); }
        }
      `}</style>
    </div>
  );
}

const meta: Meta<typeof ProactiveCopilotDemo> = {
  title: "AI & Chat/useTuringProactiveCopilot",
  component: ProactiveCopilotDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Ambient / proactive copilot that surfaces unsolicited suggestion chips based on what the user is browsing — anticipatory assistance, not reactive chat. This story is fully self-contained (no real hook, Provider, or network): pick a creature to set the 'page you're viewing', dwell to advance the ambient signal counter, and watch the throttled `TuringProactiveSuggestion` fade in. Switch creatures to see the copilot recompute its offers.",
      },
    },
  },
  argTypes: {
    dwellWindowMs: {
      description:
        "Simulated dwell window (ms) before the copilot surfaces its next throttled offer. The real server throttles to one suggestion per window.",
      control: { type: "select" },
      options: [1000, 2000, 3000],
    },
    dark: {
      description: "Render the dark on-page-overlay variant (Viglet dark palette).",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof ProactiveCopilotDemo>;

export const Bestiary: Story = {
  name: "🐉 Bestiary copilot (anticipatory offers)",
  args: { dwellWindowMs: 2000, dark: false },
};

export const QuickWindow: Story = {
  name: "✨ Snappy dwell window (1s throttle)",
  args: { dwellWindowMs: 1000, dark: false },
};

export const DarkOverlay: Story = {
  name: "🌙 Dark on-page overlay",
  args: { dwellWindowMs: 2000, dark: true },
};
