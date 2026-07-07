import type { Meta, StoryObj } from "@storybook/react-vite";
import { useMemo, useState } from "react";

/**
 * # useTuringUserMemory
 *
 * Cross-conversation **personal memory** for a chat agent. The hook owns a
 * stable per-user id and wires two backend-advertised client tools so the
 * agent can _recall_ what it knows about the user and _remember_ new facts —
 * preferences, constraints, tastes — that survive across conversations. A
 * "Vinyl Records" collector assistant, for example, can remember that you
 * **prefer 180g pressings**, keep a **$40 budget**, and **love bebop**, then
 * lean on those facts the next time you ask for a recommendation.
 *
 * The same hook exposes the remembered list for a "what the assistant knows
 * about me" panel, including per-fact delete and a GDPR "forget me" clear.
 * Because the SDK owns the `userId`, the server never threads it through the
 * chat path.
 *
 * ## Key Features
 * - `clientTools`: spread into `useTuringChat` — backs the agent's
 *   `recall_user_memory` and `remember_fact` tools
 * - `memories`: the user's remembered facts (`{ id, userId, key, content, updatedAt }`)
 *   for a transparency panel
 * - `refresh()`: re-fetch the list from the server
 * - `remove(id)`: delete one remembered fact
 * - `clear()`: delete every fact for this user (GDPR "forget me")
 * - SDK-owned `userId` — kept out of the chat request entirely
 *
 * ## Usage
 * ```tsx
 * const memory = useTuringUserMemory({ userId: "collector@example.com" });
 *
 * // 1. Let the agent recall + remember across conversations
 * const chat = useTuringChat({
 *   agent: "vinyl-collector",
 *   clientTools: { ...memory.clientTools },
 * });
 *
 * // 2. Render a "what we remember about you" panel
 * memory.memories.map((m) => (
 *   <li key={m.id}>
 *     <strong>{m.key}</strong>: {m.content}
 *     <button onClick={() => memory.remove(m.id)}>Forget</button>
 *   </li>
 * ));
 *
 * // 3. GDPR "forget me"
 * <button onClick={() => memory.clear()}>Forget everything</button>
 * ```
 *
 * ## When to use
 * - **Personalized assistants**: a recommender that should not re-ask the same
 *   preference questions every session (budget, format, favorite genres)
 * - **Long-lived relationships**: support / concierge bots that span many
 *   conversations for the same logged-in user
 * - **Transparency & compliance**: surface a panel so users can see, edit, and
 *   delete the facts an agent has stored about them
 *
 * ## About this story
 * This story is a **fully self-contained simulation** — no real hook, provider,
 * or network. It models the memory store in local React state so you can add,
 * edit, and forget facts, and watch a sample assistant reply change
 * **before/after** based on what is currently remembered. The "agent reply" is
 * a deterministic template that reads the current memory — it illustrates how
 * recalled facts shape an answer, not a real LLM call.
 */

interface MemoryItem {
  id: string;
  key: string;
  content: string;
  updatedAt: number;
}

interface MemoryDemoProps {
  /** Seed the store with the classic collector preferences. */
  seeded: boolean;
  /** Render the dark "turntable" variant. */
  dark: boolean;
}

const SEED: ReadonlyArray<Omit<MemoryItem, "id" | "updatedAt">> = [
  { key: "format", content: "prefers 180g vinyl pressings" },
  { key: "budget", content: "budget around $40 per record" },
  { key: "genre", content: "loves bebop and hard bop" },
];

let nextId = 100;
function makeItem(key: string, content: string): MemoryItem {
  return { id: `mem-${nextId++}`, key, content, updatedAt: Date.now() };
}

/**
 * Deterministic "assistant" — reads the current memory and composes a
 * recommendation. This stands in for an LLM call so the story can show how
 * recalled facts change the answer.
 */
function composeReply(memories: MemoryItem[]): { text: string; grounded: string[] } {
  const byKey = new Map(memories.map((m) => [m.key, m.content]));
  const grounded: string[] = [];

  if (memories.length === 0) {
    return {
      text:
        "I don't know your tastes yet. What genres do you collect, what's your budget, and do you care about pressing weight?",
      grounded,
    };
  }

  const parts: string[] = [];
  const genre = byKey.get("genre");
  const format = byKey.get("format");
  const budget = byKey.get("budget");

  if (genre) {
    parts.push(`Since you ${genre}, try Dexter Gordon's "Go!" — a bebop staple.`);
    grounded.push("genre");
  } else {
    parts.push("Here's a well-reviewed jazz reissue.");
  }
  if (format) {
    parts.push("I picked the 180g reissue to match your pressing preference.");
    grounded.push("format");
  }
  if (budget) {
    parts.push("It's $34, comfortably under your budget.");
    grounded.push("budget");
  }

  // Any extra custom facts the user added beyond the canonical three.
  for (const m of memories) {
    if (!["genre", "format", "budget"].includes(m.key)) {
      parts.push(`I'll also keep in mind that you ${m.content}.`);
      grounded.push(m.key);
    }
  }

  return { text: parts.join(" "), grounded };
}

function UserMemoryDemo({ seeded, dark }: MemoryDemoProps) {
  const [memories, setMemories] = useState<MemoryItem[]>(() =>
    seeded ? SEED.map((s) => makeItem(s.key, s.content)) : [],
  );
  const [draftKey, setDraftKey] = useState("");
  const [draftContent, setDraftContent] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);

  const reply = useMemo(() => composeReply(memories), [memories]);

  function rememberFact(key: string, content: string) {
    const k = key.trim();
    const c = content.trim();
    if (!k || !c) return;
    setMemories((prev) => {
      const existing = prev.find((m) => m.key === k);
      if (existing) {
        return prev.map((m) =>
          m.key === k ? { ...m, content: c, updatedAt: Date.now() } : m,
        );
      }
      return [...prev, makeItem(k, c)];
    });
    setDraftKey("");
    setDraftContent("");
    setEditingId(null);
  }

  function forget(id: string) {
    setMemories((prev) => prev.filter((m) => m.id !== id));
  }

  function clearAll() {
    setMemories([]);
  }

  function startEdit(item: MemoryItem) {
    setEditingId(item.id);
    setDraftKey(item.key);
    setDraftContent(item.content);
  }

  // Theme tokens.
  const t = dark
    ? {
        page: "#0a0a0f",
        card: "#16161f",
        border: "#1e1e2e",
        text: "#e2e8f0",
        subtle: "#94a3b8",
        chipBg: "#1e293b",
        chipText: "#cbd5e1",
        inputBg: "#0f0f17",
      }
    : {
        page: "#ffffff",
        card: "#ffffff",
        border: "#e2e8f0",
        text: "#0f172a",
        subtle: "#64748b",
        chipBg: "#eef2ff",
        chipText: "#4338ca",
        inputBg: "#ffffff",
      };

  const gradient = "linear-gradient(135deg, #2563eb, #4f46e5)";

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        maxWidth: "560px",
        background: t.page,
        color: t.text,
        padding: dark ? "18px" : 0,
        borderRadius: "16px",
      }}
    >
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "14px" }}>
        <div
          style={{
            width: 40,
            height: 40,
            borderRadius: "50%",
            background: gradient,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: "20px",
          }}
        >
          🎵
        </div>
        <div>
          <div style={{ fontWeight: 700, fontSize: "15px" }}>Vinyl Collector Assistant</div>
          <div style={{ fontSize: "12px", color: t.subtle }}>
            🧠 remembers your tastes across conversations
          </div>
        </div>
      </div>

      {/* Memory panel */}
      <div style={{ border: `1px solid ${t.border}`, borderRadius: "12px", overflow: "hidden", marginBottom: "14px", background: t.card }}>
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            padding: "10px 14px",
            borderBottom: `1px solid ${t.border}`,
            background: dark ? "#0f0f17" : "#f8fafc",
          }}
        >
          <span style={{ fontSize: "12px", fontWeight: 600, color: t.subtle, textTransform: "uppercase", letterSpacing: "0.05em" }}>
            🧠 What I remember about you ({memories.length})
          </span>
          {memories.length > 0 && (
            <button
              type="button"
              onClick={clearAll}
              style={{ fontSize: "12px", color: "#ef4444", background: "none", border: "none", cursor: "pointer" }}
              title="GDPR — forget everything (clear())"
            >
              Forget all
            </button>
          )}
        </div>

        {memories.length === 0 ? (
          <div style={{ padding: "24px", textAlign: "center", color: t.subtle, fontSize: "13px" }}>
            Nothing remembered yet — add a fact below.
          </div>
        ) : (
          memories.map((m) => (
            <div
              key={m.id}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                padding: "10px 14px",
                borderBottom: `1px solid ${t.border}`,
              }}
            >
              <code
                style={{
                  fontSize: "11px",
                  padding: "2px 8px",
                  borderRadius: "999px",
                  background: t.chipBg,
                  color: t.chipText,
                  fontWeight: 600,
                }}
              >
                {m.key}
              </code>
              <span style={{ flex: 1, fontSize: "14px" }}>{m.content}</span>
              <button
                type="button"
                onClick={() => startEdit(m)}
                style={{ border: "none", background: "transparent", cursor: "pointer", color: t.subtle, fontSize: "13px" }}
                title="Edit (remember_fact upsert)"
              >
                ✏️
              </button>
              <button
                type="button"
                onClick={() => forget(m.id)}
                style={{ border: "none", background: "transparent", cursor: "pointer", color: "#94a3b8", fontSize: "14px" }}
                title="Forget this fact (remove(id))"
              >
                ✕
              </button>
            </div>
          ))
        )}

        {/* Add / edit fact */}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            rememberFact(draftKey, draftContent);
          }}
          style={{ display: "flex", gap: "8px", padding: "12px 14px", background: dark ? "#0f0f17" : "#f8fafc" }}
        >
          <input
            type="text"
            value={draftKey}
            onChange={(e) => setDraftKey(e.target.value)}
            placeholder="key (e.g. genre)"
            style={{
              width: "120px",
              padding: "8px 10px",
              border: `1px solid ${t.border}`,
              borderRadius: "8px",
              fontSize: "13px",
              outline: "none",
              background: t.inputBg,
              color: t.text,
            }}
          />
          <input
            type="text"
            value={draftContent}
            onChange={(e) => setDraftContent(e.target.value)}
            placeholder="fact to remember…"
            style={{
              flex: 1,
              padding: "8px 10px",
              border: `1px solid ${t.border}`,
              borderRadius: "8px",
              fontSize: "13px",
              outline: "none",
              background: t.inputBg,
              color: t.text,
            }}
          />
          <button
            type="submit"
            style={{
              padding: "8px 14px",
              borderRadius: "8px",
              border: "none",
              background: gradient,
              color: "white",
              fontWeight: 600,
              cursor: "pointer",
              fontSize: "13px",
            }}
          >
            {editingId ? "Update" : "Remember"}
          </button>
        </form>
      </div>

      {/* Live assistant reply — shows how memory shapes the answer */}
      <div style={{ border: `1px solid ${t.border}`, borderRadius: "12px", overflow: "hidden", background: t.card }}>
        <div
          style={{
            padding: "10px 14px",
            borderBottom: `1px solid ${t.border}`,
            fontSize: "12px",
            fontWeight: 600,
            color: t.subtle,
            textTransform: "uppercase",
            letterSpacing: "0.05em",
            background: dark ? "#0f0f17" : "#f1f5f9",
          }}
        >
          Sample reply to “recommend me a record”
        </div>
        <div style={{ padding: "14px", display: "flex", gap: "10px" }}>
          <div style={{ fontSize: "20px" }}>🎵</div>
          <div style={{ flex: 1 }}>
            <p style={{ margin: 0, fontSize: "14px", lineHeight: 1.55 }}>{reply.text}</p>
            {reply.grounded.length > 0 && (
              <div style={{ marginTop: "10px", display: "flex", flexWrap: "wrap", gap: "6px", alignItems: "center" }}>
                <span style={{ fontSize: "11px", color: t.subtle }}>grounded in recalled memory:</span>
                {reply.grounded.map((g) => (
                  <code
                    key={g}
                    style={{
                      fontSize: "11px",
                      padding: "1px 7px",
                      borderRadius: "999px",
                      background: "#dcfce7",
                      color: "#166534",
                      fontWeight: 600,
                    }}
                  >
                    {g}
                  </code>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Didactic note */}
      <div
        style={{
          marginTop: "14px",
          padding: "10px 14px",
          background: dark ? "#0f172a" : "#eff6ff",
          borderRadius: "8px",
          fontSize: "12px",
          color: "#3b82f6",
          lineHeight: 1.5,
        }}
      >
        💡 Add, edit, or forget a fact and watch the reply above change. In the real
        SDK, the agent calls <code>recall_user_memory</code> to read these facts and{" "}
        <code>remember_fact</code> to upsert new ones — both wired by{" "}
        <code>useTuringUserMemory</code> and persisted server-side per <code>userId</code>.
      </div>
    </div>
  );
}

const meta: Meta<typeof UserMemoryDemo> = {
  title: "AI & Chat/useTuringUserMemory",
  component: UserMemoryDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Self-contained simulation of `useTuringUserMemory` — persistent per-user memory the agent reads/writes across conversations. Add, edit, and forget facts in the memory panel and watch a sample assistant reply change before/after based on what is currently remembered. No real hook, provider, or network is used; the 'agent reply' is a deterministic template that reads the current memory.",
      },
    },
  },
  argTypes: {
    seeded: {
      description: "Pre-populate the store with the classic collector preferences (180g, $40, bebop).",
      control: { type: "boolean" },
    },
    dark: {
      description: "Render the dark 'turntable' variant.",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof UserMemoryDemo>;

export const RemembersYourTastes: Story = {
  name: "🎵 Remembers your tastes",
  args: { seeded: true, dark: false },
};

export const FreshUserNoMemory: Story = {
  name: "🧠 Fresh user (nothing remembered yet)",
  args: { seeded: false, dark: false },
};

export const DarkTurntable: Story = {
  name: "🎵 Dark turntable variant",
  args: { seeded: true, dark: true },
};
