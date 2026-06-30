import type { Meta, StoryObj } from "@storybook/react-vite";
import { useEffect, useRef, useState } from "react";

/**
 * # useTuringSlots
 *
 * Reads the **live, aggregated slot state** of a guided chat conversation —
 * every slot the assistant has captured so far, merged into one flat
 * `Record<string, string>`. Where `useTuringSlot` selects *one* slot and
 * `useTuringSlotWriter` *writes* a slot, this hook is the read-all surface:
 * the whole slot board for the current `TUR_SESSION`, refreshed as the
 * conversation advances (polling timer or live SSE push).
 *
 * ## Key Features
 * - `slots`: frozen `Record<string, string>` — every captured slot, merged
 *   across all flows in the conversation
 * - `status`: `"idle" | "loading" | "success" | "error"`
 * - `conversationId`: the conversation the slots were read for (from the
 *   `TUR_SESSION` cookie, or an explicit `conversationId` option)
 * - `error`: human-readable message from the last failed fetch
 * - `refresh()`: force a refetch right now
 * - Two transports: `"polling"` (timer, works everywhere) or `"sse"`
 *   (single shared `EventSource`, &lt;100 ms latency, opt-in)
 *
 * ## Usage
 * ```tsx
 * // Live order board — repaint as each slot fills in.
 * const { slots, status, conversationId } = useTuringSlots({
 *   transport: "sse",      // live push; falls back to polling on failure
 *   pollInterval: 3000,    // also used by the SSE branch to catch the
 *                          // freshly-minted TUR_SESSION cookie
 * });
 *
 * return (
 *   <OrderSummary
 *     genre={slots.genre}
 *     artist={slots.artist}
 *     format={slots.format}
 *     budget={slots.budget}
 *     shipping={slots.shipping}
 *     loading={status === "loading"}
 *   />
 * );
 * ```
 *
 * ## When to use
 * Reach for `useTuringSlots` (plural) — not the single-slot hooks — when a
 * panel mirrors the **whole** captured state rather than one field:
 *
 * - **`useTuringSlots`** (this hook): the entire board, one fetch/stream.
 *   Ideal for an order summary, a progress bar, or a sign-up form pre-fill
 *   that watches several slots at once.
 * - **`useTuringSlot("budget")`**: one strongly-typed slot with parse modes
 *   — when a component only cares about a single value.
 * - **`useTuringSlotWriter()`**: the inverse — *write* a slot from a button
 *   so the flow skips the matching question on the next turn.
 *
 * Subscribing once and reading `slots.x` is cheaper than mounting one
 * `useTuringSlot` per field: with `transport: "sse"` every instance on the
 * page shares a single `EventSource`, and you get all five fields from one
 * merged map.
 *
 * ## About this story
 * Fully self-contained — no real hook, Provider, network, or SSE. A faux
 * record-store assistant ("Vinyl Records") walks the order flow on a timer:
 * each transcript turn captures one slot (genre → artist → format → budget
 * → shipping), and the live board flips that row from *pending* to *filled*
 * while the progress bar advances. Mirrors the real return shape (`slots`,
 * `status`, `conversationId`) so the contract is visible without a backend.
 */

// ─── Slot board definition (the Vinyl Records order flow) ──────────────

interface SlotSpec {
  readonly name: string;
  readonly label: string;
  readonly icon: string;
  /** What the assistant says when capturing this slot. */
  readonly prompt: string;
  /** What the shopper replies — drives the captured value. */
  readonly reply: string;
  /** The value written into the slots map once captured. */
  readonly value: string;
}

const ORDER_FLOW: ReadonlyArray<SlotSpec> = [
  {
    name: "genre",
    label: "Genre",
    icon: "🎸",
    prompt: "Hey! What are you in the mood for today?",
    reply: "Something jazzy, maybe some hard bop.",
    value: "Jazz · Hard Bop",
  },
  {
    name: "artist",
    label: "Artist",
    icon: "🎤",
    prompt: "Nice. Any artist in particular?",
    reply: "Got any Art Blakey?",
    value: "Art Blakey",
  },
  {
    name: "format",
    label: "Format",
    icon: "💿",
    prompt: "We do. 180g vinyl or standard pressing?",
    reply: "180-gram, please.",
    value: "180g Vinyl",
  },
  {
    name: "budget",
    label: "Budget",
    icon: "💵",
    prompt: "What's your budget per record?",
    reply: "Up to forty dollars.",
    value: "$40.00,USD",
  },
  {
    name: "shipping",
    label: "Shipping",
    icon: "📦",
    prompt: "And how should we ship it?",
    reply: "Express, I can't wait!",
    value: "Express (2-day)",
  },
];

type Status = "idle" | "loading" | "success" | "error";

interface TranscriptLine {
  readonly role: "assistant" | "shopper";
  readonly text: string;
  readonly capturedSlot?: string;
}

// ─── Demo component ────────────────────────────────────────────────────

function SlotsBoardDemo({
  stepMs,
  transport,
  dark,
}: {
  stepMs: number;
  transport: "polling" | "sse";
  dark: boolean;
}) {
  const [slots, setSlots] = useState<Readonly<Record<string, string>>>({});
  const [status, setStatus] = useState<Status>("idle");
  const [transcript, setTranscript] = useState<TranscriptLine[]>([]);
  const [step, setStep] = useState(0);
  const [runId, setRunId] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const conversationId = `tur-vinyl-${String(runId).padStart(3, "0")}`;
  const filled = Object.keys(slots).length;
  const total = ORDER_FLOW.length;
  const progress = Math.round((filled / total) * 100);

  // Drive the faux conversation forward on a timer. Each tick advances one
  // turn: the assistant asks, the shopper replies, and one slot is merged
  // into the map — exactly what a live useTuringSlots stream would surface.
  useEffect(() => {
    setSlots({});
    setTranscript([]);
    setStep(0);
    setStatus("loading");

    let i = 0;
    timerRef.current = setInterval(() => {
      const spec = ORDER_FLOW[i];
      if (!spec) {
        if (timerRef.current) clearInterval(timerRef.current);
        timerRef.current = null;
        return;
      }
      setTranscript((prev) => [
        ...prev,
        { role: "assistant", text: spec.prompt },
        { role: "shopper", text: spec.reply, capturedSlot: spec.name },
      ]);
      setSlots((prev) => Object.freeze({ ...prev, [spec.name]: spec.value }));
      setStep(i + 1);
      setStatus("success");
      i += 1;
      if (i >= ORDER_FLOW.length && timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    }, stepMs);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
      timerRef.current = null;
    };
  }, [stepMs, runId]);

  const replay = () => setRunId((r) => r + 1);

  // ── Theme tokens ──
  const bg = dark ? "#0a0a0f" : "#ffffff";
  const card = dark ? "#16161f" : "#ffffff";
  const border = dark ? "#1e1e2e" : "#e2e8f0";
  const headBg = dark ? "#1e1e2e" : "#f1f5f9";
  const textMain = dark ? "#e2e8f0" : "#0f172a";
  const textSub = dark ? "#94a3b8" : "#64748b";
  const pendingBg = dark ? "#101019" : "#f8fafc";
  const VIGLET = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        maxWidth: "680px",
        background: bg,
        color: textMain,
        padding: dark ? "20px" : 0,
        borderRadius: "14px",
      }}
    >
      {/* Header */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: "12px",
          marginBottom: "14px",
        }}
      >
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: "50%",
            background: VIGLET,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: "20px",
            boxShadow: "0 2px 8px rgba(37,99,235,0.35)",
          }}
        >
          🎵
        </div>
        <div>
          <div style={{ fontWeight: 700, fontSize: "15px" }}>Vinyl Records · Order Assistant</div>
          <div style={{ fontSize: "12px", color: textSub }}>
            useTuringSlots reading the live order board
          </div>
        </div>
      </div>

      {/* Meta row — mirrors the hook return */}
      <div
        style={{
          display: "flex",
          flexWrap: "wrap",
          gap: "8px 16px",
          padding: "8px 14px",
          background: pendingBg,
          border: `1px solid ${border}`,
          borderRadius: "8px",
          fontSize: "12px",
          marginBottom: "14px",
        }}
      >
        <span>
          <strong>status:</strong>{" "}
          <code
            style={{
              padding: "1px 6px",
              borderRadius: "4px",
              background:
                status === "success" ? "#dcfce7" : status === "loading" ? "#fef3c7" : "#e2e8f0",
              color:
                status === "success" ? "#166534" : status === "loading" ? "#92400e" : "#475569",
            }}
          >
            {status}
          </code>
        </span>
        <span>
          <strong>transport:</strong> <code>{transport}</code>
        </span>
        <span>
          <strong>conversationId:</strong> <code style={{ color: textSub }}>{conversationId}</code>
        </span>
        <span>
          <strong>slots filled:</strong>{" "}
          <code>
            {filled}/{total}
          </code>
        </span>
      </div>

      {/* Progress bar */}
      <div
        style={{
          height: "8px",
          borderRadius: "999px",
          background: dark ? "#1e1e2e" : "#e2e8f0",
          overflow: "hidden",
          marginBottom: "16px",
        }}
        role="progressbar"
        aria-valuenow={progress}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div
          style={{
            height: "100%",
            width: `${progress}%`,
            background: VIGLET,
            transition: "width 0.4s ease",
          }}
        />
      </div>

      <div style={{ display: "flex", gap: "16px", flexWrap: "wrap" }}>
        {/* Live slot board */}
        <div
          style={{
            flex: "1 1 280px",
            border: `1px solid ${border}`,
            borderRadius: "12px",
            overflow: "hidden",
            background: card,
          }}
        >
          <div
            style={{
              padding: "10px 14px",
              background: headBg,
              fontSize: "12px",
              fontWeight: 600,
              color: textSub,
              textTransform: "uppercase",
              letterSpacing: "0.05em",
            }}
          >
            Slot board (slots map)
          </div>
          {ORDER_FLOW.map((spec, idx) => {
            const value = slots[spec.name];
            const isFilled = value !== undefined;
            const isNext = idx === step && !isFilled;
            return (
              <div
                key={spec.name}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "10px",
                  padding: "11px 14px",
                  borderBottom: `1px solid ${border}`,
                  background: isFilled ? (dark ? "#0e1b14" : "#f0fdf4") : pendingBg,
                  opacity: isFilled || isNext ? 1 : 0.55,
                  transition: "background 0.3s ease, opacity 0.3s ease",
                }}
              >
                <span aria-hidden="true" style={{ fontSize: "16px" }}>
                  {spec.icon}
                </span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <code style={{ fontSize: "12px", color: textSub }}>{spec.name}</code>
                  <div
                    style={{
                      fontSize: "13px",
                      fontWeight: isFilled ? 600 : 400,
                      color: isFilled ? (dark ? "#86efac" : "#166534") : textSub,
                      whiteSpace: "nowrap",
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                    }}
                  >
                    {isFilled ? value : isNext ? "capturing…" : "— pending"}
                  </div>
                </div>
                <span style={{ fontSize: "14px" }}>
                  {isFilled ? "✓" : isNext ? "…" : ""}
                </span>
              </div>
            );
          })}
        </div>

        {/* Transcript that drives the fills */}
        <div
          style={{
            flex: "1 1 280px",
            border: `1px solid ${border}`,
            borderRadius: "12px",
            overflow: "hidden",
            background: card,
            display: "flex",
            flexDirection: "column",
          }}
        >
          <div
            style={{
              padding: "10px 14px",
              background: headBg,
              fontSize: "12px",
              fontWeight: 600,
              color: textSub,
              textTransform: "uppercase",
              letterSpacing: "0.05em",
            }}
          >
            Conversation
          </div>
          <div style={{ padding: "12px", flex: 1, minHeight: "180px" }}>
            {transcript.length === 0 ? (
              <div style={{ color: textSub, fontSize: "13px", textAlign: "center", paddingTop: "60px" }}>
                Order in progress…
              </div>
            ) : (
              transcript.map((line, idx) => (
                <div
                  key={idx}
                  style={{
                    display: "flex",
                    justifyContent: line.role === "shopper" ? "flex-end" : "flex-start",
                    marginBottom: "8px",
                  }}
                >
                  <div
                    style={{
                      maxWidth: "85%",
                      padding: "8px 12px",
                      borderRadius: "12px",
                      fontSize: "13px",
                      background:
                        line.role === "shopper"
                          ? VIGLET
                          : dark
                            ? "#1e1e2e"
                            : "#f1f5f9",
                      color: line.role === "shopper" ? "#ffffff" : textMain,
                    }}
                  >
                    {line.text}
                    {line.capturedSlot && (
                      <span
                        style={{
                          display: "block",
                          marginTop: "4px",
                          fontSize: "10px",
                          opacity: 0.85,
                        }}
                      >
                        → wrote slot <code>{line.capturedSlot}</code>
                      </span>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Replay control */}
      <div style={{ marginTop: "16px", display: "flex", alignItems: "center", gap: "12px" }}>
        <button
          type="button"
          onClick={replay}
          style={{
            padding: "9px 18px",
            borderRadius: "8px",
            border: "none",
            background: VIGLET,
            color: "#ffffff",
            fontSize: "13px",
            fontWeight: 600,
            cursor: "pointer",
            boxShadow: "0 2px 6px rgba(37,99,235,0.3)",
          }}
        >
          🎰 Replay order
        </button>
        <span style={{ fontSize: "12px", color: textSub }}>
          {filled === total ? "Order complete — all slots captured." : "Watch the board fill live."}
        </span>
      </div>
    </div>
  );
}

const meta: Meta<typeof SlotsBoardDemo> = {
  title: "AI & Chat/useTuringSlots",
  component: SlotsBoardDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Reads the live, aggregated slot state of a guided chat conversation as one flat `Record<string,string>` — the whole board, not a single field. This story simulates a Vinyl Records order assistant filling five slots (genre → artist → format → budget → shipping) on a timer; the slot board flips each row from pending to filled while a transcript drives the captures. No real hook, Provider, network, or SSE.",
      },
    },
  },
  argTypes: {
    stepMs: {
      description: "Delay between conversation turns (one slot captured per turn), in milliseconds.",
      control: { type: "range", min: 400, max: 3000, step: 100 },
    },
    transport: {
      description:
        "Reflected in the meta row. `polling` refetches on a timer; `sse` opens a single shared EventSource for <100 ms live pushes (falls back to polling on failure).",
      control: { type: "inline-radio" },
      options: ["polling", "sse"],
    },
    dark: {
      description: "Render the dark Vinyl Records variant (Viglet dark palette).",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof SlotsBoardDemo>;

export const LiveBoard: Story = {
  name: "🎵 Live slot board (SSE, fills as it talks)",
  args: { stepMs: 1200, transport: "sse", dark: false },
};

export const Polling: Story = {
  name: "🎰 Polling transport (timer-refetched)",
  args: { stepMs: 1600, transport: "polling", dark: false },
};

export const FastPour: Story = {
  name: "⚡ Fast pour (snappy fills)",
  args: { stepMs: 600, transport: "sse", dark: false },
};

export const DarkStore: Story = {
  name: "🌙 Dark record store",
  args: { stepMs: 1200, transport: "sse", dark: true },
};
