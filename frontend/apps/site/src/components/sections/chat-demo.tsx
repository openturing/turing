import { useEffect, useRef, useState } from "react";
import { ArrowUp, Check, Copy, FileText, Sparkles } from "lucide-react";
import {
  createChatController,
  createSimilarController,
  type ChatController,
  type SimilarController,
  type SimilarStatus,
  type TurChatSource,
  type TurSimilarResult,
} from "@viglet/turing-sdk";
import {
  TuringCopyButton,
  TuringPersonaPicker,
  TuringSourceChips,
  TuringThinkingDots,
} from "@viglet/turing-react-ui";
import { RichAnswer } from "@/components/rich-answer";
import { PLAYGROUND_PERSONAS } from "@/lib/site-content";
import { useTuringDemo } from "@/lib/turing-demo";
import { cn } from "@/lib/utils";

// Dogfooding: the assistant streams REAL grounded answers via @viglet/turing-sdk
// and renders them with the product's own @viglet/turing-react-ui components —
// RichAnswer (markdown / ```html / ```d2), TuringSourceChips (auditable RAG
// citations with a "why did you say this?" trace), TuringThinkingDots and
// TuringCopyButton. Offline it plays a short scripted exchange so the widget
// never looks broken. The SN site is pinned to `turing-docs` (T483 dogfood seed).

interface DemoMsg {
  role: "user" | "assistant";
  content: string;
  sources?: TurChatSource[];
}

const DEFAULT_GREETING =
  "Hi! I'm the Turing assistant — ask me anything about the platform.";
// T638 — the "default voice" option has no entry in PLAYGROUND_PERSONAS, so give
// it its own one-line hint for the active-persona caption below the picker.
const DEFAULT_PERSONA_HINT = "The agent's own voice — no persona applied.";
const DEFAULT_SUGGESTIONS = [
  "Does Turing support self-hosting?",
  "Which LLM providers work?",
  "What can the agents do?",
];

/** A scripted, keyword-matched exchange for the offline (sample-data) fallback. */
export interface ChatScriptEntry {
  match: RegExp;
  answer: string;
  sources: string[];
}

// Scripted fallback answers (keyword-matched) for the offline demo.
const DEFAULT_SCRIPT: ChatScriptEntry[] = [
  {
    match: /self.?host|host|infra|data/i,
    answer:
      "Yes — Turing is open source (Apache 2.0) and runs entirely on your own infrastructure. Your content and embeddings never leave your network.\n\n```bash\ndocker compose up -d --wait\n```",
    sources: ["Self-hosting guide", "Security & data residency"],
  },
  {
    match: /llm|model|openai|anthropic|gemini|ollama|provider/i,
    answer:
      "Turing is provider-agnostic: **OpenAI**, **Anthropic Claude**, **Google Gemini**, Azure OpenAI and local **Ollama** models all plug in — pick one per agent.",
    sources: ["LLM providers"],
  },
  {
    match: /agent|tool|mcp|skill/i,
    answer:
      "Agents call tools, run Anthropic-standard skills in a Docker sandbox, and reach MCP servers (with cross-vendor federation) — all configurable per agent.",
    sources: ["AI agents", "MCP server"],
  },
];
const DEFAULT_FALLBACK = {
  answer:
    "Turing unifies enterprise search, RAG and AI agents in one self-hosted platform. Ask about self-hosting, LLM providers, or agents & tools.",
  sources: ["Platform overview"],
};

function scriptedSources(titles: string[]): TurChatSource[] {
  return titles.map((title, i) => ({ sourceId: `s${i}`, title }));
}

// T707 — the "not in this site" refusals the demo should never surface as a
// dead-end. Covers (a) the canonical deterministic refusals the backend emits
// per locale (en/pt/es/ca) + the "LLM not enabled" guard, AND (b) the softer
// LLM-phrased "the content doesn't cover X" refusals a model produces when a
// thin page (e.g. a nav/landing hub with no body) is retrieved but has nothing
// to answer. Matching any of these rewrites the turn to the scripted fallback so
// the demo never dead-ends (its "never looks broken" contract) — regardless of
// whether a (thin) source was attached.
const REFUSAL_RE =
  /not available in the website|não está disponível na base|no está disponible en la base|no està disponible a la base|not enabled for this site|does not (?:provide|contain) any (?:information|details)|no (?:information|details) (?:about|on|regarding)|couldn'?t find|could not find|não (?:fornece|contém) (?:nenhuma|)\s*informaç|nenhuma informação|no (?:proporciona|contiene) (?:ninguna|)\s*informaci/i;

/** Picks the scripted answer for a user message (same matching as the offline path). */
function scriptedAnswerFor(
  userText: string,
  script: ChatScriptEntry[],
  fallback: { answer: string; sources: string[] },
): { answer: string; sources: TurChatSource[] } {
  const hit = script.find((s) => s.match.test(userText)) ?? fallback;
  return { answer: hit.answer, sources: scriptedSources(hit.sources) };
}

/**
 * T707 — post-processes a completed LIVE conversation: any sourceless refusal
 * assistant turn is rewritten to the scripted answer for its preceding user
 * message. Pure + idempotent, so it can run on every store emission. Skipped
 * while streaming (a partial answer may transiently look empty).
 */
function withScriptedFallback(
  msgs: DemoMsg[],
  streaming: boolean,
  script: ChatScriptEntry[],
  fallback: { answer: string; sources: string[] },
): DemoMsg[] {
  if (streaming) return msgs;
  return msgs.map((m, i) => {
    // A refusal is a dead-end whether or not a (thin) source was attached — a
    // nav/landing page with no body is often retrieved as a source yet answers
    // nothing. So match on the refusal text alone, not on sourcelessness.
    if (m.role !== "assistant" || !REFUSAL_RE.test(m.content)) return m;
    for (let j = i - 1; j >= 0; j--) {
      if (msgs[j].role === "user") {
        const sc = scriptedAnswerFor(msgs[j].content, script, fallback);
        return { role: "assistant" as const, content: sc.answer, sources: sc.sources };
      }
    }
    return m;
  });
}

export interface ChatDemoProps {
  /** Drop the standalone card chrome + header so a parent (the playground) frames it. */
  bare?: boolean;
  /**
   * When `nonce` changes, the given text is sent as a user message (search→ask
   * bridge). `id` (when known) is the seed document's id: if that ask comes back
   * as a refusal, the chat pivots to that document's related pages instead of
   * dead-ending.
   */
  seed?: { text: string; nonce: number; id?: string };
  /** Pin a specific demo SN site (else the env pin / first discovered). */
  siteOverride?: string;
  /** Opening assistant line (per-page flavor). */
  greeting?: string;
  /** Starter prompt chips (per-page flavor). */
  suggestions?: string[];
  /** Offline scripted answers + fallback (per-page flavor). */
  script?: ChatScriptEntry[];
  fallback?: { answer: string; sources: string[] };
  /**
   * Show the "answer as persona" picker. The seeded personas describe evaluating
   * the Turing platform, so pages over other corpora (e.g. WKND) hide it.
   */
  showPersonas?: boolean;
}

export function ChatDemo({
  bare = false,
  seed,
  siteOverride,
  greeting = DEFAULT_GREETING,
  suggestions = DEFAULT_SUGGESTIONS,
  script = DEFAULT_SCRIPT,
  fallback = DEFAULT_FALLBACK,
  showPersonas = true,
}: Readonly<ChatDemoProps>) {
  const { client, site, live, analytics } = useTuringDemo(siteOverride);
  const [convo, setConvo] = useState<DemoMsg[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  // T638 — per-request persona ("same question, different eyes"). null = the
  // agent's default voice. Changing it starts a fresh conversation so the
  // contrast is clean.
  const [personaId, setPersonaId] = useState<string | null>(null);
  const controllerRef = useRef<ChatController | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const busyRef = useRef(false);
  const seedNonce = useRef<number | null>(null);
  // T707 — mirror the (per-render, new-identity) script/fallback props into refs
  // so the subscribe effect can read the latest values without re-subscribing
  // (and re-creating the controller) on every render.
  const scriptRef = useRef(script);
  scriptRef.current = script;
  const fallbackRef = useRef(fallback);
  fallbackRef.current = fallback;

  // Rich pivot: when a seeded "Ask about this page" comes back as a refusal
  // (the page had no answerable body), surface that page's RELATED documents
  // instead of dead-ending. Reuses the same /search/similar controller the
  // search panel uses (T485).
  const [related, setRelated] = useState<{ status: SimilarStatus; results: TurSimilarResult[] }>({
    status: "idle",
    results: [],
  });
  const similarRef = useRef<SimilarController | null>(null);
  const seedIdRef = useRef<string | null>(null);
  // Dedupe the pivot per seed nonce so it fires once per seeded ask.
  const pivotRef = useRef<{ nonce: number; done: boolean } | null>(null);

  // Live mode: drive the widget from the SDK chat controller. Re-runs once the
  // site resolves (pinned or auto-discovered) so the controller binds to it.
  useEffect(() => {
    if (!client || !site) return;
    const c = createChatController(client, {
      site,
      personaId: personaId ?? undefined,
      // T458/T460 — route chat lifecycle (start, funnel steps, conversion) and
      // client-side abandonment into GA4 via the shared bus. Additive: a failed
      // emit never breaks the chat.
      analytics,
      abandonment: true,
    });
    controllerRef.current = c;

    const similar = createSimilarController(client, site, { rows: 5, locale: "en_US" });
    similarRef.current = similar;
    const unsubSimilar = similar.subscribe((s) =>
      setRelated({ status: s.status, results: s.results })
    );

    const unsub = c.subscribe((s) => {
      const loading = s.status === "loading";
      const mapped: DemoMsg[] = s.messages.map((m) => ({
        role: m.role === "user" ? "user" : "assistant",
        content: m.content,
        sources: m.sources,
      }));
      // Rich pivot: a seeded page ask that came back as a refusal → load that
      // page's related docs (once per seed nonce). Detect on the RAW message
      // (before withScriptedFallback rewrites it to the generic fallback).
      const last = mapped[mapped.length - 1];
      if (
        !loading &&
        last?.role === "assistant" &&
        REFUSAL_RE.test(last.content) &&
        seedIdRef.current &&
        pivotRef.current &&
        !pivotRef.current.done
      ) {
        pivotRef.current.done = true;
        setRelated({ status: "loading", results: [] });
        void similarRef.current?.load(seedIdRef.current);
      }
      setConvo(withScriptedFallback(mapped, loading, scriptRef.current, fallbackRef.current));
      setBusy(loading);
    });
    return () => {
      controllerRef.current = null;
      similarRef.current = null;
      if (typeof unsub === "function") unsub();
      if (typeof unsubSimilar === "function") unsubSimilar();
    };
  }, [client, site, personaId, analytics]);

  useEffect(() => {
    busyRef.current = busy;
  }, [busy]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
  }, [convo, busy]);

  function scriptedSend(text: string) {
    setConvo((c) => [...c, { role: "user", content: text }]);
    const hit = script.find((s) => s.match.test(text)) ?? fallback;
    setBusy(true);
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
          sources: i >= words.length ? scriptedSources(hit.sources) : undefined,
        };
        return next;
      });
      if (i >= words.length) {
        window.clearInterval(timer);
        setBusy(false);
      }
    }, 60);
  }

  function send(text: string, seeded = false) {
    const t = text.trim();
    if (!t || busyRef.current) return;
    setInput("");
    // An organic (typed) question is a fresh topic — drop any armed/loaded pivot
    // so the previous page's related list doesn't linger. The seeded path arms
    // its own pivot just before calling this, so it opts out of the reset.
    if (!seeded) {
      seedIdRef.current = null;
      pivotRef.current = null;
      similarRef.current?.clear();
      setRelated({ status: "idle", results: [] });
    }
    if (controllerRef.current) {
      void controllerRef.current.send(t);
    } else {
      scriptedSend(t);
    }
  }

  // Search → Ask bridge: when the parent bumps `seed.nonce`, send that prompt.
  useEffect(() => {
    if (!seed || seed.nonce === seedNonce.current) return;
    seedNonce.current = seed.nonce;
    // Arm the rich pivot for this seeded ask: remember the seed page id and
    // clear any prior related list. If the ask refuses, the subscribe handler
    // loads this id's related docs.
    seedIdRef.current = seed.id ?? null;
    pivotRef.current = { nonce: seed.nonce, done: false };
    similarRef.current?.clear();
    setRelated({ status: "idle", results: [] });
    send(seed.text, true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seed?.nonce]);

  const showSuggestions = convo.length === 0;
  // T638 — one-line hint for the persona currently answering, shown under the
  // picker so visitors know what each "eye" means without hunting for a tooltip.
  const activePersonaHint =
    personaId == null
      ? DEFAULT_PERSONA_HINT
      : PLAYGROUND_PERSONAS.find((p) => p.id === personaId)?.description ?? DEFAULT_PERSONA_HINT;

  return (
    <div
      className={cn(
        "flex flex-col overflow-hidden bg-card text-card-foreground",
        bare
          ? "h-full"
          : "h-[420px] rounded-2xl border border-border shadow-xl"
      )}
    >
      {!bare && (
        <div className="flex items-center gap-2 border-b border-border px-4 py-3">
          <span className="flex size-6 items-center justify-center rounded-md bg-gradient-to-br from-[#1a3a9e] to-[#4f46e5] text-[#FFDEAD]">
            <Sparkles className="size-3.5" />
          </span>
          <span className="text-sm font-semibold">Turing assistant</span>
          <span className="ml-auto rounded border border-border px-1.5 py-0.5 font-mono text-[0.65rem] text-muted-foreground">
            {live ? "live" : "demo"}
          </span>
        </div>
      )}

      {/* T638 — persona intro + picker + active-persona caption. The one-line
          concept sits above the pills; the selected persona's description sits
          below so its meaning is always visible (no tooltip hunt, mobile-safe). */}
      {showPersonas && (
      <div className="border-b border-border px-4 py-2">
        <p className="mb-1.5 text-[0.7rem] leading-snug text-muted-foreground/80">
          Hear the same answer through different eyes — pick a persona to answer as.
        </p>
        <TuringPersonaPicker
          options={PLAYGROUND_PERSONAS}
          value={personaId}
          onChange={setPersonaId}
          includeDefault
          labels={{ heading: "Answer as", defaultOption: "Default" }}
          classNames={{
            container: "flex flex-wrap items-center gap-1.5",
            label: "text-[0.65rem] font-bold uppercase tracking-wide text-muted-foreground/70",
            list: "flex flex-wrap gap-1",
            option:
              "rounded-full border border-border bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground transition-colors hover:border-primary/50 hover:text-foreground data-[active]:border-primary data-[active]:bg-primary/10 data-[active]:text-foreground",
            optionActive: "border-primary bg-primary/10 text-foreground",
          }}
        />
        <p className="mt-1.5 text-xs leading-snug text-muted-foreground">
          {activePersonaHint}
        </p>
      </div>
      )}

      <div ref={scrollRef} className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
        <Bubble role="assistant" content={greeting} />
        {convo.map((m, i) => (
          // The SDK pre-commits an EMPTY assistant bubble the moment a turn
          // starts (chat controller: "so a UI can render a typing animation
          // immediately"). Render the thinking dots inside that bubble while it
          // waits for the first token — otherwise it shows as an empty chip.
          <Bubble
            key={i}
            role={m.role}
            content={m.content}
            sources={m.sources}
            pending={m.role === "assistant" && m.content.length === 0 && busy}
          />
        ))}
        {/* Rich pivot — a seeded page ask that refused surfaces the page's
            related docs here (reusing /search/similar), so it never dead-ends. */}
        {related.status === "loading" && (
          <div className="flex justify-start">
            <div className="rounded-2xl bg-muted px-3.5 py-2 text-xs text-muted-foreground">
              Finding related pages…
            </div>
          </div>
        )}
        {related.results.length > 0 && (
          <div className="flex justify-start">
            <div className="max-w-[85%] rounded-2xl border border-primary/20 bg-primary/5 px-3.5 py-2.5">
              <div className="mb-1.5 inline-flex items-center gap-1.5 text-xs font-semibold text-primary">
                <Sparkles className="size-3.5" />
                Related pages
              </div>
              <ul className="flex flex-col gap-1">
                {related.results.map((r) => (
                  <li key={r.id}>
                    <a
                      href={r.url || undefined}
                      target="_blank"
                      rel="noreferrer"
                      className="flex items-center gap-2 rounded px-1.5 py-1 text-sm text-foreground hover:bg-muted"
                    >
                      <Sparkles className="size-3 shrink-0 text-primary/60" />
                      <span className="truncate">{r.title}</span>
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}
        {busy && convo[convo.length - 1]?.role !== "assistant" && (
          <div className="flex justify-start">
            <div className="rounded-2xl bg-muted px-3.5 py-2.5">
              <TuringThinkingDots
                className="inline-flex items-center gap-1 text-muted-foreground"
                classNames={{ dot: "inline-block size-1.5 animate-bounce rounded-full bg-current" }}
              />
            </div>
          </div>
        )}
        {showSuggestions && (
          <div className="flex flex-wrap gap-1.5 pt-1">
            {suggestions.map((s) => (
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
  pending = false,
}: Readonly<{
  role: "user" | "assistant";
  content: string;
  sources?: TurChatSource[];
  /** Assistant bubble awaiting its first token — show the typing indicator. */
  pending?: boolean;
}>) {
  const isUser = role === "user";
  return (
    <div className={cn("group flex", isUser ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "max-w-[85%] rounded-2xl px-3.5 py-2 text-sm leading-relaxed",
          isUser ? "bg-primary text-primary-foreground" : "bg-muted text-foreground"
        )}
      >
        {pending ? (
          <TuringThinkingDots
            className="inline-flex items-center gap-1 text-muted-foreground"
            classNames={{ dot: "inline-block size-1.5 animate-bounce rounded-full bg-current" }}
          />
        ) : isUser ? (
          content
        ) : (
          <RichAnswer content={content} />
        )}

        {!isUser && content && (
          <div className="mt-1 flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
            <TuringCopyButton
              value={content}
              className="rounded p-1 text-muted-foreground/60 transition-colors hover:text-foreground"
              icons={{ copy: <Copy className="size-3.5" />, copied: <Check className="size-3.5 text-emerald-500" /> }}
            />
          </div>
        )}

        {sources && sources.length > 0 && (
          <TuringSourceChips
            sources={sources}
            labels={{ heading: "Sources", why: "Why this answer?", open: "Open" }}
            icons={{ source: <FileText className="size-3 shrink-0" /> }}
            classNames={{
              container: "mt-2 border-t border-border/60 pt-2",
              heading:
                "mb-1 text-[0.6rem] font-bold uppercase tracking-wide text-muted-foreground/70",
              list: "flex flex-wrap gap-1",
              chip: "inline-flex items-center gap-1 rounded bg-background/60 px-1.5 py-0.5 text-[0.65rem] font-medium text-muted-foreground transition-colors hover:text-foreground",
              chipExpanded: "ring-1 ring-primary/40",
              confidence:
                "ml-0.5 inline-block size-1.5 rounded-full data-[confidence=high]:bg-emerald-500 data-[confidence=medium]:bg-amber-500 data-[confidence=low]:bg-slate-400",
              panel: "mt-1 w-full space-y-1 rounded bg-background/50 p-2 text-[0.65rem]",
              panelCaption: "font-semibold text-muted-foreground",
              chunk: "flex items-center justify-between gap-2",
              chunkMeta: "font-mono text-muted-foreground/70",
              link: "text-primary underline",
            }}
          />
        )}
      </div>
    </div>
  );
}
