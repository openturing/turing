import type { Meta, StoryObj } from "@storybook/react-vite";
import { useRef, useState } from "react";
import { missionDocuments } from "../__mocks__/fixtures";
import type { TurDocument } from "../../core/types";

/**
 * # useTuringSemanticChat
 *
 * Semantic Navigation chat hook (T404). It binds a single
 * `TurLLMInstance` to `POST /v2/llm/{id}/semantic-chat` and runs the model
 * with the **Semantic Navigation tool set** — `list_sites`,
 * `get_site_fields`, `search_site` (plus `catalog_search` and any MCP tools).
 *
 * The model "talks to the index" through tool calls: when a user asks a
 * question, the LLM decides which tools to invoke, the backend executes them
 * against the search engine, and the results are fed back into the model
 * before it streams a grounded answer. **No AI Agent, no site RAG, no
 * chat-flow** is involved — just an LLM instance and the navigation tools.
 *
 * ## Key Features
 * - `send(content)`: append the user turn, POST the full history, stream back
 *   the assistant reply token-by-token (`onToken` under the hood)
 * - `messages`: ordered `ChatMessage[]` (oldest first), the assistant bubble
 *   grows as tokens arrive
 * - `status` / `isStreaming`: `"idle" | "loading" | "success" | "error"`,
 *   with `isStreaming === (status === "loading")` for input gating
 * - `stop()`: abort the in-flight turn via `AbortController` (rolls back the
 *   pending bubble)
 * - `reset()`: clear the conversation and error state
 * - `error`: message from the last failed send
 *
 * > **Tool activity** is server-side: the model's tool calls are executed on
 * > the backend and only the final streamed text reaches the hook. This story
 * > *visualises* that hidden tool loop so you can see how a tool-calling chat
 * > behaves before wiring real telemetry.
 *
 * ## Usage
 * ```tsx
 * const { messages, send, isStreaming, status, stop, reset } =
 *   useTuringSemanticChat({ llmInstanceId: "gpt-4o-mini" });
 *
 * return (
 *   <>
 *     {messages.map((m) => (
 *       <Bubble key={m.id} role={m.role}>{m.content}</Bubble>
 *     ))}
 *     <ChatInput
 *       disabled={isStreaming}
 *       onSubmit={(text) => send(text)}
 *     />
 *     {isStreaming && <button onClick={stop}>Stop</button>}
 *     <button onClick={reset}>Clear</button>
 *   </>
 * );
 * ```
 *
 * ## When to use
 *
 * - **"Talk to the index" widgets** — a chat box that answers from the live
 *   search engine without standing up an AI Agent or RAG pipeline
 * - **Mission-Control style consoles** — let operators ask natural-language
 *   questions ("which Apollo missions failed?") and have the LLM pick the
 *   right `search_site` / `get_site_fields` calls
 * - **Showing your work** — when transparency matters, surface the tool calls
 *   so users trust *where* the answer came from
 *
 * ## About this story
 *
 * This story is **fully self-contained** — it does NOT mount the real hook,
 * `TuringProvider`, or hit the network. A local reducer-free component
 * simulates the semantic-chat turn: it picks tool calls from a small script,
 * animates each `🛠 tool(...) → N results` step against the
 * `missionDocuments` fixture, then streams a synthesised answer word-by-word —
 * exactly the *feel* of the real tool-calling loop, with zero backend.
 */

/* ── Local demo state types (the real hook return is simulated) ── */

type DemoStatus = "idle" | "thinking" | "tooling" | "streaming" | "done";

interface ToolStep {
  readonly tool: "search_site" | "get_site_fields" | "list_sites";
  readonly arg: string;
  readonly resultCount: number;
}

interface DemoTurn {
  readonly id: string;
  readonly question: string;
  readonly toolSteps: ToolStep[];
  /** Step index currently revealed (-1 = none yet). */
  revealedSteps: number;
  /** Assistant answer text streamed so far. */
  answer: string;
  readonly fullAnswer: string;
  status: DemoStatus;
}

/* ── A tiny "router": map a question to the tool calls the model would make ── */

const MISSIONS = missionDocuments;

function countByOutcome(outcome: string): number {
  return MISSIONS.filter((d: TurDocument) => d.fields.outcome === outcome).length;
}

function planTurn(question: string): {
  toolSteps: ToolStep[];
  answer: string;
} {
  const q = question.toLowerCase();

  if (q.includes("fail") || q.includes("disaster") || q.includes("lost")) {
    const failures = MISSIONS.filter((d) => d.fields.outcome === "Failure");
    return {
      toolSteps: [
        { tool: "get_site_fields", arg: "space-missions", resultCount: 6 },
        { tool: "search_site", arg: "outcome:Failure", resultCount: failures.length },
      ],
      answer: `I found ${failures.length} mission with a Failure outcome: ${failures
        .map((d) => d.fields.title)
        .join(", ")}. The Challenger STS-51-L broke apart 73 seconds after launch in 1986, with the loss of all seven crew members. Out of ${MISSIONS.length} catalogued missions, ${countByOutcome("Success")} succeeded.`,
    };
  }

  if (q.includes("apollo")) {
    const apollo = MISSIONS.filter((d) =>
      String(d.fields.title).toLowerCase().includes("apollo"),
    );
    return {
      toolSteps: [{ tool: "search_site", arg: "apollo", resultCount: apollo.length }],
      answer: `Apollo 11 was the first crewed mission to land on the Moon — Neil Armstrong and Buzz Aldrin touched down on July 20, 1969. It was a NASA mission with a crew of 3 and an 8-day duration. Outcome: Success. 🚀`,
    };
  }

  if (q.includes("esa") || q.includes("comet")) {
    const esa = MISSIONS.filter((d) =>
      Array.isArray(d.fields.agency) ? d.fields.agency.includes("ESA") : false,
    );
    return {
      toolSteps: [
        { tool: "list_sites", arg: "—", resultCount: 1 },
        { tool: "search_site", arg: "agency:ESA", resultCount: esa.length },
      ],
      answer: `ESA's Rosetta performed the first-ever landing on a comet nucleus (67P/Churyumov–Gerasimenko) after a 12-year journey. It launched March 2, 2004. That's the only ESA mission in this index — the other ${MISSIONS.length - esa.length} are NASA.`,
    };
  }

  // Default: a broad search across everything.
  return {
    toolSteps: [
      { tool: "list_sites", arg: "—", resultCount: 1 },
      { tool: "search_site", arg: "*", resultCount: MISSIONS.length },
    ],
    answer: `The Space Missions index holds ${MISSIONS.length} missions spanning ${countByOutcome(
      "Success",
    )} successes and ${countByOutcome(
      "Failure",
    )} failure — from Apollo 11 (1969) to Mars Perseverance (2021). Ask me about a specific mission, agency, or outcome.`,
  };
}

/* ── Suggested prompts per story ── */

const PROMPTS = [
  "Which missions failed?",
  "Tell me about Apollo 11",
  "What did ESA do?",
  "Summarise the whole index",
];

const TOOL_ICON: Record<ToolStep["tool"], string> = {
  search_site: "🔍",
  get_site_fields: "🗂️",
  list_sites: "📚",
};

/* ── The simulated Mission Control chat ── */

function SemanticChatDemo({
  llmInstanceId,
  theme,
  speed,
}: {
  llmInstanceId: string;
  theme: "light" | "dark";
  speed: "fast" | "cinematic";
}) {
  const [turns, setTurns] = useState<DemoTurn[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  const isDark = theme === "dark";

  const stepDelay = speed === "cinematic" ? 750 : 320;
  const tokenDelay = speed === "cinematic" ? 55 : 22;

  function patch(id: string, mutator: (t: DemoTurn) => DemoTurn) {
    setTurns((prev) => prev.map((t) => (t.id === id ? mutator(t) : t)));
  }

  function send(raw: string) {
    const question = raw.trim();
    if (!question || busy) return;
    setInput("");
    setBusy(true);

    const { toolSteps, answer } = planTurn(question);
    const id = `${Date.now().toString(36)}`;
    const turn: DemoTurn = {
      id,
      question,
      toolSteps,
      revealedSteps: 0,
      answer: "",
      fullAnswer: answer,
      status: "thinking",
    };
    setTurns((prev) => [...prev, turn]);

    let clock = stepDelay; // brief "thinking" beat before the first tool fires

    // Reveal each tool step in sequence.
    toolSteps.forEach((_, i) => {
      timers.current.push(
        setTimeout(() => {
          patch(id, (t) => ({ ...t, status: "tooling", revealedSteps: i + 1 }));
        }, clock),
      );
      clock += stepDelay;
    });

    // Then stream the answer word-by-word.
    const words = answer.split(" ");
    timers.current.push(
      setTimeout(() => patch(id, (t) => ({ ...t, status: "streaming" })), clock),
    );
    words.forEach((_, wi) => {
      timers.current.push(
        setTimeout(() => {
          patch(id, (t) => ({
            ...t,
            answer: words.slice(0, wi + 1).join(" "),
          }));
        }, clock + (wi + 1) * tokenDelay),
      );
    });

    const total = clock + (words.length + 1) * tokenDelay;
    timers.current.push(
      setTimeout(() => {
        patch(id, (t) => ({ ...t, status: "done" }));
        setBusy(false);
      }, total),
    );
  }

  function reset() {
    timers.current.forEach(clearTimeout);
    timers.current = [];
    setTurns([]);
    setBusy(false);
    setInput("");
  }

  const bg = isDark ? "#0a0a23" : "#ffffff";
  const cardBg = isDark ? "#11112e" : "#f8fafc";
  const border = isDark ? "#1e1e44" : "#e2e8f0";
  const muted = isDark ? "#94a3b8" : "#64748b";
  const fg = isDark ? "#e2e8f0" : "#1e293b";
  const gradient = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        width: "620px",
        maxWidth: "100%",
        color: fg,
        background: bg,
        border: `1px solid ${border}`,
        borderRadius: "14px",
        overflow: "hidden",
      }}
    >
      {/* Header — Mission Control */}
      <div style={{ background: gradient, padding: "14px 18px", color: "white" }}>
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <span style={{ fontSize: "20px" }}>🚀</span>
          <div>
            <div style={{ fontWeight: 700, fontSize: "15px" }}>Mission Control</div>
            <div style={{ fontSize: "11px", opacity: 0.85 }}>
              semantic chat · llm: <code>{llmInstanceId}</code>
            </div>
          </div>
          <span
            style={{
              marginLeft: "auto",
              fontSize: "10px",
              fontWeight: 700,
              textTransform: "uppercase",
              letterSpacing: "0.08em",
              padding: "3px 9px",
              borderRadius: "999px",
              background: "rgba(255,255,255,0.18)",
            }}
          >
            {busy ? "● live" : "idle"}
          </span>
        </div>
      </div>

      {/* Transcript */}
      <div
        style={{
          padding: "16px",
          minHeight: "300px",
          maxHeight: "420px",
          overflowY: "auto",
          display: "flex",
          flexDirection: "column",
          gap: "16px",
        }}
      >
        {turns.length === 0 && (
          <div style={{ textAlign: "center", color: muted, padding: "40px 12px" }}>
            <div style={{ fontSize: "34px", marginBottom: "8px" }}>🛰️</div>
            <div style={{ fontSize: "13px" }}>
              Ask Mission Control about launches, agencies, or outcomes.
              <br />
              Watch it call <code>search_site</code> / <code>get_site_fields</code>{" "}
              before it answers.
            </div>
          </div>
        )}

        {turns.map((turn) => (
          <div key={turn.id} style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
            {/* User bubble */}
            <div style={{ alignSelf: "flex-end", maxWidth: "78%" }}>
              <div
                style={{
                  background: gradient,
                  color: "white",
                  padding: "9px 13px",
                  borderRadius: "14px 14px 4px 14px",
                  fontSize: "13px",
                }}
              >
                {turn.question}
              </div>
            </div>

            {/* Tool-activity panel — visually distinct from chat bubbles */}
            {turn.toolSteps.length > 0 && (
              <div
                style={{
                  alignSelf: "flex-start",
                  maxWidth: "92%",
                  width: "100%",
                  background: isDark ? "#0c0c28" : "#f1f5f9",
                  border: `1px dashed ${isDark ? "#34346a" : "#cbd5e1"}`,
                  borderRadius: "10px",
                  padding: "8px 10px",
                  fontFamily:
                    "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
                  fontSize: "11.5px",
                }}
              >
                <div
                  style={{
                    color: muted,
                    fontSize: "10px",
                    textTransform: "uppercase",
                    letterSpacing: "0.08em",
                    marginBottom: "5px",
                  }}
                >
                  Tool calls
                </div>
                {turn.toolSteps.map((step, i) => {
                  const revealed = i < turn.revealedSteps;
                  const running =
                    i === turn.revealedSteps - 1 && turn.status === "tooling";
                  return (
                    <div
                      key={`${step.tool}-${i}`}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "6px",
                        padding: "3px 0",
                        opacity: revealed ? 1 : 0.28,
                        transition: "opacity 0.2s ease",
                      }}
                    >
                      <span>{TOOL_ICON[step.tool]}</span>
                      <span style={{ color: isDark ? "#7dd3fc" : "#2563eb", fontWeight: 600 }}>
                        {step.tool}
                      </span>
                      <span style={{ color: muted }}>({step.arg})</span>
                      {revealed && (
                        <span style={{ marginLeft: "auto", color: isDark ? "#86efac" : "#15803d" }}>
                          {running ? (
                            <span style={{ color: muted }}>running…</span>
                          ) : (
                            `→ ${step.resultCount} result${step.resultCount === 1 ? "" : "s"}`
                          )}
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            )}

            {/* Assistant bubble */}
            {(turn.status === "streaming" || turn.status === "done") && (
              <div style={{ alignSelf: "flex-start", maxWidth: "88%" }}>
                <div
                  style={{
                    background: cardBg,
                    border: `1px solid ${border}`,
                    color: fg,
                    padding: "10px 13px",
                    borderRadius: "14px 14px 14px 4px",
                    fontSize: "13px",
                    lineHeight: 1.5,
                  }}
                >
                  {turn.answer}
                  {turn.status === "streaming" && (
                    <span
                      style={{
                        display: "inline-block",
                        width: 6,
                        height: 14,
                        background: "#4f46e5",
                        marginLeft: 2,
                        verticalAlign: "middle",
                        animation: "scblink 1s steps(2) infinite",
                      }}
                    />
                  )}
                </div>
              </div>
            )}

            {turn.status === "thinking" && (
              <div style={{ alignSelf: "flex-start", color: muted, fontSize: "12px", padding: "2px 4px" }}>
                <span style={{ animation: "scpulse 1s ease-in-out infinite" }}>
                  🤔 deciding which tools to call…
                </span>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Suggested prompts */}
      <div
        style={{
          display: "flex",
          flexWrap: "wrap",
          gap: "6px",
          padding: "0 16px 10px",
        }}
      >
        {PROMPTS.map((p) => (
          <button
            key={p}
            type="button"
            disabled={busy}
            onClick={() => send(p)}
            style={{
              fontSize: "11px",
              padding: "5px 10px",
              borderRadius: "999px",
              border: `1px solid ${border}`,
              background: "transparent",
              color: muted,
              cursor: busy ? "not-allowed" : "pointer",
            }}
          >
            {p}
          </button>
        ))}
      </div>

      {/* Composer */}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          send(input);
        }}
        style={{
          display: "flex",
          gap: "8px",
          padding: "12px 16px",
          borderTop: `1px solid ${border}`,
          background: isDark ? "#0c0c28" : "#ffffff",
        }}
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Ask about a launch, agency, or outcome…"
          disabled={busy}
          style={{
            flex: 1,
            padding: "9px 12px",
            borderRadius: "8px",
            border: `1px solid ${border}`,
            background: isDark ? "#11112e" : "#ffffff",
            color: fg,
            fontSize: "13px",
            outline: "none",
          }}
        />
        <button
          type="submit"
          disabled={busy || !input.trim()}
          style={{
            padding: "9px 16px",
            borderRadius: "8px",
            border: "none",
            background: busy || !input.trim() ? "#94a3b8" : gradient,
            color: "white",
            fontWeight: 600,
            fontSize: "13px",
            cursor: busy || !input.trim() ? "not-allowed" : "pointer",
          }}
        >
          {busy ? "…" : "Send"}
        </button>
        <button
          type="button"
          onClick={reset}
          disabled={busy && turns.length === 0}
          style={{
            padding: "9px 12px",
            borderRadius: "8px",
            border: `1px solid ${border}`,
            background: "transparent",
            color: muted,
            fontSize: "13px",
            cursor: "pointer",
          }}
        >
          Clear
        </button>
      </form>

      <style>{`
        @keyframes scblink { 50% { opacity: 0; } }
        @keyframes scpulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.45; } }
      `}</style>
    </div>
  );
}

const meta: Meta<typeof SemanticChatDemo> = {
  title: "AI & Chat/useTuringSemanticChat",
  component: SemanticChatDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Semantic Navigation chat (`useTuringSemanticChat`, T404): an LLM instance answers questions by calling the search tools (`search_site`, `get_site_fields`, `list_sites`) against the index. This story is fully simulated — no Provider, no hook, no network — it animates the hidden tool-call loop, then streams a synthesised answer over the `missionDocuments` fixture so you can feel how a tool-calling chat behaves.",
      },
    },
  },
  argTypes: {
    llmInstanceId: {
      description:
        "Identifier of the TurLLMInstance the hook binds to. Required — semantic-chat is keyed by LLM instance with no fallback resolution.",
      control: { type: "text" },
    },
    theme: {
      description: "Light or dark Mission-Control skin.",
      control: { type: "inline-radio" },
      options: ["light", "dark"],
    },
    speed: {
      description:
        "Animation pacing for the simulated tool loop + token stream.",
      control: { type: "inline-radio" },
      options: ["fast", "cinematic"],
    },
  },
};

export default meta;
type Story = StoryObj<typeof SemanticChatDemo>;

export const MissionControl: Story = {
  name: "🚀 Mission Control (light)",
  args: { llmInstanceId: "gpt-4o-mini", theme: "light", speed: "fast" },
};

export const DarkOps: Story = {
  name: "🛰️ Dark Ops Console",
  args: { llmInstanceId: "claude-3-5-sonnet", theme: "dark", speed: "fast" },
};

export const Cinematic: Story = {
  name: "🎬 Cinematic tool loop (watch every step)",
  args: { llmInstanceId: "gpt-4o-mini", theme: "dark", speed: "cinematic" },
};
