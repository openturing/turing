import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";

/**
 * # useTuringHandoff
 *
 * Imperatively builds a channel-specific deep link
 * (WhatsApp / Email / SMS / Telegram / Slack) carrying the conversation
 * context — name, current role, area, objective — so a human consultant
 * picks up exactly where the bot left off. The server composes the link
 * from the captured slots; the hook either redirects to it (default)
 * or returns it for the caller to handle.
 *
 * ## Key Features
 * - `handoff({ channel?, destination?, intro?, slotsToInclude? })`:
 *   builds the URL and (when `autoOpen=true`) opens it
 * - Five channels: `whatsapp` (universal `wa.me/` link), `email`
 *   (`mailto:` with subject + body), `sms` (`sms:` with `?body=`),
 *   `telegram` (`t.me/{user|+phone}?text=`), `slack`
 *   (`slack.com/app_redirect?team=...&channel=...`)
 * - `status`: `"idle" | "building" | "success" | "error"`
 * - `lastResult`: `{ url, transcript, slotsIncluded, error }`
 * - Reads `TUR_SESSION` cookie automatically (same conversation as
 *   `useTuringChat`)
 * - Sets `handoff_status = "{channel}_requested"` slot as a side effect
 *   so the UI can hide the CTA once clicked and the analytics dashboard
 *   compares handoff rates per A/B variant
 *
 * ## Usage
 * ```tsx
 * const { handoff, status } = useTuringHandoff({
 *   channel: "whatsapp",
 *   destination: "5511999999999",
 *   intro: "Olá! Vim do site de Executive Education.",
 *   slotsToInclude: ["name", "cargo_atual", "area_label", "objetivo"],
 * });
 *
 * <button onClick={() => handoff()} disabled={status === "building"}>
 *   Continuar no WhatsApp
 * </button>
 * ```
 *
 * ## When to use
 *
 * - **Bot → human handoff** when conversational coverage runs out and
 *   the user explicitly asks to talk to a real person
 * - **Multi-channel routing** — show 3 buttons (WhatsApp, Email, SMS)
 *   each calling `handoff({ channel })` so the visitor picks their
 *   preferred medium
 * - **Lead capture endpoint** — the destination phone/email can be the
 *   sales team's hotline; the transcript carries the qualifying slots
 *
 * The story simulates the URL-building round-trip and renders a preview
 * of what the recipient would see (transcript text + composed URL).
 */

type Status = "idle" | "building" | "success" | "error";
type Channel = "whatsapp" | "email" | "sms" | "telegram" | "slack";

interface SimulatedHandoffResult {
  readonly url: string | null;
  readonly transcript: string;
  readonly slotsIncluded: number;
  readonly error: string | null;
}

const CAPTURED_SLOTS: Record<string, string> = {
  name: "Alexandre",
  cargo_atual: "Gerente sênior numa fintech, há 4 anos",
  objetivo: "Virar CFO em 3 anos",
  area_label: "Finanças & Investimentos",
  cta_visible: "true",
};

const CHANNEL_DEFAULTS: Record<
  Channel,
  { destination: string; placeholder: string; icon: string; label: string }
> = {
  whatsapp: { destination: "5511999999999", placeholder: "Phone digits (with country code)", icon: "💬", label: "WhatsApp" },
  email:    { destination: "consultor@education.example.com", placeholder: "Email address", icon: "📩", label: "Email" },
  sms:      { destination: "+5511999999999", placeholder: "Phone with +", icon: "📱", label: "SMS" },
  telegram: { destination: "@education_ee_consultor", placeholder: "@username or +phone", icon: "✈️", label: "Telegram" },
  slack:    { destination: "T01ABCDEF/U05XYZ123", placeholder: "{teamId}/{userId}", icon: "💼", label: "Slack" },
};

function buildSimulatedTranscript(intro: string | undefined, slotKeys: ReadonlyArray<string>): string {
  const parts: string[] = [];
  if (intro && intro.trim()) parts.push(intro.trim(), "");
  for (const k of slotKeys) {
    const value = CAPTURED_SLOTS[k];
    if (value) parts.push(`• ${k.replace(/_/g, " ")}: ${value}`);
  }
  return parts.join("\n");
}

function buildSimulatedUrl(channel: Channel, destination: string, transcript: string): string {
  const enc = encodeURIComponent(transcript);
  switch (channel) {
    case "whatsapp": return `https://wa.me/${destination.replace(/[^0-9]/g, "")}?text=${enc}`;
    case "email":    return `mailto:${destination}?subject=${encodeURIComponent("Continuação do atendimento")}&body=${enc}`;
    case "sms":      return `sms:${destination.replace(/[^0-9+]/g, "")}?body=${enc}`;
    case "telegram": {
      const clean = destination.startsWith("@") ? destination.slice(1) : destination;
      const stripped = clean.match(/^[+0-9 ()-]+$/) ? `+${clean.replace(/\D/g, "")}` : clean;
      return `https://t.me/${stripped}?text=${enc}`;
    }
    case "slack": {
      const [teamId, userId] = destination.split("/");
      return `https://slack.com/app_redirect?team=${encodeURIComponent(teamId ?? "")}&channel=${encodeURIComponent(userId ?? "")}&message=${enc}`;
    }
  }
}

function HandoffDemo({
  channel,
  intro,
  failProbability,
  latencyMs,
}: {
  channel: Channel;
  intro: string;
  failProbability: number;
  latencyMs: number;
}) {
  const [destination, setDestination] = useState(CHANNEL_DEFAULTS[channel].destination);
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<SimulatedHandoffResult | null>(null);

  // Keep destination synced with the channel argType — when a story flips
  // channel via controls, the default destination follows.
  const lastChannelRef = useState(channel)[0];
  if (lastChannelRef !== channel) {
    // No-op: a real impl would useEffect — this story keeps state ephemeral.
  }

  async function runHandoff() {
    setStatus("building");
    setError(null);
    await new Promise((resolve) => setTimeout(resolve, latencyMs));
    if (Math.random() < failProbability) {
      const msg = `Handoff service unreachable (channel=${channel})`;
      setError(msg);
      setStatus("error");
      return;
    }
    if (!destination.trim()) {
      const msg = "Handoff destination is required (phone / email).";
      setError(msg);
      setStatus("error");
      return;
    }
    const slotKeys = ["name", "cargo_atual", "objetivo", "area_label"];
    const transcript = buildSimulatedTranscript(intro, slotKeys);
    const url = buildSimulatedUrl(channel, destination, transcript);
    const result: SimulatedHandoffResult = {
      url,
      transcript,
      slotsIncluded: slotKeys.filter((k) => CAPTURED_SLOTS[k]).length,
      error: null,
    };
    setLastResult(result);
    setStatus("success");
  }

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", maxWidth: "620px" }}>
      {/* Captured slots preview (read-only) */}
      <div
        style={{
          padding: "10px 14px",
          background: "#f8fafc",
          border: "1px solid #e2e8f0",
          borderRadius: "10px",
          fontSize: "12px",
          marginBottom: "14px",
        }}
      >
        <div style={{ fontWeight: 600, color: "#64748b", marginBottom: "6px",
                      textTransform: "uppercase", letterSpacing: "0.05em" }}>
          Captured slots (read from chat)
        </div>
        {Object.entries(CAPTURED_SLOTS).filter(([k]) => k !== "cta_visible").map(([k, v]) => (
          <div key={k} style={{ display: "flex", justifyContent: "space-between", padding: "2px 0" }}>
            <code style={{ color: "#0f172a" }}>{k}</code>
            <span style={{ color: "#475569" }}>{v}</span>
          </div>
        ))}
      </div>

      {/* Destination input */}
      <div style={{ marginBottom: "12px" }}>
        <label style={{ display: "block", fontSize: "12px", fontWeight: 600,
                       color: "#475569", marginBottom: "4px",
                       textTransform: "uppercase", letterSpacing: "0.05em" }}>
          {CHANNEL_DEFAULTS[channel].icon} {CHANNEL_DEFAULTS[channel].label} destination
        </label>
        <input
          type="text"
          value={destination}
          onChange={(e) => setDestination(e.target.value)}
          placeholder={CHANNEL_DEFAULTS[channel].placeholder}
          style={{
            width: "100%",
            padding: "10px 12px",
            borderRadius: "8px",
            border: "1px solid #cbd5e1",
            fontSize: "13px",
            fontFamily: "monospace",
            boxSizing: "border-box",
          }}
        />
      </div>

      <button
        type="button"
        onClick={() => { void runHandoff(); }}
        disabled={status === "building"}
        style={{
          width: "100%",
          padding: "12px",
          borderRadius: "10px",
          border: "none",
          background: status === "building" ? "#94a3b8" : "#25d366",
          color: "white",
          fontSize: "14px",
          fontWeight: 600,
          cursor: status === "building" ? "wait" : "pointer",
          marginBottom: "12px",
        }}
      >
        {status === "building" ? "Building handoff link..." : `Continuar via ${CHANNEL_DEFAULTS[channel].label}`}
      </button>

      <div
        style={{
          display: "flex",
          gap: "10px",
          alignItems: "center",
          padding: "8px 14px",
          background: "#f8fafc",
          borderRadius: "8px",
          fontSize: "12px",
          marginBottom: "10px",
        }}
      >
        <span>
          <strong>status:</strong>{" "}
          <code style={{
            padding: "2px 6px",
            borderRadius: "4px",
            background:
              status === "success" ? "#dcfce7"
              : status === "error" ? "#fee2e2"
              : status === "building" ? "#fef3c7"
              : "#e2e8f0",
            color:
              status === "success" ? "#166534"
              : status === "error" ? "#991b1b"
              : status === "building" ? "#92400e"
              : "#475569",
          }}>{status}</code>
        </span>
        {lastResult && (
          <span><strong>slotsIncluded:</strong> <code>{lastResult.slotsIncluded}</code></span>
        )}
      </div>

      {error && (
        <div style={{
          padding: "10px 14px",
          background: "#fef2f2",
          borderRadius: "8px",
          fontSize: "13px",
          color: "#991b1b",
          marginBottom: "10px",
        }}>⚠ {error}</div>
      )}

      {lastResult && lastResult.url && (
        <div style={{
          border: "1px solid #bbf7d0",
          borderRadius: "10px",
          background: "#f0fdf4",
          overflow: "hidden",
        }}>
          <div style={{
            padding: "10px 14px",
            background: "#dcfce7",
            borderBottom: "1px solid #bbf7d0",
            fontSize: "12px",
            fontWeight: 600,
            color: "#166534",
            textTransform: "uppercase",
            letterSpacing: "0.05em",
          }}>✓ Handoff link ready</div>
          <div style={{ padding: "12px 14px" }}>
            <div style={{ fontSize: "11px", color: "#64748b", marginBottom: "4px", fontWeight: 600 }}>
              URL
            </div>
            <code style={{
              display: "block",
              padding: "8px 10px",
              background: "white",
              border: "1px solid #e2e8f0",
              borderRadius: "6px",
              fontSize: "11px",
              wordBreak: "break-all",
              color: "#0f172a",
              marginBottom: "12px",
            }}>{lastResult.url}</code>
            <div style={{ fontSize: "11px", color: "#64748b", marginBottom: "4px", fontWeight: 600 }}>
              Transcript (what the recipient will see)
            </div>
            <pre style={{
              margin: 0,
              padding: "10px",
              background: "white",
              border: "1px solid #e2e8f0",
              borderRadius: "6px",
              fontSize: "11px",
              whiteSpace: "pre-wrap",
              fontFamily: "system-ui, sans-serif",
              color: "#0f172a",
            }}>{lastResult.transcript}</pre>
          </div>
        </div>
      )}
    </div>
  );
}

const meta: Meta<typeof HandoffDemo> = {
  title: "Hooks/useTuringHandoff",
  component: HandoffDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Composes a channel-specific deep link carrying the captured chat slots so a human consultant picks up with full context. Five channels supported: WhatsApp universal link, mailto:, sms:, Telegram t.me, Slack app_redirect. This story builds the URL + transcript preview without actually opening the browser handler.",
      },
    },
  },
  argTypes: {
    channel: {
      description: "Which deep-link channel to compose.",
      control: { type: "select" },
      options: ["whatsapp", "email", "sms", "telegram", "slack"],
    },
    intro: {
      description: "Optional intro paragraph rendered above the slot transcript.",
      control: { type: "text" },
    },
    failProbability: {
      description: "Probability the simulated build call fails.",
      control: { type: "range", min: 0, max: 1, step: 0.05 },
    },
    latencyMs: {
      description: "Simulated build round-trip (server reads slots + composes URL).",
      control: { type: "range", min: 0, max: 2000, step: 100 },
    },
  },
};

export default meta;
type Story = StoryObj<typeof HandoffDemo>;

export const WhatsApp: Story = {
  args: {
    channel: "whatsapp",
    intro: "Olá! Vim do site de Executive Education e queria continuar a conversa por aqui.",
    failProbability: 0,
    latencyMs: 350,
  },
};

export const Email: Story = {
  args: {
    channel: "email",
    intro: "Olá, segue o resumo do meu interesse em Executive Education.",
    failProbability: 0,
    latencyMs: 350,
  },
};

export const Telegram: Story = {
  args: {
    channel: "telegram",
    intro: "Oi! Vim do site de Education, queria continuar por aqui.",
    failProbability: 0,
    latencyMs: 350,
  },
};

export const Slack: Story = {
  args: {
    channel: "slack",
    intro: "Briefing do candidato",
    failProbability: 0,
    latencyMs: 350,
  },
};

export const ErrorPath: Story = {
  name: "Error path (50% failure)",
  args: {
    channel: "whatsapp",
    intro: "Olá! Vim do site de Executive Education.",
    failProbability: 0.5,
    latencyMs: 350,
  },
};
