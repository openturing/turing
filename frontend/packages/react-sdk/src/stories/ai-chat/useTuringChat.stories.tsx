import type { Meta, StoryObj } from "@storybook/react-vite";
import { useEffect, useRef, useState } from "react";
import { creatureDocuments } from "../__mocks__/fixtures";
import type { TurDocument } from "../../core/types";

/**
 * # useTuringChat
 *
 * Conversational **RAG** (Retrieval-Augmented Generation) hook backed by the
 * site's GenAI endpoint. The visitor asks a question; the backend retrieves the
 * most relevant indexed documents, grounds an LLM answer on them, and streams
 * the reply **token-by-token** over Server-Sent Events. When the answer is
 * complete the hook also surfaces the **sources** (the bestiary entries the
 * model actually used) so the UI can cite them.
 *
 * The whole turn is a small state machine you bind to:
 *
 * - `status` walks `"idle" → "loading" → "success"` (or `"error"`),
 * - `isStreaming` is `true` while tokens are arriving (drive your typing dots),
 * - `messages` grows by one user bubble then one assistant bubble whose
 *   `content` fills in live as tokens stream,
 * - the final assistant message carries `sources` / `citations` for grounding.
 *
 * ## Key Features
 * - **Token streaming**: the assistant bubble's `content` grows char-by-char as
 *   SSE tokens land — no spinner-then-dump, the answer types itself in.
 * - **Grounded sources**: the completed assistant message carries
 *   `sources` (retrieved chunks) so you can render citation chips/cards.
 * - **Lifecycle status**: `status` + `isStreaming` let you gate the composer,
 *   show typing indicators, and render error states.
 * - **Abortable turns**: `stop()` cancels an in-flight stream and rolls the
 *   pending turn back; `reset()` clears the transcript for a new conversation.
 * - **Capability probe**: `enabled` is `null` while unknown, then `true`/`false`
 *   so you can hide the chat entirely when the site has no GenAI configured.
 * - **Optional persistence**: `persist` mirrors the transcript into
 *   `sessionStorage`, scoped per site, so a reload resumes the conversation.
 *
 * ## Usage
 * ```tsx
 * function BestiaryLibrarian() {
 *   const { enabled, messages, send, status, isStreaming, reset } =
 *     useTuringChat({ persist: true });
 *
 *   // Hide the assistant entirely when the site has no GenAI configured.
 *   if (enabled === false) return null;
 *
 *   return (
 *     <div>
 *       {messages.map((m) => (
 *         <Bubble key={m.id} role={m.role}>
 *           {m.content}
 *           {m.sources?.length ? (
 *             <Citations>
 *               {m.sources.map((s) => (
 *                 <SourceChip key={s.id} href={s.url}>{s.title}</SourceChip>
 *               ))}
 *             </Citations>
 *           ) : null}
 *         </Bubble>
 *       ))}
 *
 *       {isStreaming && <TypingDots />}
 *
 *       <Composer
 *         disabled={status === "loading"}
 *         onSubmit={(text) => send(text)}
 *       />
 *       <button onClick={reset}>New conversation</button>
 *     </div>
 *   );
 * }
 * ```
 *
 * ## When to use
 *
 * - **Grounded Q&A over a catalog**: a "Bestiary Librarian" that answers
 *   questions about your indexed content and *cites which entries it used* —
 *   trustworthy answers, not hallucinations.
 * - **Search-adjacent assistant**: pair it with `useTuringSearch` so a visitor
 *   can flip between browsing results and asking a question in natural language.
 * - **Support / docs concierge**: the streaming UX feels responsive even on
 *   long answers, and the sources give users somewhere to click through.
 *
 * For an explicit AI-agent + LLM target (authenticated console UIs), pass
 * `options.agent`; the default (shown here) is the anonymous, site-scoped RAG
 * endpoint.
 *
 * ## About this story
 *
 * **The streaming is fully simulated — there is no backend, no `TuringProvider`,
 * and the real hook is never called.** A local component fakes the hook's
 * surface: on `send()` it appends a user bubble, opens an empty assistant
 * bubble, then fake-streams a scripted answer **token-by-token** with a
 * `setInterval` (mirroring how SSE tokens feel), flips `status` through
 * `loading → success`, and finally attaches the `sources` chips drawn from the
 * Mythical-Creatures fixtures. Click a suggested question, type your own, or hit
 * **Stop** mid-stream to see an aborted turn.
 */

/* ════════════════════════════════════════════════════════════════
   Local demo types — deliberately NOT the hook's exported types, to
   keep the story decoupled from the hook's evolving surface (the task
   rules ask for this). They mirror the shape the real hook returns.
   ════════════════════════════════════════════════════════════════ */

type DemoChatStatus = "idle" | "loading" | "success" | "error";

interface DemoSource {
  readonly id: string;
  readonly title: string;
  readonly url: string;
  readonly snippet: string;
}

interface DemoMessage {
  readonly id: string;
  readonly role: "user" | "assistant";
  content: string;
  readonly sources?: DemoSource[];
}

/* ════════════════════════════════════════════════════════════════
   Scripted Q&A — grounded on the creature fixtures so the "sources"
   chips point at real bestiary entries the answer leans on.
   ════════════════════════════════════════════════════════════════ */

/** Picks the fixture docs an answer cites and maps them to source chips. */
function sourcesFor(ids: string[]): DemoSource[] {
  return ids
    .map((id) => creatureDocuments.find((d: TurDocument) => d.fields.id === id))
    .filter((d): d is TurDocument => Boolean(d))
    .map((d) => ({
      id: String(d.fields.id),
      title: String(d.fields.title),
      url: String(d.fields.url),
      snippet: String(d.fields.description),
    }));
}

interface ScriptedAnswer {
  readonly question: string;
  readonly answer: string;
  readonly sourceIds: string[];
}

const SCRIPT: ReadonlyArray<ScriptedAnswer> = [
  {
    question: "Which creatures are tied to fire?",
    answer:
      "Two entries in the bestiary are bound to fire. The Dragon is a fire-breathing reptilian renowned for both its wisdom and devastating power, while the Phoenix is an immortal bird that bursts into flames and is reborn from its own ashes — a symbol of renewal rather than destruction.",
    sourceIds: ["creature-1", "creature-2"],
  },
  {
    question: "What is the most dangerous creature here?",
    answer:
      "The Kraken tops the bestiary's danger scale at 10/10. It is a colossal sea monster dwelling in the deepest ocean trenches, powerful enough to drag entire ships beneath the waves. The Dragon follows closely at 9/10.",
    sourceIds: ["creature-3", "creature-1"],
  },
  {
    question: "Tell me about a gentle, harmless creature.",
    answer:
      "The Unicorn is the gentlest entry, with a danger level of just 1. It is a magical equine bearing a single spiraling horn, said to purify water and heal wounds simply by its presence.",
    sourceIds: ["creature-4"],
  },
];

const SUGGESTED_QUESTIONS = SCRIPT.map((s) => s.question);

/** Finds the scripted answer whose question best matches free text. */
function matchAnswer(input: string): ScriptedAnswer {
  const q = input.toLowerCase();
  const exact = SCRIPT.find((s) => s.question.toLowerCase() === q);
  if (exact) return exact;
  const fuzzy = SCRIPT.find((s) =>
    s.question
      .toLowerCase()
      .split(/\s+/)
      .some((w) => w.length > 3 && q.includes(w)),
  );
  return fuzzy ?? SCRIPT[0];
}

let demoSeq = 0;
function demoId(): string {
  demoSeq += 1;
  return `demo-${demoSeq}-${Math.random().toString(36).slice(2, 7)}`;
}

/* ════════════════════════════════════════════════════════════════
   The interactive demo — a self-contained fake of useTuringChat.
   ════════════════════════════════════════════════════════════════ */

const BRAND_GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

function BestiaryLibrarianDemo({
  theme,
  streamSpeedMs,
}: {
  theme: "light" | "dark";
  streamSpeedMs: number;
}) {
  const isDark = theme === "dark";
  const [messages, setMessages] = useState<DemoMessage[]>([]);
  const [status, setStatus] = useState<DemoChatStatus>("idle");
  const [input, setInput] = useState("");
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  const isStreaming = status === "loading";

  // Auto-scroll the transcript as tokens stream in.
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages]);

  // Cleanup the fake stream on unmount.
  useEffect(() => {
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, []);

  /** Mirrors `send(content)` from the real hook — token-by-token streaming. */
  function send(rawContent: string) {
    const content = rawContent.trim();
    if (!content || isStreaming) return;

    const userMsg: DemoMessage = { id: demoId(), role: "user", content };
    const assistantId = demoId();
    const assistantMsg: DemoMessage = { id: assistantId, role: "assistant", content: "" };
    setMessages((prev) => [...prev, userMsg, assistantMsg]);
    setStatus("loading");
    setInput("");

    const scripted = matchAnswer(content);
    // Tokenize into word-ish chunks so the stream "feels" like real SSE tokens.
    const tokens = scripted.answer.match(/\S+\s*/g) ?? [scripted.answer];
    let i = 0;

    intervalRef.current = setInterval(() => {
      const token = tokens[i];
      i += 1;
      setMessages((prev) =>
        prev.map((m) => (m.id === assistantId ? { ...m, content: m.content + token } : m)),
      );
      if (i >= tokens.length) {
        if (intervalRef.current) clearInterval(intervalRef.current);
        intervalRef.current = null;
        // Final patch: attach the grounded sources, flip status to success.
        setMessages((prev) =>
          prev.map((m) =>
            m.id === assistantId ? { ...m, sources: sourcesFor(scripted.sourceIds) } : m,
          ),
        );
        setStatus("success");
      }
    }, streamSpeedMs);
  }

  /** Mirrors `stop()` — aborts the in-flight turn, keeps what streamed so far. */
  function stop() {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    setMessages((prev) =>
      prev.map((m, idx) =>
        idx === prev.length - 1 && m.role === "assistant" && !m.content
          ? { ...m, content: "_(stopped)_" }
          : m,
      ),
    );
    setStatus("idle");
  }

  /** Mirrors `reset()` — new conversation. */
  function reset() {
    if (intervalRef.current) clearInterval(intervalRef.current);
    intervalRef.current = null;
    setMessages([]);
    setStatus("idle");
  }

  const bg = isDark ? "#0b1120" : "#ffffff";
  const panelBg = isDark ? "#0f172a" : "#f8fafc";
  const border = isDark ? "#1e293b" : "#e2e8f0";
  const textColor = isDark ? "#e2e8f0" : "#1e293b";
  const mutedColor = isDark ? "#94a3b8" : "#64748b";

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        width: "640px",
        maxWidth: "100%",
        border: `1px solid ${border}`,
        borderRadius: "16px",
        overflow: "hidden",
        background: bg,
        color: textColor,
        boxShadow: "0 10px 40px rgba(15,23,42,0.12)",
      }}
    >
      {/* Header */}
      <div
        style={{
          background: BRAND_GRADIENT,
          color: "white",
          padding: "16px 20px",
          display: "flex",
          alignItems: "center",
          gap: "12px",
        }}
      >
        <span style={{ fontSize: "26px" }}>🐉</span>
        <div>
          <div style={{ fontWeight: 700, fontSize: "15px" }}>Bestiary Librarian</div>
          <div style={{ fontSize: "12px", opacity: 0.85 }}>
            RAG assistant · grounded in the bestiary
          </div>
        </div>
        <span style={{ marginLeft: "auto", display: "inline-flex", alignItems: "center", gap: "6px", fontSize: "11px" }}>
          <span style={{ width: 8, height: 8, borderRadius: "50%", background: "#4ade80" }} />
          enabled
        </span>
      </div>

      {/* Transcript */}
      <div
        ref={scrollRef}
        style={{
          height: "320px",
          overflowY: "auto",
          padding: "16px 20px",
          display: "flex",
          flexDirection: "column",
          gap: "12px",
          background: panelBg,
        }}
      >
        {messages.length === 0 && (
          <div style={{ textAlign: "center", color: mutedColor, fontSize: "13px", margin: "auto 0" }}>
            <div style={{ fontSize: "32px", marginBottom: "8px" }}>📖</div>
            Ask the Librarian about any creature in the bestiary.
            <br />
            Its answers are grounded — and it cites the entries it used.
          </div>
        )}

        {messages.map((m) => {
          const isUser = m.role === "user";
          return (
            <div
              key={m.id}
              style={{
                display: "flex",
                justifyContent: isUser ? "flex-end" : "flex-start",
              }}
            >
              <div style={{ maxWidth: "82%" }}>
                <div
                  style={{
                    padding: "10px 14px",
                    borderRadius: "14px",
                    fontSize: "14px",
                    lineHeight: 1.5,
                    background: isUser ? BRAND_GRADIENT : isDark ? "#1e293b" : "#ffffff",
                    color: isUser ? "white" : textColor,
                    border: isUser ? "none" : `1px solid ${border}`,
                    borderBottomRightRadius: isUser ? "4px" : "14px",
                    borderBottomLeftRadius: isUser ? "14px" : "4px",
                  }}
                >
                  {m.content || (
                    <TypingDots color={isDark ? "#60a5fa" : "#4f46e5"} />
                  )}
                </div>

                {/* Grounded source chips — the bestiary entries the answer used. */}
                {m.sources && m.sources.length > 0 && (
                  <div style={{ marginTop: "8px" }}>
                    <div
                      style={{
                        fontSize: "10px",
                        fontWeight: 700,
                        textTransform: "uppercase",
                        letterSpacing: "0.08em",
                        color: mutedColor,
                        marginBottom: "6px",
                      }}
                    >
                      Sources
                    </div>
                    <div style={{ display: "flex", flexWrap: "wrap", gap: "6px" }}>
                      {m.sources.map((s) => (
                        <a
                          key={s.id}
                          href={s.url}
                          onClick={(e) => e.preventDefault()}
                          title={s.snippet}
                          style={{
                            display: "inline-flex",
                            alignItems: "center",
                            gap: "5px",
                            padding: "4px 10px",
                            borderRadius: "999px",
                            fontSize: "12px",
                            textDecoration: "none",
                            fontWeight: 600,
                            color: isDark ? "#93c5fd" : "#4f46e5",
                            background: isDark ? "rgba(59,130,246,0.12)" : "rgba(79,70,229,0.08)",
                            border: `1px solid ${isDark ? "rgba(59,130,246,0.3)" : "rgba(79,70,229,0.2)"}`,
                          }}
                        >
                          📜 {s.title}
                        </a>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Suggested questions */}
      {messages.length === 0 && (
        <div style={{ padding: "0 20px 4px", background: panelBg, display: "flex", flexWrap: "wrap", gap: "6px" }}>
          {SUGGESTED_QUESTIONS.map((q) => (
            <button
              key={q}
              type="button"
              onClick={() => send(q)}
              style={{
                padding: "6px 12px",
                borderRadius: "999px",
                border: `1px solid ${border}`,
                background: isDark ? "#1e293b" : "#ffffff",
                color: mutedColor,
                fontSize: "12px",
                cursor: "pointer",
              }}
            >
              {q}
            </button>
          ))}
        </div>
      )}

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
          background: bg,
        }}
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Ask about a creature…"
          disabled={isStreaming}
          style={{
            flex: 1,
            padding: "10px 14px",
            borderRadius: "10px",
            border: `1px solid ${border}`,
            background: isDark ? "#0f172a" : "#ffffff",
            color: textColor,
            fontSize: "14px",
            outline: "none",
          }}
        />
        {isStreaming ? (
          <button
            type="button"
            onClick={stop}
            style={{
              padding: "10px 18px",
              borderRadius: "10px",
              border: "none",
              background: "#dc2626",
              color: "white",
              fontWeight: 600,
              fontSize: "13px",
              cursor: "pointer",
            }}
          >
            ◼ Stop
          </button>
        ) : (
          <button
            type="submit"
            disabled={!input.trim()}
            style={{
              padding: "10px 18px",
              borderRadius: "10px",
              border: "none",
              background: input.trim() ? BRAND_GRADIENT : "#cbd5e1",
              color: "white",
              fontWeight: 600,
              fontSize: "13px",
              cursor: input.trim() ? "pointer" : "not-allowed",
            }}
          >
            Send
          </button>
        )}
        {messages.length > 0 && !isStreaming && (
          <button
            type="button"
            onClick={reset}
            style={{
              padding: "10px 14px",
              borderRadius: "10px",
              border: `1px solid ${border}`,
              background: "transparent",
              color: mutedColor,
              fontSize: "13px",
              cursor: "pointer",
            }}
          >
            Clear
          </button>
        )}
      </form>

      {/* Status strip — mirrors the hook's status / isStreaming surface. */}
      <div
        style={{
          padding: "8px 16px",
          borderTop: `1px solid ${border}`,
          background: panelBg,
          fontSize: "11px",
          color: mutedColor,
          display: "flex",
          gap: "16px",
        }}
      >
        <span>
          <strong>status:</strong>{" "}
          <code
            style={{
              padding: "1px 6px",
              borderRadius: "4px",
              background:
                status === "loading"
                  ? "#dbeafe"
                  : status === "success"
                    ? "#dcfce7"
                    : status === "error"
                      ? "#fee2e2"
                      : isDark
                        ? "#1e293b"
                        : "#f1f5f9",
              color:
                status === "loading"
                  ? "#1d4ed8"
                  : status === "success"
                    ? "#166534"
                    : status === "error"
                      ? "#991b1b"
                      : mutedColor,
            }}
          >
            {status}
          </code>
        </span>
        <span>
          <strong>isStreaming:</strong> <code>{String(isStreaming)}</code>
        </span>
        <span>
          <strong>messages:</strong> <code>{messages.length}</code>
        </span>
      </div>

      <style>{`
        @keyframes turChatBlink { 0%, 80%, 100% { opacity: 0.2; } 40% { opacity: 1; } }
      `}</style>
    </div>
  );
}

/** Three-dot typing indicator shown while the assistant bubble is empty. */
function TypingDots({ color }: { color: string }) {
  return (
    <span style={{ display: "inline-flex", gap: "4px", alignItems: "center", padding: "2px 0" }}>
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          style={{
            width: 6,
            height: 6,
            borderRadius: "50%",
            background: color,
            display: "inline-block",
            animation: "turChatBlink 1.2s infinite both",
            animationDelay: `${i * 0.16}s`,
          }}
        />
      ))}
    </span>
  );
}

const meta: Meta<typeof BestiaryLibrarianDemo> = {
  title: "AI & Chat/useTuringChat",
  component: BestiaryLibrarianDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Conversational RAG hook: streams a grounded LLM answer token-by-token over SSE and surfaces the sources it used. This demo is fully self-contained — the real hook, `TuringProvider`, and the network are NOT used. The 'Bestiary Librarian' fake-streams scripted answers and attaches citation chips drawn from the Mythical-Creatures fixtures. Send a suggested question, type your own, or hit Stop mid-stream.",
      },
    },
  },
  argTypes: {
    theme: {
      description: "Light or dark surface for the chat panel.",
      control: { type: "inline-radio" },
      options: ["light", "dark"],
    },
    streamSpeedMs: {
      description: "Delay between fake SSE tokens (ms). Lower = faster streaming.",
      control: { type: "range", min: 20, max: 200, step: 10 },
    },
  },
};

export default meta;
type Story = StoryObj<typeof BestiaryLibrarianDemo>;

export const Default: Story = {
  name: "🐉 Bestiary Librarian (streaming RAG)",
  args: { theme: "light", streamSpeedMs: 60 },
};

export const DarkMode: Story = {
  name: "🌙 Dark mode",
  args: { theme: "dark", streamSpeedMs: 60 },
};

export const FastStream: Story = {
  name: "⚡ Fast stream (snappier tokens)",
  args: { theme: "light", streamSpeedMs: 25 },
};
