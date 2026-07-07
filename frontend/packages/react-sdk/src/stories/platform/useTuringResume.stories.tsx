import type { Meta, StoryObj } from "@storybook/react-vite";
import { useEffect, useRef, useState } from "react";

/**
 * # useTuringResume
 *
 * Picks a chat conversation back up where the visitor left it. When a
 * chat-flow parks at a suspend node — a human-approval gate, a "wait for
 * payment", a long-running tool — the conversation sits dormant until
 * someone calls `resume()`. The hook reads the parked `conversationId`
 * (from the `TUR_SESSION` cookie, or an explicit one you pass), optionally
 * applies a batch of `slotUpdates`, and POSTs to `/sn/{site}/chat/resume`
 * so the flow advances past the suspend point.
 *
 * The return is a `TurChatResumeResponse`:
 *
 * - `resumed` — how many parked turns were re-activated (`0` means there
 *   was nothing parked)
 * - `wasParked` — whether the conversation was actually suspended at a
 *   resumable node
 * - `error` — a human-readable message when the resume could not proceed
 *
 * This drives the classic **"Welcome back"** experience: a returning
 * visitor sees a summary of the prior exchange and a single button to
 * rehydrate the transcript and continue.
 *
 * ## Key Features
 * - `resume(slotUpdates?, resumeReason?)`: imperatively advances a parked
 *   conversation, returning the `TurChatResumeResponse`
 * - `status`: `"idle" | "resuming" | "success" | "error"` — bind directly
 *   to a button's disabled/label state
 * - `error`: human-readable message from the last failed resume
 * - `lastResult`: the most recent successful `TurChatResumeResponse`
 * - Reads the parked `conversationId` from the `TUR_SESSION` cookie, or
 *   accepts an explicit `conversationId` / `sessionCookieName` option
 * - Surfaces a clear "No active conversation — nothing to resume." error
 *   when there is no parked session to pick back up
 *
 * ## Usage
 * ```tsx
 * const { resume, status, error, lastResult } = useTuringResume();
 *
 * <button
 *   onClick={() => resume({ approved: "true" }, "Manager approved the quote")}
 *   disabled={status === "resuming"}
 * >
 *   {status === "resuming" ? "Resuming…" : "Welcome back — continue"}
 * </button>
 *
 * {status === "success" && lastResult?.wasParked && (
 *   <p>Re-activated {lastResult.resumed} parked turn(s).</p>
 * )}
 * {error && <p role="alert">{error}</p>}
 * ```
 *
 * ## When to use
 * - **Human-in-the-loop approval**: the flow parks at a `suspend` node
 *   pending a manager's sign-off; the approver clicks "Approve & continue"
 *   and `resume({ approved: "true" })` advances it
 * - **Return visits**: a visitor closes the tab mid-conversation; on their
 *   next visit the `TUR_SESSION` cookie still points at the parked
 *   conversation, so a "Welcome back" panel can rehydrate and continue
 * - **Out-of-band events**: a payment webhook or a long batch job finishes;
 *   a server (or the page) calls `resume()` to wake the parked flow
 *
 * ## About this story
 * Fully self-contained — there is no real hook, Provider, or network here.
 * A local timer simulates the resume round-trip and, on success, reveals
 * the previously-parked transcript so you can watch the
 * `idle → resuming → success` status pill transition. Toggle
 * `nothingParked` to preview the empty-state error path, and `failResume`
 * to preview a server-side failure.
 */

type ResumeStatus = "idle" | "resuming" | "success" | "error";

interface TurChatResumeResponse {
  readonly resumed: number;
  readonly wasParked: boolean;
  readonly error?: string | null;
}

interface PriorMessage {
  readonly role: "user" | "assistant";
  readonly text: string;
}

const GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

const PRIOR_TRANSCRIPT: ReadonlyArray<PriorMessage> = [
  { role: "assistant", text: "Olá! Posso te ajudar a montar uma trilha executiva. Qual é o seu objetivo?" },
  { role: "user", text: "Sou CFO de uma fintech e quero liderar a área de M&A em 2 anos." },
  { role: "assistant", text: "Ótimo. Montei uma proposta de programa intensivo. Para liberá-la, preciso da aprovação do seu gestor." },
  { role: "user", text: "Pode enviar para aprovação." },
];

interface ResumeDemoProps {
  readonly latencyMs: number;
  readonly nothingParked: boolean;
  readonly failResume: boolean;
  readonly dark: boolean;
}

function ResumeDemo({ latencyMs, nothingParked, failResume, dark }: ResumeDemoProps) {
  const [status, setStatus] = useState<ResumeStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<TurChatResumeResponse | null>(null);
  const [revealed, setRevealed] = useState(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const revealRef = useRef<ReturnType<typeof setInterval> | null>(null);

  function clearTimers() {
    if (timerRef.current) clearTimeout(timerRef.current);
    if (revealRef.current) clearInterval(revealRef.current);
    timerRef.current = null;
    revealRef.current = null;
  }

  useEffect(() => clearTimers, []);

  function resume() {
    if (status === "resuming") return;
    clearTimers();
    setRevealed(0);

    // Mirrors the hook: no parked conversationId → immediate error, no POST.
    if (nothingParked) {
      setLastResult(null);
      setError("No active conversation — nothing to resume.");
      setStatus("error");
      return;
    }

    setError(null);
    setStatus("resuming");
    timerRef.current = setTimeout(() => {
      if (failResume) {
        const result: TurChatResumeResponse = {
          resumed: 0,
          wasParked: true,
          error: "Approval node rejected the resume (simulated 409).",
        };
        setLastResult(null);
        setError(result.error ?? "Resume failed");
        setStatus("error");
        return;
      }
      const result: TurChatResumeResponse = {
        resumed: PRIOR_TRANSCRIPT.length,
        wasParked: true,
        error: null,
      };
      setLastResult(result);
      setStatus("success");
      // Rehydrate the transcript one message at a time for effect.
      revealRef.current = setInterval(() => {
        setRevealed((n) => {
          if (n >= PRIOR_TRANSCRIPT.length) {
            if (revealRef.current) clearInterval(revealRef.current);
            revealRef.current = null;
            return n;
          }
          return n + 1;
        });
      }, 280);
    }, latencyMs);
  }

  function reset() {
    clearTimers();
    setStatus("idle");
    setError(null);
    setLastResult(null);
    setRevealed(0);
  }

  // Theme tokens
  const pageBg = dark ? "#0a0a0f" : "transparent";
  const cardBg = dark ? "#16161f" : "white";
  const cardBorder = dark ? "#1e1e2e" : "#e2e8f0";
  const textColor = dark ? "#e2e8f0" : "#0f172a";
  const mutedColor = dark ? "#94a3b8" : "#64748b";
  const bubbleAssistantBg = dark ? "#1e1e2e" : "#f1f5f9";
  const bubbleUserBorder = dark ? "#312e81" : "#c7d2fe";

  const statusStyles: Record<ResumeStatus, { bg: string; fg: string; label: string }> = {
    idle: { bg: dark ? "#1e293b" : "#e2e8f0", fg: dark ? "#cbd5e1" : "#475569", label: "idle" },
    resuming: { bg: "#fef3c7", fg: "#92400e", label: "resuming" },
    success: { bg: "#dcfce7", fg: "#166534", label: "success" },
    error: { bg: "#fee2e2", fg: "#991b1b", label: "error" },
  };
  const pill = statusStyles[status];

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        maxWidth: "560px",
        padding: dark ? "20px" : "0",
        background: pageBg,
        borderRadius: "16px",
      }}
    >
      <div
        style={{
          border: `1px solid ${cardBorder}`,
          borderRadius: "16px",
          overflow: "hidden",
          background: cardBg,
          boxShadow: dark ? "none" : "0 1px 3px rgba(15,23,42,0.08)",
        }}
      >
        {/* Gradient header */}
        <div style={{ background: GRADIENT, padding: "18px 20px", color: "white" }}>
          <div style={{ fontSize: "12px", opacity: 0.85, letterSpacing: "0.06em", textTransform: "uppercase" }}>
            ↩️ Viglet Turing ES
          </div>
          <div style={{ fontSize: "20px", fontWeight: 700, marginTop: "4px" }}>
            Welcome back 👋
          </div>
          <div style={{ fontSize: "13px", opacity: 0.9, marginTop: "2px" }}>
            You left a conversation parked for approval.
          </div>
        </div>

        <div style={{ padding: "18px 20px" }}>
          {/* Prior conversation summary */}
          <div
            style={{
              display: "flex",
              gap: "10px",
              alignItems: "flex-start",
              padding: "12px 14px",
              background: bubbleAssistantBg,
              borderRadius: "12px",
              marginBottom: "16px",
            }}
          >
            <span style={{ fontSize: "20px" }} aria-hidden="true">💬</span>
            <div style={{ fontSize: "13px", color: textColor, lineHeight: 1.5 }}>
              <strong>Last session</strong> — {PRIOR_TRANSCRIPT.length} messages about an
              executive M&A track, parked at a manager-approval gate.
            </div>
          </div>

          {/* Resume button + status pill */}
          <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "16px" }}>
            <button
              type="button"
              onClick={status === "success" || status === "error" ? reset : resume}
              disabled={status === "resuming"}
              style={{
                flex: 1,
                padding: "12px 18px",
                borderRadius: "10px",
                border: "none",
                background: status === "resuming" ? "#94a3b8" : GRADIENT,
                color: "white",
                fontSize: "14px",
                fontWeight: 600,
                cursor: status === "resuming" ? "wait" : "pointer",
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
                gap: "8px",
              }}
            >
              {status === "resuming" && (
                <span
                  style={{
                    width: "14px",
                    height: "14px",
                    borderRadius: "50%",
                    border: "2px solid rgba(255,255,255,0.5)",
                    borderTopColor: "white",
                    animation: "tur-resume-spin 0.8s linear infinite",
                  }}
                />
              )}
              {status === "idle" && "↩️ Resume conversation"}
              {status === "resuming" && "Resuming…"}
              {status === "success" && "✓ Resumed — start over"}
              {status === "error" && "Try again"}
            </button>

            <code
              style={{
                padding: "6px 12px",
                borderRadius: "999px",
                background: pill.bg,
                color: pill.fg,
                fontSize: "12px",
                fontWeight: 600,
                whiteSpace: "nowrap",
              }}
            >
              {pill.label}
            </code>
          </div>

          {/* lastResult readout */}
          {lastResult && (
            <div
              style={{
                display: "flex",
                gap: "16px",
                padding: "10px 14px",
                background: dark ? "#0f172a" : "#f8fafc",
                borderRadius: "10px",
                fontSize: "12px",
                color: mutedColor,
                marginBottom: "14px",
              }}
            >
              <span>
                <strong>resumed:</strong> <code>{lastResult.resumed}</code>
              </span>
              <span>
                <strong>wasParked:</strong> <code>{String(lastResult.wasParked)}</code>
              </span>
            </div>
          )}

          {error && (
            <div
              role="alert"
              style={{
                padding: "10px 14px",
                background: dark ? "#2a1215" : "#fef2f2",
                border: `1px solid ${dark ? "#7f1d1d" : "#fecaca"}`,
                borderRadius: "8px",
                fontSize: "13px",
                color: dark ? "#fca5a5" : "#991b1b",
                marginBottom: "14px",
              }}
            >
              ⚠ {error}
            </div>
          )}

          {/* Rehydrated transcript */}
          {status === "success" && (
            <div
              style={{
                border: `1px solid ${cardBorder}`,
                borderRadius: "12px",
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  padding: "9px 14px",
                  background: dark ? "#0f172a" : "#f8fafc",
                  borderBottom: `1px solid ${cardBorder}`,
                  fontSize: "11px",
                  fontWeight: 600,
                  color: mutedColor,
                  textTransform: "uppercase",
                  letterSpacing: "0.05em",
                }}
              >
                Restored transcript ({revealed}/{PRIOR_TRANSCRIPT.length})
              </div>
              <div style={{ padding: "12px 14px", display: "flex", flexDirection: "column", gap: "8px" }}>
                {PRIOR_TRANSCRIPT.slice(0, revealed).map((m, i) => (
                  <div
                    key={i}
                    style={{
                      alignSelf: m.role === "user" ? "flex-end" : "flex-start",
                      maxWidth: "85%",
                      padding: "8px 12px",
                      borderRadius: "12px",
                      fontSize: "13px",
                      lineHeight: 1.45,
                      background: m.role === "user" ? "transparent" : bubbleAssistantBg,
                      border: m.role === "user" ? `1px solid ${bubbleUserBorder}` : "none",
                      color: textColor,
                    }}
                  >
                    {m.text}
                  </div>
                ))}
                {revealed < PRIOR_TRANSCRIPT.length && (
                  <div style={{ fontSize: "12px", color: mutedColor }}>…rehydrating</div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      <style>{`@keyframes tur-resume-spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}

const meta: Meta<typeof ResumeDemo> = {
  title: "Platform/useTuringResume",
  component: ResumeDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Resumes a chat conversation parked at a suspend node — the engine behind a 'Welcome back' panel that rehydrates the prior transcript so a visitor can continue where they left off. This story is a fully self-contained simulation: a local timer fakes the resume round-trip and reveals a restored transcript on success, with toggles for the empty-state and failure paths.",
      },
    },
  },
  argTypes: {
    latencyMs: {
      description: "Simulated resume round-trip latency, in milliseconds.",
      control: { type: "range", min: 0, max: 2000, step: 100 },
    },
    nothingParked: {
      description:
        "No parked conversationId — mirrors the hook's immediate 'nothing to resume' error (no POST is sent).",
      control: { type: "boolean" },
    },
    failResume: {
      description: "Simulate a server-side rejection of the resume (e.g. a 409 from the approval node).",
      control: { type: "boolean" },
    },
    dark: {
      description: "Render the panel on the Viglet dark surface (#0a0a0f).",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof ResumeDemo>;

export const WelcomeBack: Story = {
  name: "↩️ Welcome back (resume & rehydrate)",
  args: { latencyMs: 700, nothingParked: false, failResume: false, dark: false },
};

export const NothingParked: Story = {
  name: "💬 Nothing parked (empty-state error)",
  args: { latencyMs: 700, nothingParked: true, failResume: false, dark: false },
};

export const ResumeRejected: Story = {
  name: "⚠️ Resume rejected (server 409)",
  args: { latencyMs: 700, nothingParked: false, failResume: true, dark: false },
};

export const DarkWelcomeBack: Story = {
  name: "🌙 Welcome back (dark)",
  args: { latencyMs: 700, nothingParked: false, failResume: false, dark: true },
};
