import type { Meta, StoryObj } from "@storybook/react-vite";
import { useMemo, useState } from "react";
import { missionDocuments, resolveDocsMock } from "../__mocks__/fixtures";

/**
 * # useAnswerAsApp
 *
 * Convenience hook that turns the assistant's answer into an **interactive
 * mini-app** instead of a wall of prose. It is a thin wrapper over
 * `useGenerativeUI` pre-wired with the built-in answer-as-app components
 * (`comparison_table` / `spec_card` / `configurator`) that match the backend's
 * built-in client tools — advertised automatically when the agent has
 * `answerAsAppEnabled`.
 *
 * When the model decides a structured UI beats text (e.g. "compare X vs Y", or
 * "configure a build"), it emits a client-tool call. The hook resolves that
 * call against its `registry`, exposes the rendered widgets as `items`, and the
 * widgets call back via `respond(...)` so the user's in-app choices flow back
 * into the conversation — the answer becomes a tool the user can _operate_.
 *
 * ## Key Features
 * - **Pre-wired registry**: `comparison_table`, `spec_card`, `configurator`
 *   ready out of the box — no manual component registration
 * - **Spread-in client tools**: `clientTools` drop straight into `useTuringChat`
 *   so the agent knows which app components it may render
 * - **Live `items`**: the resolved widget instances, rendered with
 *   `TuringGenerativeContent` using the hook's `registry`
 * - **`respond(...)` round-trip**: in-widget interactions (toggle a metric,
 *   drag a slider, pick a config) post a structured tool result back to chat
 * - **Override-friendly**: pass `extra` to add or replace a component
 *   (e.g. a branded comparison table) — extras win on name collision
 *
 * ## Usage
 * ```tsx
 * const app = useAnswerAsApp();
 * const chat = useTuringChat({ agent, clientTools: app.clientTools });
 *
 * return (
 *   <>
 *     <ChatThread messages={chat.messages} />
 *     <TuringGenerativeContent
 *       items={app.items}
 *       registry={app.registry}
 *       onRespond={app.respond}
 *     />
 *   </>
 * );
 *
 * // Override a built-in with a branded widget:
 * const app = useAnswerAsApp({ comparison_table: MyBrandedComparison });
 * ```
 *
 * ## When to use
 *
 * - **Comparisons**: "Apollo 11 vs Artemis 1" → a metric-toggle table beats a
 *   paragraph the user has to parse
 * - **Spec sheets**: a single product/entity rendered as a structured spec card
 * - **Configurators**: "build me a mission profile" → sliders + toggles that
 *   recompute live and post the final config back to the agent
 * - Any answer where **interaction > reading**: the user explores rather than
 *   re-prompts
 *
 * ## About this story
 *
 * Fully self-contained — **no real hook, Provider, or network**. A local state
 * machine simulates the agent: typing "compare Apollo 11 vs Artemis 1" emits a
 * faux `comparison_table` client-tool call, which renders as a genuinely
 * interactive widget (toggle which metrics show, drag a weight slider to score
 * each mission). The Space Missions data is drawn from the shared
 * `missionDocuments` fixture (Apollo 11) plus a local Artemis 1 record.
 */

/* ── Local types (kept independent of the hook's return types) ───────────── */

interface MissionMetric {
  key: string;
  label: string;
  /** Per-mission display value, keyed by mission id. */
  values: Record<string, string>;
  /** 0–100 normalized score used by the weighted comparison. */
  scores: Record<string, number>;
}

interface ComparisonApp {
  kind: "comparison_table";
  title: string;
  missionIds: string[];
  metrics: MissionMetric[];
}

type SimMessage =
  | { role: "user"; text: string }
  | { role: "assistant"; text: string; app?: ComparisonApp };

/* ── Mission data ─────────────────────────────────────────────────────────
   Apollo 11 comes from the shared fixture; Artemis 1 is local to this story. */

const apollo = resolveDocsMock(missionDocuments).find((d) =>
  d.title.includes("Apollo 11"),
);

const MISSIONS: Record<string, { name: string; tag: string; tagColor: string }> = {
  "apollo-11": {
    name: apollo?.title ?? "Apollo 11",
    tag: "Crewed · 1969",
    tagColor: "#00d4ff",
  },
  "artemis-1": {
    name: "Artemis 1",
    tag: "Uncrewed · 2022",
    tagColor: "#ff8c00",
  },
};

const COMPARISON: ComparisonApp = {
  kind: "comparison_table",
  title: "Apollo 11 vs Artemis 1",
  missionIds: ["apollo-11", "artemis-1"],
  metrics: [
    {
      key: "crew",
      label: "Crew size",
      values: { "apollo-11": "3 astronauts", "artemis-1": "0 (uncrewed)" },
      scores: { "apollo-11": 100, "artemis-1": 0 },
    },
    {
      key: "duration",
      label: "Mission duration",
      values: { "apollo-11": "8 days", "artemis-1": "25.5 days" },
      scores: { "apollo-11": 31, "artemis-1": 100 },
    },
    {
      key: "distance",
      label: "Max distance from Earth",
      values: { "apollo-11": "≈ 400,000 km", "artemis-1": "≈ 432,000 km" },
      scores: { "apollo-11": 93, "artemis-1": 100 },
    },
    {
      key: "thrust",
      label: "Launch vehicle thrust",
      values: { "apollo-11": "Saturn V · 35 MN", "artemis-1": "SLS Block 1 · 39 MN" },
      scores: { "apollo-11": 90, "artemis-1": 100 },
    },
    {
      key: "landing",
      label: "Moon landing",
      values: { "apollo-11": "Yes — Sea of Tranquility", "artemis-1": "No — lunar flyby" },
      scores: { "apollo-11": 100, "artemis-1": 0 },
    },
  ],
};

const GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";
const TRIGGER = "compare apollo 11 vs artemis 1";

/* ── The rendered mini-app (the "answer as an app") ──────────────────────── */

function ComparisonWidget({
  app,
  dark,
  onRespond,
}: {
  app: ComparisonApp;
  dark: boolean;
  onRespond: (summary: string) => void;
}) {
  // Interactive state #1 — which metrics are visible.
  const [enabled, setEnabled] = useState<Record<string, boolean>>(
    () => Object.fromEntries(app.metrics.map((m) => [m.key, true])),
  );
  // Interactive state #2 — a single "weight" slider that biases the score.
  const [recencyWeight, setRecencyWeight] = useState(50);
  const [responded, setResponded] = useState(false);

  const surface = dark ? "#16161f" : "#ffffff";
  const border = dark ? "#1e1e2e" : "#e2e8f0";
  const ink = dark ? "#e2e8f0" : "#0f172a";
  const muted = dark ? "#94a3b8" : "#64748b";
  const rowAlt = dark ? "#1b1b27" : "#f8fafc";

  // recencyWeight=100 fully favours the newer mission (artemis-1).
  const weighted = useMemo(() => {
    const recencyBonus: Record<string, number> = {
      "apollo-11": (100 - recencyWeight) * 0.4,
      "artemis-1": recencyWeight * 0.4,
    };
    const totals: Record<string, number> = {};
    for (const id of app.missionIds) {
      const base = app.metrics
        .filter((m) => enabled[m.key])
        .reduce((sum, m) => sum + m.scores[id], 0);
      const count = app.metrics.filter((m) => enabled[m.key]).length || 1;
      totals[id] = Math.round(base / count + recencyBonus[id]);
    }
    return totals;
  }, [app, enabled, recencyWeight]);

  const winner = app.missionIds.reduce((best, id) =>
    weighted[id] > weighted[best] ? id : best,
  );

  function toggleMetric(key: string) {
    setEnabled((prev) => ({ ...prev, [key]: !prev[key] }));
  }

  return (
    <div
      style={{
        border: `1px solid ${border}`,
        borderRadius: "16px",
        overflow: "hidden",
        background: surface,
        boxShadow: dark ? "none" : "0 8px 24px rgba(15,23,42,0.08)",
      }}
    >
      {/* Widget header — signals this is a rendered tool, not text */}
      <div
        style={{
          background: GRADIENT,
          color: "white",
          padding: "14px 18px",
          display: "flex",
          alignItems: "center",
          gap: "10px",
        }}
      >
        <span style={{ fontSize: "18px" }}>🚀</span>
        <div>
          <div style={{ fontWeight: 700, fontSize: "15px" }}>{app.title}</div>
          <div style={{ fontSize: "11px", opacity: 0.85 }}>
            comparison_table · rendered by the agent
          </div>
        </div>
      </div>

      {/* Mission column headers */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1.4fr 1fr 1fr",
          padding: "12px 18px",
          borderBottom: `1px solid ${border}`,
          fontSize: "12px",
        }}
      >
        <div style={{ color: muted, fontWeight: 600 }}>Metric</div>
        {app.missionIds.map((id) => (
          <div key={id} style={{ textAlign: "center" }}>
            <div style={{ fontWeight: 700, color: ink }}>{MISSIONS[id].name}</div>
            <div style={{ color: MISSIONS[id].tagColor, fontSize: "11px" }}>
              {MISSIONS[id].tag}
            </div>
          </div>
        ))}
      </div>

      {/* Metric rows — each toggleable */}
      {app.metrics.map((m, i) => {
        const on = enabled[m.key];
        return (
          <button
            key={m.key}
            type="button"
            onClick={() => toggleMetric(m.key)}
            style={{
              display: "grid",
              gridTemplateColumns: "1.4fr 1fr 1fr",
              alignItems: "center",
              width: "100%",
              textAlign: "left",
              gap: 0,
              padding: "10px 18px",
              border: "none",
              borderBottom: `1px solid ${border}`,
              background: i % 2 ? rowAlt : "transparent",
              cursor: "pointer",
              opacity: on ? 1 : 0.4,
              fontSize: "13px",
              color: ink,
            }}
          >
            <span style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}>
              <span
                aria-hidden
                style={{
                  width: 14,
                  height: 14,
                  borderRadius: "4px",
                  border: `2px solid ${on ? "#4f46e5" : muted}`,
                  background: on ? "#4f46e5" : "transparent",
                  display: "inline-block",
                  flexShrink: 0,
                }}
              />
              {m.label}
            </span>
            {app.missionIds.map((id) => (
              <span key={id} style={{ textAlign: "center", color: on ? ink : muted }}>
                {m.values[id]}
              </span>
            ))}
          </button>
        );
      })}

      {/* Recency-weight slider — the second interactive control */}
      <div style={{ padding: "14px 18px", borderBottom: `1px solid ${border}` }}>
        <label
          style={{
            display: "block",
            fontSize: "12px",
            fontWeight: 600,
            color: muted,
            marginBottom: "8px",
          }}
        >
          Bias toward newer missions: <strong style={{ color: ink }}>{recencyWeight}%</strong>
        </label>
        <input
          type="range"
          min={0}
          max={100}
          value={recencyWeight}
          onChange={(e) => setRecencyWeight(Number(e.target.value))}
          style={{ width: "100%", accentColor: "#4f46e5" }}
        />
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", color: muted }}>
          <span>Favor Apollo (legacy)</span>
          <span>Favor Artemis (modern)</span>
        </div>
      </div>

      {/* Live weighted score + winner badge */}
      <div style={{ padding: "14px 18px", display: "flex", gap: "12px" }}>
        {app.missionIds.map((id) => {
          const isWinner = id === winner;
          return (
            <div
              key={id}
              style={{
                flex: 1,
                borderRadius: "12px",
                padding: "12px",
                textAlign: "center",
                background: isWinner ? GRADIENT : rowAlt,
                color: isWinner ? "white" : ink,
                border: `1px solid ${isWinner ? "transparent" : border}`,
              }}
            >
              <div style={{ fontSize: "11px", opacity: 0.8 }}>{MISSIONS[id].name}</div>
              <div style={{ fontSize: "26px", fontWeight: 800 }}>{weighted[id]}</div>
              <div style={{ fontSize: "11px" }}>{isWinner ? "▲ leads" : "score"}</div>
            </div>
          );
        })}
      </div>

      {/* respond() round-trip — posts the user's choices back into chat */}
      <div style={{ padding: "0 18px 16px" }}>
        <button
          type="button"
          disabled={responded}
          onClick={() => {
            const shown = app.metrics.filter((m) => enabled[m.key]).map((m) => m.label);
            onRespond(
              `${MISSIONS[winner].name} leads (${weighted[winner]} pts) ` +
                `with recency bias ${recencyWeight}% across ${shown.length} metric(s).`,
            );
            setResponded(true);
          }}
          style={{
            width: "100%",
            padding: "11px",
            borderRadius: "10px",
            border: "none",
            background: responded ? (dark ? "#334155" : "#cbd5e1") : GRADIENT,
            color: "white",
            fontWeight: 600,
            fontSize: "13px",
            cursor: responded ? "default" : "pointer",
          }}
        >
          {responded ? "✓ Sent to chat" : "Send this comparison to the agent"}
        </button>
      </div>
    </div>
  );
}

/* ── The simulated chat surface ──────────────────────────────────────────── */

function AnswerAsAppDemo({ dark, autoAsk }: { dark: boolean; autoAsk: boolean }) {
  const initial: SimMessage[] = autoAsk
    ? [
        { role: "user", text: "Compare Apollo 11 vs Artemis 1" },
        {
          role: "assistant",
          text: "Here's an interactive comparison — toggle metrics and bias by era:",
          app: COMPARISON,
        },
      ]
    : [];

  const [messages, setMessages] = useState<SimMessage[]>(initial);
  const [input, setInput] = useState("");

  const page = dark ? "#0a0a0f" : "#f8fafc";
  const surface = dark ? "#16161f" : "#ffffff";
  const border = dark ? "#1e1e2e" : "#e2e8f0";
  const ink = dark ? "#e2e8f0" : "#0f172a";
  const muted = dark ? "#94a3b8" : "#64748b";

  function send(text: string) {
    const q = text.trim();
    if (!q) return;
    const isCompare = q.toLowerCase().includes("compare") || q.toLowerCase() === TRIGGER;
    setMessages((prev) => [
      ...prev,
      { role: "user", text: q },
      isCompare
        ? {
            role: "assistant",
            text: "Here's an interactive comparison — toggle metrics and bias by era:",
            app: COMPARISON,
          }
        : {
            role: "assistant",
            text:
              'Try asking me to "compare Apollo 11 vs Artemis 1" — I\'ll render an ' +
              "interactive comparison app instead of a paragraph.",
          },
    ]);
    setInput("");
  }

  function respond(summary: string) {
    setMessages((prev) => [
      ...prev,
      { role: "user", text: `📊 ${summary}` },
      {
        role: "assistant",
        text: "Got it — I'll factor that weighting into any follow-up recommendation.",
      },
    ]);
  }

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        width: "560px",
        maxWidth: "100%",
        background: page,
        borderRadius: "16px",
        border: `1px solid ${border}`,
        overflow: "hidden",
      }}
    >
      <div
        style={{
          background: GRADIENT,
          color: "white",
          padding: "12px 18px",
          fontWeight: 700,
          fontSize: "14px",
        }}
      >
        🛰️ Space Missions Agent · answer-as-app
      </div>

      <div style={{ padding: "16px", display: "flex", flexDirection: "column", gap: "12px" }}>
        {messages.length === 0 && (
          <div style={{ color: muted, fontSize: "13px", textAlign: "center", padding: "20px 0" }}>
            Ask me to <strong>“compare Apollo 11 vs Artemis 1”</strong> 🚀
          </div>
        )}

        {messages.map((m, i) => (
          <div
            key={i}
            style={{
              alignSelf: m.role === "user" ? "flex-end" : "flex-start",
              maxWidth: "app" in m && m.app ? "100%" : "85%",
              width: "app" in m && m.app ? "100%" : "auto",
            }}
          >
            {m.role === "user" ? (
              <div
                style={{
                  background: GRADIENT,
                  color: "white",
                  padding: "9px 14px",
                  borderRadius: "14px 14px 4px 14px",
                  fontSize: "13px",
                }}
              >
                {m.text}
              </div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                <div
                  style={{
                    background: surface,
                    color: ink,
                    padding: "9px 14px",
                    borderRadius: "14px 14px 14px 4px",
                    fontSize: "13px",
                    border: `1px solid ${border}`,
                  }}
                >
                  {m.text}
                </div>
                {m.app && (
                  <ComparisonWidget app={m.app} dark={dark} onRespond={respond} />
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Composer */}
      <div style={{ display: "flex", gap: "8px", padding: "12px 16px", borderTop: `1px solid ${border}` }}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") send(input);
          }}
          placeholder="Ask the agent…"
          style={{
            flex: 1,
            padding: "9px 12px",
            borderRadius: "10px",
            border: `1px solid ${border}`,
            background: dark ? "#0f0f17" : "white",
            color: ink,
            fontSize: "13px",
          }}
        />
        <button
          type="button"
          onClick={() => send(input)}
          style={{
            padding: "9px 16px",
            borderRadius: "10px",
            border: "none",
            background: GRADIENT,
            color: "white",
            fontWeight: 600,
            fontSize: "13px",
            cursor: "pointer",
          }}
        >
          Send
        </button>
      </div>

      <div style={{ padding: "0 16px 12px" }}>
        <button
          type="button"
          onClick={() => send("Compare Apollo 11 vs Artemis 1")}
          style={{
            width: "100%",
            padding: "8px",
            borderRadius: "8px",
            border: `1px dashed ${border}`,
            background: "transparent",
            color: muted,
            fontSize: "12px",
            cursor: "pointer",
          }}
        >
          ⚡ Quick prompt: “Compare Apollo 11 vs Artemis 1”
        </button>
      </div>
    </div>
  );
}

const meta: Meta<typeof AnswerAsAppDemo> = {
  title: "AI & Chat/useAnswerAsApp",
  component: AnswerAsAppDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Renders the assistant's answer as an interactive mini-app (the built-in `comparison_table` component) instead of static prose. Fully simulated — no real hook, Provider, or network. Asking “compare Apollo 11 vs Artemis 1” makes the faux agent emit a `comparison_table` client-tool call that renders as a widget you can operate: toggle which metrics show, drag the recency-bias slider to re-score the missions live, then send the result back into the conversation via `respond(...)`.",
      },
    },
  },
  argTypes: {
    dark: {
      description: "Render on the dark Viglet surface (#0a0a0f / #16161f).",
      control: { type: "boolean" },
    },
    autoAsk: {
      description: "Pre-seed the thread with the comparison already rendered.",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof AnswerAsAppDemo>;

export const ComparisonApp: Story = {
  name: "🚀 Comparison app (Apollo 11 vs Artemis 1)",
  args: { dark: false, autoAsk: true },
};

export const EmptyThread: Story = {
  name: "💬 Empty thread — type to trigger the app",
  args: { dark: false, autoAsk: false },
};

export const DarkMode: Story = {
  name: "🌑 Dark mode (mission control)",
  args: { dark: true, autoAsk: true },
};
