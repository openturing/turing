import { useEffect, useRef, useState } from "react";
import { ArrowUp, Sparkles } from "lucide-react";
import { createChatController, type ChatController } from "@viglet/turing-sdk";
import { useTuringDemo } from "@/lib/turing-demo";
import { cn } from "@/lib/utils";

// Same wiring as the search demo (see @/lib/turing-demo). When a live instance is
// configured the chat streams REAL grounded answers via @viglet/turing-sdk;
// otherwise it plays a short scripted exchange so the widget never looks broken
// offline. The SN site is pinned via VITE_TURING_DEMO_SITE or auto-discovered.

interface DemoMsg {
  role: "user" | "assistant";
  content: string;
  sources?: { title: string }[];
}

const GREETING = "Hi! I'm the Turing assistant — ask me about the platform.";
const SUGGESTIONS = [
  "Does Turing support self-hosting?",
  "Which LLM providers work?",
  "What can the agents do?",
];

// Scripted fallback answers (keyword-matched) for the offline demo.
const SCRIPT: { match: RegExp; answer: string; sources: string[] }[] = [
  {
    match: /self.?host|host|infra|data/i,
    answer:
      "Yes — Turing is open source (Apache 2.0) and runs entirely on your own infrastructure. Your content and embeddings never leave your network.",
    sources: ["Self-hosting guide", "Security & data residency"],
  },
  {
    match: /llm|model|openai|anthropic|gemini|ollama|provider/i,
    answer:
      "Turing is provider-agnostic: OpenAI, Anthropic Claude, Google Gemini, Azure OpenAI and local Ollama models all plug in — pick one per agent.",
    sources: ["LLM providers"],
  },
  {
    match: /agent|tool|mcp|skill/i,
    answer:
      "Agents call tools, run Anthropic-standard skills in a Docker sandbox, and reach MCP servers (with cross-vendor federation) — all configurable per agent.",
    sources: ["AI agents", "MCP server"],
  },
];
const FALLBACK = {
  answer:
    "Turing unifies enterprise search, RAG and AI agents in one self-hosted platform. Ask about self-hosting, LLM providers, or agents & tools.",
  sources: ["Platform overview"],
};

export function ChatDemo() {
  const { client, site, live } = useTuringDemo();
  const [convo, setConvo] = useState<DemoMsg[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const controllerRef = useRef<ChatController | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  // Live mode: drive the widget from the SDK chat controller. Re-runs once the
  // site resolves (pinned or auto-discovered) so the controller binds to it.
  useEffect(() => {
    if (!client || !site) return;
    const c = createChatController(client, { site });
    controllerRef.current = c;
    const unsub = c.subscribe((s) => {
      setConvo(
        s.messages.map((m) => ({
          role: m.role === "user" ? "user" : "assistant",
          content: m.content,
          sources: m.sources?.map((src) => ({ title: src.title })),
        }))
      );
      setBusy(s.status === "loading");
    });
    return () => {
      controllerRef.current = null;
      if (typeof unsub === "function") unsub();
    };
  }, [client, site]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
  }, [convo, busy]);

  function scriptedSend(text: string) {
    setConvo((c) => [...c, { role: "user", content: text }]);
    const hit = SCRIPT.find((s) => s.match.test(text)) ?? FALLBACK;
    setBusy(true);
    // Reveal the answer progressively for a streaming feel.
    setConvo((c) => [...c, { role: "assistant", content: "" }]);
    const words = hit.answer.split(" ");
    let i = 0;
    const timer = window.setInterval(() => {
      i += 2;
      const partial = words.slice(0, i).join(" ");
      setConvo((c) => {
        const next = [...c];
        next[next.length - 1] = {
          role: "assistant",
          content: partial,
          sources: i >= words.length ? hit.sources.map((title) => ({ title })) : undefined,
        };
        return next;
      });
      if (i >= words.length) {
        window.clearInterval(timer);
        setBusy(false);
      }
    }, 60);
  }

  function send(text: string) {
    const t = text.trim();
    if (!t || busy) return;
    setInput("");
    if (controllerRef.current) {
      void controllerRef.current.send(t);
    } else {
      scriptedSend(t);
    }
  }

  const showSuggestions = convo.length === 0;

  return (
    <div className="flex h-[420px] flex-col overflow-hidden rounded-2xl border border-border bg-card text-card-foreground shadow-xl">
      <div className="flex items-center gap-2 border-b border-border px-4 py-3">
        <span className="flex size-6 items-center justify-center rounded-md bg-gradient-to-br from-[#1a3a9e] to-[#4f46e5] text-[#FFDEAD]">
          <Sparkles className="size-3.5" />
        </span>
        <span className="text-sm font-semibold">Turing assistant</span>
        <span className="ml-auto rounded border border-border px-1.5 py-0.5 font-mono text-[0.65rem] text-muted-foreground">
          {live ? "live" : "demo"}
        </span>
      </div>

      <div ref={scrollRef} className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
        <Bubble role="assistant" content={GREETING} />
        {convo.map((m, i) => (
          <Bubble key={i} role={m.role} content={m.content} sources={m.sources} />
        ))}
        {busy && convo[convo.length - 1]?.role !== "assistant" && (
          <Bubble role="assistant" content="…" />
        )}
        {showSuggestions && (
          <div className="flex flex-wrap gap-1.5 pt-1">
            {SUGGESTIONS.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => send(s)}
                className="rounded-full border border-border bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:border-primary/50 hover:text-foreground"
              >
                {s}
              </button>
            ))}
          </div>
        )}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          send(input);
        }}
        className="flex items-center gap-2 border-t border-border px-3 py-2.5"
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Ask about Turing…"
          className="w-full bg-transparent px-1 text-sm outline-none placeholder:text-muted-foreground"
          aria-label="Ask the assistant"
        />
        <button
          type="submit"
          disabled={!input.trim() || busy}
          aria-label="Send"
          className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground transition-opacity disabled:opacity-40"
        >
          <ArrowUp className="size-4" />
        </button>
      </form>
    </div>
  );
}

function Bubble({
  role,
  content,
  sources,
}: {
  role: "user" | "assistant";
  content: string;
  sources?: { title: string }[];
}) {
  const isUser = role === "user";
  return (
    <div className={cn("flex", isUser ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "max-w-[85%] rounded-2xl px-3.5 py-2 text-sm leading-relaxed",
          isUser
            ? "bg-primary text-primary-foreground"
            : "bg-muted text-foreground"
        )}
      >
        {content}
        {sources && sources.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1">
            {sources.map((s) => (
              <span
                key={s.title}
                className="rounded bg-background/60 px-1.5 py-0.5 text-[0.65rem] font-medium text-muted-foreground"
              >
                {s.title}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
