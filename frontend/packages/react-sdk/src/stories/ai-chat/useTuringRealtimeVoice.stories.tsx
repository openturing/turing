import type { Meta, StoryObj } from "@storybook/react-vite";
import { useEffect, useRef, useState } from "react";

/**
 * # useTuringRealtimeVoice
 *
 * Full-duplex, real-time voice for an AI agent (OpenAI Realtime API). Unlike
 * its simpler sibling `useTuringVoice` — a browser Web Speech wrapper that does
 * **separate** STT then TTS, one utterance at a time — this hook holds a **live
 * bidirectional audio session**: the visitor speaks, the model listens with
 * server-side VAD, and the agent's synthesized speech streams back
 * token-by-token, barge-in and all.
 *
 * Transport: the hook calls Turing's `POST …/voice/session` to mint a
 * short-lived **ephemeral** token (the account API key never reaches the
 * browser), then opens a WebSocket straight to the vendor's realtime endpoint.
 * Mic audio is captured, downsampled to 24 kHz PCM16 and appended to the input
 * buffer; the model's audio deltas are scheduled back through Web Audio. When
 * the model wants to act, it emits a **realtime tool call** the caller resolves.
 *
 * Feature-detected: when the browser lacks `getUserMedia`, `AudioContext` or
 * `WebSocket`, `supported` is false and `start()` is a no-op, so the caller can
 * hide the voice button.
 *
 * ## Key Features
 * - **Single live session, both directions** — no separate listen/speak phases;
 *   the visitor can interrupt (barge-in) mid-reply
 * - **Status lifecycle** — `idle → connecting → connected → closed` (or `error`),
 *   surfaced as `isConnecting` / `isConnected` / `isListening` /
 *   `isAssistantSpeaking`
 * - **Realtime tool calls** — `onToolCall({ callId, name, argumentsJson })`
 *   fires when the model asks to run a function; you execute and echo the result
 * - **Streaming transcripts** — `userTranscript` / `assistantTranscript` plus
 *   completed-utterance callbacks (`onUserUtterance` / `onAssistantUtterance`)
 * - **Ephemeral-token transport** — account key stays server-side
 * - `mute()` / `unmute()` — stop sending mic audio without closing the session
 *
 * ## Usage
 * ```tsx
 * const voice = useTuringRealtimeVoice({
 *   agentId: "mission-control",
 *   voice: "verse",
 *   locale: "en-US",
 *   onToolCall: (call) => {
 *     // The model asked to run a function — resolve it and echo the result back.
 *     if (call.name === "set_thrust") {
 *       const args = JSON.parse(call.argumentsJson);
 *       applyThrust(args.percent);
 *     }
 *   },
 *   onAssistantUtterance: (text) => logToTimeline("agent", text),
 * });
 *
 * return (
 *   <button onClick={voice.isConnected ? voice.stop : voice.start}>
 *     {voice.isConnecting ? "Connecting…"
 *       : voice.isConnected ? "End session"
 *       : "Start voice"}
 *   </button>
 * );
 * ```
 *
 * ## When to use
 *
 * - **`useTuringRealtimeVoice`** (this hook): low-latency, conversational,
 *   interruptible voice agent — the visitor and agent talk over a single open
 *   socket, and the model can call tools mid-conversation. Needs an agent +
 *   realtime-capable LLM instance and a server `voice/session` endpoint.
 * - **`useTuringVoice`** (the sibling): zero-backend, browser-only Web Speech
 *   STT/TTS — perfect for pumping dictation into a chat composer and reading a
 *   finished reply aloud, but it's turn-based (listen, *then* speak) with no
 *   server session, no VAD, and no tool calls.
 *
 * ## About this story
 *
 * Fully self-contained — **no real hook, Provider, mic or network**. It
 * simulates the status state machine with local state + `setInterval`, animates
 * a fake mic-level meter / waveform, and streams in scripted realtime tool-call
 * events so you can watch the connection lifecycle and tool stream without any
 * audio permission.
 */

type SimStatus = "idle" | "connecting" | "connected" | "closed" | "error";

interface SimToolCall {
  readonly callId: string;
  readonly name: string;
  readonly argumentsJson: string;
  readonly at: number;
}

interface ScriptStep {
  /** Whether the agent or the visitor is "speaking" during this beat. */
  readonly speaker: "agent" | "visitor";
  /** Caption shown in the transcript strip. */
  readonly text: string;
  /** Optional tool call the model emits at the end of this beat. */
  readonly toolCall?: { name: string; argumentsJson: string };
}

const MISSION_SCRIPT: ReadonlyArray<ScriptStep> = [
  { speaker: "agent", text: "Mission Control online. Go for voice ops." },
  { speaker: "visitor", text: "Status on the orbital insertion burn?" },
  {
    speaker: "agent",
    text: "Pulling telemetry now.",
    toolCall: {
      name: "get_telemetry",
      argumentsJson: '{"vehicle":"Artemis-VII","metric":"delta_v"}',
    },
  },
  { speaker: "agent", text: "Delta-v margin is nominal at 142 m/s." },
  { speaker: "visitor", text: "Trim main thruster to eighty-five percent." },
  {
    speaker: "agent",
    text: "Setting thrust.",
    toolCall: { name: "set_thrust", argumentsJson: '{"engine":"main","percent":85}' },
  },
  { speaker: "agent", text: "Thrust locked at 85%. Standing by, Commander." },
];

interface DemoArgs {
  voice: string;
  locale: string;
  dark: boolean;
  forceUnsupported: boolean;
}

const GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

function statusColor(status: SimStatus): string {
  switch (status) {
    case "connected":
      return "#22c55e";
    case "connecting":
      return "#f59e0b";
    case "error":
      return "#ef4444";
    default:
      return "#94a3b8";
  }
}

function RealtimeVoiceDemo({ voice, locale, dark, forceUnsupported }: DemoArgs) {
  const supported = !forceUnsupported;

  const [status, setStatus] = useState<SimStatus>("idle");
  const [isMuted, setIsMuted] = useState(false);
  const [stepIdx, setStepIdx] = useState(-1);
  const [caption, setCaption] = useState("");
  const [toolCalls, setToolCalls] = useState<SimToolCall[]>([]);
  const [levels, setLevels] = useState<number[]>(() => new Array(28).fill(0.04));

  const stepTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const meterTimer = useRef<ReturnType<typeof setInterval> | null>(null);
  const stepRef = useRef(0);

  const speaker: "agent" | "visitor" | null =
    status === "connected" && stepIdx >= 0 ? MISSION_SCRIPT[stepIdx]?.speaker ?? null : null;
  const isAssistantSpeaking = speaker === "agent";
  const isListening = status === "connected" && !isMuted && speaker === "visitor";

  function clearTimers() {
    if (stepTimer.current) clearTimeout(stepTimer.current);
    if (meterTimer.current) clearInterval(meterTimer.current);
    stepTimer.current = null;
    meterTimer.current = null;
  }

  function advance() {
    const idx = stepRef.current;
    if (idx >= MISSION_SCRIPT.length) {
      // Conversation complete — close the session like a real teardown.
      setStatus("closed");
      setCaption("");
      return;
    }
    const step = MISSION_SCRIPT[idx];
    setStepIdx(idx);
    setCaption(step.text);
    if (step.toolCall) {
      setToolCalls((prev) => [
        ...prev,
        {
          callId: `call_${Math.random().toString(36).slice(2, 8)}`,
          name: step.toolCall!.name,
          argumentsJson: step.toolCall!.argumentsJson,
          at: Date.now(),
        },
      ]);
    }
    stepRef.current = idx + 1;
    // Beat length scales loosely with caption length, like real speech.
    const beat = 1100 + step.text.length * 28;
    stepTimer.current = setTimeout(advance, beat);
  }

  function start() {
    if (!supported || status === "connecting" || status === "connected") return;
    clearTimers();
    setToolCalls([]);
    setStepIdx(-1);
    setCaption("");
    setIsMuted(false);
    setStatus("connecting");
    stepRef.current = 0;
    // Simulate ephemeral-token mint + WebSocket open latency, then go live.
    stepTimer.current = setTimeout(() => {
      setStatus("connected");
      advance();
    }, 1400);
  }

  function stop() {
    clearTimers();
    setStatus("idle");
    setCaption("");
    setStepIdx(-1);
    setLevels(new Array(28).fill(0.04));
  }

  // Animated fake audio-level meter. Tall, lively bars when someone is
  // "speaking"; a flat noise floor otherwise (or when muted).
  useEffect(() => {
    if (meterTimer.current) clearInterval(meterTimer.current);
    const active = status === "connected" && speaker !== null && !(speaker === "visitor" && isMuted);
    if (!active) {
      setLevels(new Array(28).fill(0.04));
      return;
    }
    meterTimer.current = setInterval(() => {
      setLevels((prev) =>
        prev.map((_, i) => {
          const wobble = Math.sin(Date.now() / 90 + i) * 0.5 + 0.5;
          return 0.12 + wobble * Math.random() * 0.85;
        }),
      );
    }, 70);
    return () => {
      if (meterTimer.current) clearInterval(meterTimer.current);
    };
  }, [status, speaker, isMuted]);

  // Cleanup on unmount.
  useEffect(() => clearTimers, []);

  const c = dark
    ? {
        bg: "#0a0a0f",
        card: "#16161f",
        border: "#1e1e2e",
        text: "#e2e8f0",
        subtle: "#94a3b8",
        chip: "#1e1e2e",
        meterBg: "#0f0f17",
      }
    : {
        bg: "#ffffff",
        card: "#ffffff",
        border: "#e2e8f0",
        text: "#0f172a",
        subtle: "#64748b",
        chip: "#f1f5f9",
        meterBg: "#f8fafc",
      };

  const lifecyclePhases: SimStatus[] = ["idle", "connecting", "connected", "closed"];

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        width: "580px",
        background: c.bg,
        color: c.text,
        padding: "20px",
        borderRadius: "16px",
        border: `1px solid ${c.border}`,
      }}
    >
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "16px" }}>
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: "12px",
            background: GRADIENT,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: "22px",
          }}
        >
          🚀
        </div>
        <div>
          <div style={{ fontWeight: 700, fontSize: "16px" }}>Mission Control · Voice Ops</div>
          <div style={{ fontSize: "12px", color: c.subtle }}>
            agent <code>mission-control</code> · voice <code>{voice}</code> · <code>{locale}</code>
          </div>
        </div>
        <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: "8px" }}>
          <span
            style={{
              width: 10,
              height: 10,
              borderRadius: "50%",
              background: statusColor(status),
              boxShadow: status === "connected" ? `0 0 8px ${statusColor(status)}` : "none",
              animation: status === "connecting" ? "rtv-pulse 1s ease-in-out infinite" : "none",
            }}
          />
          <code style={{ fontSize: "12px", fontWeight: 600 }}>{status}</code>
        </div>
      </div>

      {/* Lifecycle breadcrumb */}
      <div style={{ display: "flex", gap: "6px", marginBottom: "16px", flexWrap: "wrap" }}>
        {lifecyclePhases.map((phase) => {
          const isCurrent = phase === status || (phase === "connected" && status === "connected");
          return (
            <span
              key={phase}
              style={{
                fontSize: "11px",
                padding: "3px 10px",
                borderRadius: "999px",
                fontWeight: 600,
                background: phase === status ? GRADIENT : c.chip,
                color: phase === status ? "white" : c.subtle,
                opacity: isCurrent ? 1 : 0.75,
              }}
            >
              {phase}
            </span>
          );
        })}
        {status === "error" && (
          <span
            style={{
              fontSize: "11px",
              padding: "3px 10px",
              borderRadius: "999px",
              fontWeight: 600,
              background: "#ef4444",
              color: "white",
            }}
          >
            error
          </span>
        )}
      </div>

      {/* Waveform / level meter */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          gap: "3px",
          height: "76px",
          padding: "0 14px",
          marginBottom: "12px",
          background: c.meterBg,
          borderRadius: "12px",
          border: `1px solid ${c.border}`,
        }}
      >
        {levels.map((lvl, i) => (
          <div
            key={i}
            style={{
              width: "6px",
              height: `${Math.max(4, lvl * 64)}px`,
              borderRadius: "3px",
              background: isAssistantSpeaking ? GRADIENT : isListening ? "#22c55e" : c.border,
              transition: "height 70ms linear",
            }}
          />
        ))}
      </div>

      {/* Live caption + speaker badge */}
      <div
        style={{
          minHeight: "52px",
          padding: "12px 14px",
          marginBottom: "14px",
          background: c.card,
          border: `1px solid ${c.border}`,
          borderRadius: "12px",
          fontSize: "14px",
        }}
      >
        {speaker ? (
          <>
            <span
              style={{
                fontSize: "10px",
                fontWeight: 700,
                textTransform: "uppercase",
                letterSpacing: "0.06em",
                color: speaker === "agent" ? "#4f46e5" : "#16a34a",
                marginRight: "8px",
              }}
            >
              {speaker === "agent" ? "🛰 Agent" : "🎙 Visitor"}
            </span>
            <span>{caption}</span>
          </>
        ) : (
          <span style={{ color: c.subtle }}>
            {status === "connecting"
              ? "Minting ephemeral token, opening socket…"
              : status === "closed"
                ? "Session closed. Press Start to reconnect."
                : status === "error"
                  ? "Transport error — see error state."
                  : "Idle — press Start to open a live voice session."}
          </span>
        )}
      </div>

      {/* Controls */}
      <div style={{ display: "flex", gap: "10px", marginBottom: "16px" }}>
        <button
          type="button"
          onClick={status === "connected" || status === "connecting" ? stop : start}
          disabled={!supported}
          style={{
            padding: "10px 20px",
            borderRadius: "999px",
            border: "none",
            background:
              !supported
                ? "#cbd5e1"
                : status === "connected" || status === "connecting"
                  ? "#dc2626"
                  : GRADIENT,
            color: "white",
            fontSize: "13px",
            fontWeight: 600,
            cursor: supported ? "pointer" : "not-allowed",
          }}
        >
          {status === "connecting"
            ? "Connecting…"
            : status === "connected"
              ? "■ End session"
              : "● Start voice"}
        </button>
        <button
          type="button"
          onClick={() => setIsMuted((m) => !m)}
          disabled={status !== "connected"}
          style={{
            padding: "10px 18px",
            borderRadius: "999px",
            border: `1px solid ${c.border}`,
            background: isMuted ? "#f59e0b" : c.chip,
            color: isMuted ? "white" : c.text,
            fontSize: "13px",
            fontWeight: 600,
            cursor: status === "connected" ? "pointer" : "not-allowed",
            opacity: status === "connected" ? 1 : 0.5,
          }}
        >
          {isMuted ? "🔇 Unmute mic" : "🎙 Mute mic"}
        </button>
        <div style={{ marginLeft: "auto", alignSelf: "center", fontSize: "11px", color: c.subtle }}>
          <code>isListening:{String(isListening)}</code>{" · "}
          <code>isAssistantSpeaking:{String(isAssistantSpeaking)}</code>
        </div>
      </div>

      {/* Realtime tool-call stream */}
      <div
        style={{
          border: `1px solid ${c.border}`,
          borderRadius: "12px",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            padding: "8px 14px",
            background: c.chip,
            fontSize: "11px",
            fontWeight: 700,
            textTransform: "uppercase",
            letterSpacing: "0.05em",
            color: c.subtle,
          }}
        >
          🛠 Realtime tool calls (onToolCall)
        </div>
        <div style={{ padding: "10px 14px", minHeight: "60px" }}>
          {toolCalls.length === 0 ? (
            <div style={{ fontSize: "12px", color: c.subtle }}>
              No tool calls yet — the model emits these mid-conversation.
            </div>
          ) : (
            toolCalls.map((tc) => (
              <div
                key={tc.callId}
                style={{
                  display: "flex",
                  alignItems: "baseline",
                  gap: "8px",
                  fontSize: "12px",
                  padding: "4px 0",
                  borderBottom: `1px dashed ${c.border}`,
                }}
              >
                <code style={{ color: "#4f46e5", fontWeight: 700 }}>{tc.name}</code>
                <code style={{ color: c.subtle, flex: 1, overflow: "hidden", textOverflow: "ellipsis" }}>
                  {tc.argumentsJson}
                </code>
                <span style={{ fontSize: "10px", color: c.subtle }}>{tc.callId}</span>
              </div>
            ))
          )}
        </div>
      </div>

      {!supported && (
        <div style={{ marginTop: "12px", fontSize: "12px", color: "#ef4444" }}>
          <strong>supported: false</strong> — browser lacks getUserMedia / AudioContext / WebSocket.
          The voice button is disabled and <code>start()</code> is a no-op.
        </div>
      )}

      <style>{`@keyframes rtv-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }`}</style>
    </div>
  );
}

const meta: Meta<typeof RealtimeVoiceDemo> = {
  title: "AI & Chat/useTuringRealtimeVoice",
  component: RealtimeVoiceDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Full-duplex realtime voice agent (OpenAI Realtime API) — a single live bidirectional audio session with server-side VAD, barge-in, and realtime tool calls. Distinct from the turn-based, browser-only `useTuringVoice` STT/TTS wrapper. This story is a fully self-contained simulation: no real hook, Provider, mic or network — it drives the `idle → connecting → connected → closed` lifecycle with local state, animates a fake mic-level meter, and streams in scripted tool-call events. Press **Start voice** to play the Mission Control script.",
      },
    },
  },
  argTypes: {
    voice: {
      description: "Synthesized voice name override (server/capability default otherwise).",
      control: { type: "select" },
      options: ["verse", "alloy", "shimmer", "sage"],
    },
    locale: {
      description: "BCP-47 locale hint for transcription.",
      control: { type: "select" },
      options: ["en-US", "pt-BR", "es-ES"],
    },
    dark: {
      description: "Render the dark 'Mission Control' variant.",
      control: { type: "boolean" },
    },
    forceUnsupported: {
      description: "Override feature-detect to preview the unsupported-browser (supported:false) state.",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof RealtimeVoiceDemo>;

export const MissionControl: Story = {
  name: "🚀 Mission Control (light) — press Start",
  args: { voice: "verse", locale: "en-US", dark: false, forceUnsupported: false },
};

export const MissionControlDark: Story = {
  name: "🛰 Mission Control (dark cockpit)",
  args: { voice: "verse", locale: "en-US", dark: true, forceUnsupported: false },
};

export const PortugueseOps: Story = {
  name: "🎙 Voice ops in pt-BR",
  args: { voice: "sage", locale: "pt-BR", dark: true, forceUnsupported: false },
};

export const Unsupported: Story = {
  name: "Browser without realtime audio (supported:false)",
  args: { voice: "verse", locale: "en-US", dark: false, forceUnsupported: true },
};
