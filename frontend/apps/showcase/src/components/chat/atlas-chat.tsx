import { NativeForm } from "@/components/chat/native-form";
import { Button } from "@/components/ui/button";
import { useCart } from "@/contexts/cart";
import { createCartClientTools } from "@/lib/cart-tools";
import { MarkdownBlock, D2Block } from "@/lib/markdown";
import { cn } from "@/lib/utils";
import { getVisitorId } from "@/lib/visitor";
import {
  IconAlertTriangle,
  IconArrowUp,
  IconBrain,
  IconFileText,
  IconLoader2,
  IconMicrophone,
  IconPaperclip,
  IconRobot,
  IconSparkles,
  IconTool,
  IconTrash,
  IconUser,
  IconX,
} from "@tabler/icons-react";
import {
  TuringGenerativeContent,
  TuringRichContent,
  TuringSourceChips,
  TuringToolActivity,
  TuringWorkspacePanelView,
  useAnswerAsApp,
  useCoBrowseSearch,
  useSearchStore,
  useTuringChat,
  useTuringSlotUpload,
  useTuringSlots,
  useTuringUserMemory,
  useTuringVoice,
  useTuringWorkspace,
} from "@viglet/turing-react-sdk";
import { useEffect, useMemo, useRef, useState } from "react";

const SOURCE_CHIP_CLASSNAMES = {
  container: "not-prose mt-3 border-t border-border/60 pt-2",
  heading: "mb-1.5 text-[11px] font-medium uppercase tracking-wide text-muted-foreground/70",
  list: "flex flex-wrap gap-1.5",
  chip: "inline-flex max-w-full items-center gap-1.5 rounded-full border border-border bg-muted/40 px-2.5 py-1 text-xs text-foreground/80 transition hover:bg-muted",
  panel: "mt-1.5 w-full space-y-1 rounded-lg border border-border/60 bg-muted/20 p-2.5",
  link: "text-primary hover:underline shrink-0",
} as const;

/** Collected slots (T63) — shows the structured data captured this conversation. */
function SlotsStrip() {
  const { slots } = useTuringSlots();
  const entries = Object.entries(slots).filter(([, v]) => v);
  if (entries.length === 0) return null;
  return (
    <div className="border-t border-border/60 px-4 py-2">
      <p className="mb-1 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
        Collected
      </p>
      <div className="flex flex-wrap gap-1">
        {entries.map(([k, v]) => (
          <span
            key={k}
            className="rounded-full bg-muted px-2 py-0.5 text-[11px] text-muted-foreground"
          >
            <span className="font-medium text-foreground/80">{k}</span>: {v}
          </span>
        ))}
      </div>
    </div>
  );
}

/** Workspace artifacts (T113) the agent produced this conversation. */
function WorkspaceStrip() {
  const { artifacts, status } = useTuringWorkspace();
  if (artifacts.length === 0) return null;
  return (
    <div className="border-t border-border/60 px-4 py-2">
      <TuringWorkspacePanelView
        artifacts={artifacts}
        status={status}
        title="Workspace"
        className="text-sm"
      />
    </div>
  );
}

function MessageBubble({
  role,
  content,
  sources,
  options,
  toolCalls,
  isStreaming,
  onPickOption,
}: Readonly<{
  role: "user" | "assistant";
  content: string;
  sources?: unknown[];
  options?: string[];
  toolCalls?: unknown[];
  isStreaming: boolean;
  onPickOption: (label: string) => void;
}>) {
  const isAssistant = role === "assistant";
  return (
    <div className="flex gap-2.5">
      <div
        className={cn(
          "grid size-7 shrink-0 place-items-center rounded-full",
          isAssistant ? "bg-primary/15 text-primary" : "bg-muted text-muted-foreground"
        )}
      >
        {isAssistant ? <IconRobot className="size-4" /> : <IconUser className="size-4" />}
      </div>
      <div className="min-w-0 flex-1">
        {isAssistant ? (
          <div className="prose prose-sm dark:prose-invert max-w-none break-words text-sm leading-relaxed prose-p:my-2 prose-pre:my-2">
            {toolCalls && toolCalls.length > 0 && (
              <TuringToolActivity
                toolCalls={toolCalls as Parameters<typeof TuringToolActivity>[0]["toolCalls"]}
                className="not-prose mb-2 space-y-1 rounded-lg border border-border/60 bg-muted/30 p-2 text-xs"
                icons={{ tool: <IconTool className="size-3.5 shrink-0" /> }}
              />
            )}
            {content ? (
              <TuringRichContent content={content} markdown={MarkdownBlock} d2={D2Block} />
            ) : null}
            {sources && sources.length > 0 && (
              <TuringSourceChips
                sources={sources as Parameters<typeof TuringSourceChips>[0]["sources"]}
                classNames={SOURCE_CHIP_CLASSNAMES}
                icons={{ source: <IconFileText className="size-3.5 shrink-0" /> }}
              />
            )}
            {options && options.length > 0 && (
              <div className="not-prose mt-3 flex flex-wrap gap-1.5">
                {options.map((q) => (
                  <button
                    key={q}
                    type="button"
                    onClick={() => onPickOption(q)}
                    disabled={isStreaming}
                    className="inline-flex items-center gap-1 rounded-full border border-primary/40 bg-primary/5 px-3 py-1 text-xs font-medium text-primary transition hover:bg-primary/15 disabled:opacity-50"
                  >
                    <IconSparkles className="size-3 shrink-0" />
                    {q}
                  </button>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div className="whitespace-pre-wrap break-words text-sm leading-relaxed">{content}</div>
        )}
      </div>
    </div>
  );
}

export function AtlasChatPanel({
  onClose,
  initialMessage,
  onInitialConsumed,
}: Readonly<{
  onClose: () => void;
  initialMessage?: string | null;
  onInitialConsumed?: () => void;
}>) {
  const cart = useCart();
  const store = useSearchStore();
  const visitorId = useMemo(getVisitorId, []);
  // Answer-as-app generative UI (T442) + cart client tools (T438/T439) +
  // co-browse search-driving tools (T443) + cross-conversation memory tools
  // (T446), merged into one client-tool map.
  const app = useAnswerAsApp();
  const coBrowse = useCoBrowseSearch(store);
  const memory = useTuringUserMemory({ userId: visitorId });
  const cartTools = useMemo(
    () => createCartClientTools({ add: cart.add, remove: cart.remove, clear: cart.clear, items: cart.items }),
    [cart.add, cart.remove, cart.clear, cart.items]
  );
  const clientTools = useMemo(
    () => ({ ...app.clientTools, ...coBrowse.clientTools, ...memory.clientTools, ...cartTools }),
    [app.clientTools, coBrowse.clientTools, memory.clientTools, cartTools]
  );
  const [showMemory, setShowMemory] = useState(false);
  const voice = useTuringVoice({ lang: "en-US" });

  const {
    enabled,
    messages,
    send,
    submitForm,
    activeForm,
    isStreaming,
    status,
    error,
    reset,
    conversationId,
  } = useTuringChat({ locale: import.meta.env.VITE_LOCALE, clientTools });
  const { upload, status: uploadStatus } = useTuringSlotUpload({ vision: true });

  const [input, setInput] = useState("");
  const endRef = useRef<HTMLDivElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const sentInitialRef = useRef<string | null>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, isStreaming]);

  const ask = (q: string) => {
    const trimmed = q.trim();
    if (!trimmed || isStreaming) return;
    setInput("");
    void send(trimmed);
  };

  // Auto-send a prompt handed in via the ask-Atlas bridge (Why #1? / proactive).
  useEffect(() => {
    if (!initialMessage || enabled === false) return;
    if (sentInitialRef.current === initialMessage) return;
    sentInitialRef.current = initialMessage;
    const trimmed = initialMessage.trim();
    if (trimmed && !isStreaming) void send(trimmed);
    onInitialConsumed?.();
  }, [initialMessage, enabled, isStreaming, send, onInitialConsumed]);

  // Voice dictation (T147) — mirror the live transcript into the input box and
  // auto-send once the utterance is final.
  const lastFinalRef = useRef("");
  useEffect(() => {
    if (voice.transcript) setInput(voice.transcript);
    if (voice.isFinal && voice.transcript.trim() && voice.transcript !== lastFinalRef.current) {
      lastFinalRef.current = voice.transcript;
      const t = voice.transcript.trim();
      if (t && !isStreaming) {
        setInput("");
        void send(t);
      }
    }
    // `send`/`isStreaming` are read fresh each render; transcript drives this.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [voice.transcript, voice.isFinal]);

  const onFile = async (file: File | undefined) => {
    if (!file) return;
    // Multimodal slot upload (T401) — fills an "image" slot with vision on.
    await upload("image", file, { vision: true });
  };

  const lastMessage = messages.at(-1);
  const showPlaceholder =
    messages.length > 0 &&
    lastMessage?.role === "user" &&
    (isStreaming || status === "error");

  return (
    <div className="fixed inset-y-0 right-0 z-50 flex w-full max-w-md flex-col border-l border-border bg-background shadow-2xl">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-border px-4 py-3">
        <div className="flex items-center gap-2">
          <span className="grid size-8 place-items-center rounded-lg bg-linear-to-br from-primary to-indigo-700 text-primary-foreground">
            <IconSparkles className="size-4" />
          </span>
          <div className="leading-tight">
            <p className="text-sm font-semibold">Ask Atlas</p>
            <p className="text-[11px] text-muted-foreground">Shopping copilot</p>
          </div>
        </div>
        <div className="flex items-center gap-1">
          <button
            aria-label="What Atlas remembers about you"
            title="What Atlas remembers about you"
            onClick={() => setShowMemory((v) => !v)}
            className={cn(
              "rounded-md p-1.5 hover:bg-accent",
              showMemory && "bg-accent text-primary"
            )}
          >
            <IconBrain className="size-5" />
          </button>
          {messages.length > 0 && (
            <Button variant="ghost" size="sm" onClick={reset} disabled={isStreaming} className="text-xs">
              New chat
            </Button>
          )}
          <button aria-label="Close chat" onClick={onClose} className="rounded-md p-1.5 hover:bg-accent">
            <IconX className="size-5" />
          </button>
        </div>
      </div>

      {/* Cross-conversation personal memory panel (T446) */}
      {showMemory && (
        <div className="border-b border-border bg-muted/30 px-4 py-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              What Atlas remembers
            </span>
            {memory.memories.length > 0 && (
              <button
                onClick={() => void memory.clear()}
                className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-destructive"
              >
                <IconTrash className="size-3.5" /> Forget me
              </button>
            )}
          </div>
          {memory.memories.length === 0 ? (
            <p className="text-xs text-muted-foreground">
              Nothing yet. As you chat, Atlas remembers preferences across sessions.
            </p>
          ) : (
            <ul className="space-y-1">
              {memory.memories.map((m) => (
                <li key={m.id} className="flex items-start justify-between gap-2 text-xs">
                  <span>
                    <span className="font-medium text-foreground/80">{m.key}</span>: {m.content}
                  </span>
                  <button
                    aria-label={`Forget ${m.key}`}
                    onClick={() => void memory.remove(m.id)}
                    className="shrink-0 text-muted-foreground hover:text-destructive"
                  >
                    <IconX className="size-3.5" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {/* Disabled (no agent) notice — honest offline-seed state */}
      {enabled === false && (
        <div className="m-4 flex items-start gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm">
          <IconAlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600" />
          <span className="text-muted-foreground">
            The RAG copilot lights up when this site has an AI agent + LLM configured. The offline
            sample has search only — import Atlas Store into a Turing backend with GenAI enabled to
            chat over the catalog.
          </span>
        </div>
      )}

      {/* Messages */}
      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-4 py-4">
        {messages.length === 0 && enabled !== false && (
          <div className="flex h-full flex-col items-center justify-center gap-2 text-center text-sm text-muted-foreground">
            <IconRobot className="size-8 text-primary/50" />
            <p>Ask about products, compare options, or get a recommendation.</p>
            <div className="mt-2 flex flex-wrap justify-center gap-1.5">
              {["Best wireless headphones under $200?", "Compare the top 3 coffee makers", "What's on sale in Outdoors?"].map(
                (s) => (
                  <button
                    key={s}
                    onClick={() => ask(s)}
                    className="rounded-full border border-border px-3 py-1 text-xs hover:bg-accent"
                  >
                    {s}
                  </button>
                )
              )}
            </div>
          </div>
        )}

        {messages.filter((m) => m.role !== "system").map((m) => (
          <MessageBubble
            key={m.id}
            role={m.role as "user" | "assistant"}
            content={m.content}
            sources={m.sources}
            options={m.options}
            toolCalls={m.toolCalls}
            isStreaming={isStreaming}
            onPickOption={ask}
          />
        ))}

        {/* Answer-as-an-app generative UI (T442): comparison table / spec card /
            configurator rendered from the agent's client-tool calls. */}
        {app.items.length > 0 && (
          <TuringGenerativeContent
            items={app.items}
            registry={app.registry}
            onRespond={app.respond}
            className="space-y-3"
          />
        )}

        {/* Native form (T107) */}
        {activeForm && (
          <NativeForm
            form={activeForm}
            disabled={isStreaming}
            onSubmit={(values) => void submitForm(values)}
          />
        )}

        {showPlaceholder && (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            {isStreaming ? (
              <>
                <IconLoader2 className="size-4 animate-spin" /> Thinking…
              </>
            ) : (
              <span className="text-destructive">{error}</span>
            )}
          </div>
        )}
        <div ref={endRef} />
      </div>

      <SlotsStrip />
      <WorkspaceStrip />

      {/* Composer */}
      <div className="border-t border-border p-3">
        <input
          ref={fileRef}
          type="file"
          accept="image/*"
          hidden
          onChange={(e) => void onFile(e.target.files?.[0])}
        />
        <div className="flex items-end gap-2 rounded-xl border border-border bg-muted/40 p-2">
          <button
            type="button"
            aria-label="Attach image"
            onClick={() => fileRef.current?.click()}
            disabled={isStreaming || uploadStatus === "uploading" || !conversationId}
            title={conversationId ? "Attach an image (vision)" : "Start the chat first"}
            className="shrink-0 rounded-lg p-1.5 text-muted-foreground hover:bg-accent disabled:opacity-40"
          >
            {uploadStatus === "uploading" ? (
              <IconLoader2 className="size-5 animate-spin" />
            ) : (
              <IconPaperclip className="size-5" />
            )}
          </button>
          {voice.sttSupported && (
            <button
              type="button"
              aria-label={voice.isListening ? "Stop voice input" : "Voice input"}
              onClick={() => (voice.isListening ? voice.stopListening() : voice.startListening())}
              disabled={isStreaming || enabled === false}
              title="Dictate your question (T147 voice)"
              className={cn(
                "shrink-0 rounded-lg p-1.5 hover:bg-accent disabled:opacity-40",
                voice.isListening ? "bg-red-500/15 text-red-600" : "text-muted-foreground"
              )}
            >
              <IconMicrophone className={cn("size-5", voice.isListening && "animate-pulse")} />
            </button>
          )}
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                ask(input);
              }
            }}
            rows={1}
            disabled={isStreaming || enabled === false || Boolean(activeForm)}
            placeholder={activeForm ? "Complete the form above…" : "Ask about products…"}
            className="max-h-32 min-h-[32px] flex-1 resize-none bg-transparent px-1 py-1.5 text-sm placeholder:text-muted-foreground/60 focus:outline-none disabled:opacity-60"
          />
          <button
            type="button"
            onClick={() => ask(input)}
            disabled={!input.trim() || isStreaming || enabled === false}
            className="shrink-0 rounded-lg bg-primary p-1.5 text-primary-foreground hover:bg-primary/90 disabled:opacity-40"
          >
            {isStreaming ? <IconLoader2 className="size-4 animate-spin" /> : <IconArrowUp className="size-4" />}
          </button>
        </div>
      </div>
    </div>
  );
}
