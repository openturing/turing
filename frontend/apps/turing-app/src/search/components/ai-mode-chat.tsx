import { GradientAvatar, GradientAvatarFallback } from "@/components/ui/gradient-avatar";
import type { TurChatConversationMessage } from "@viglet/turing-react-sdk";
import {
  IconArrowUp,
  IconCpu2,
  IconLoader2,
  IconSparkles,
  IconUser,
} from "@tabler/icons-react";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import ReactMarkdown from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";

interface AiModeChatProps {
  siteName: string;
  locale?: string;
  /** When present, auto-sends this query as the first question (e.g. from the search input). */
  initialQuery?: string;
  /** Called once after the auto-sent initial query is dispatched, so the parent can clear its own input. */
  onInitialQueryConsumed?: () => void;
}

export interface AiModeChatHandle {
  /** Imperatively submit a question to the chat (e.g. from the top search bar). */
  ask: (query: string) => void;
}

interface AiMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
}

const genId = () =>
  (typeof crypto !== "undefined" && crypto.randomUUID)
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

/** Read the CSRF token Spring stores in the XSRF-TOKEN cookie. */
function readXsrfCookie(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function encodeMarkdownLinkUrls(text: string): string {
  return text.replaceAll(
    /\[([^\]]*)\]\(([^)]+)\)/g,
    (_match, label: string, url: string) => `[${label}](<${url.replaceAll(" ", "%20")}>)`,
  );
}

export const AiModeChat = forwardRef<AiModeChatHandle, AiModeChatProps>(function AiModeChat(
  { siteName, locale, initialQuery, onInitialQueryConsumed }: Readonly<AiModeChatProps>,
  ref,
) {
  const { t } = useTranslation();
  const [messages, setMessages] = useState<AiMessage[]>([]);
  const messagesRef = useRef<AiMessage[]>([]);
  messagesRef.current = messages;
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const loadingRef = useRef(false);
  loadingRef.current = loading;
  const endRef = useRef<HTMLDivElement>(null);
  const autoSentRef = useRef(false);
  const initialQueryRef = useRef(initialQuery);
  initialQueryRef.current = initialQuery;
  const onInitialQueryConsumedRef = useRef(onInitialQueryConsumed);
  onInitialQueryConsumedRef.current = onInitialQueryConsumed;

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, loading]);

  const askAi = useCallback(async (query: string) => {
    const trimmed = query.trim();
    if (!trimmed || loadingRef.current) return;

    const userMsg: AiMessage = { id: genId(), role: "user", content: trimmed };
    const assistantId = genId();
    const historyForRequest: TurChatConversationMessage[] = [
      ...messagesRef.current.map((m) => ({ role: m.role, content: m.content })),
      { role: "user", content: trimmed },
    ];
    setMessages((prev) => [...prev, userMsg, { id: assistantId, role: "assistant", content: "" }]);
    setInput("");
    setLoading(true);

    try {
      // Stream the agent-driven RAG response over SSE. The backend now exposes
      // the linked AI agent's tool callings (native + MCP) inside this same
      // endpoint, so the LLM can call tools while still grounded by the site
      // RAG context that the server injects into the system prompt.
      const csrfToken = readXsrfCookie();
      const headers: Record<string, string> = {
        Accept: "text/event-stream",
        "Content-Type": "application/json",
      };
      if (csrfToken) headers["X-XSRF-TOKEN"] = csrfToken;

      const response = await fetch(`/api/sn/${siteName}/chat/conversation`, {
        method: "POST",
        headers,
        credentials: "include",
        body: JSON.stringify({ messages: historyForRequest, locale }),
      });

      if (!response.ok || !response.body) {
        throw new Error(`HTTP ${response.status}`);
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      let assembled = "";

      const flushChunk = (jsonStr: string) => {
        if (!jsonStr) return;
        try {
          const parsed = JSON.parse(jsonStr) as { content?: string };
          if (parsed.content) {
            // The backend executor emits a single ChatResponse at the end
            // (Spring AI resolves the tool-calling loop before flushing), so
            // we replace rather than append. If a future server-side refactor
            // emits incremental tokens, swap to `+= parsed.content`.
            assembled = parsed.content;
            setMessages((prev) =>
              prev.map((m) => (m.id === assistantId ? { ...m, content: assembled } : m)),
            );
          }
        } catch {
          // ignore malformed JSON chunks
        }
      };

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() ?? "";
        for (const line of lines) {
          if (line.startsWith("data:")) flushChunk(line.slice(5).trim());
        }
      }
      if (buffer.startsWith("data:")) flushChunk(buffer.slice(5).trim());

      if (!assembled) {
        setMessages((prev) =>
          prev.map((m) => (m.id === assistantId ? { ...m, content: t("search.aiModeError") } : m)),
        );
      }
    } catch {
      setMessages((prev) =>
        prev.map((m) => (m.id === assistantId ? { ...m, content: t("search.aiModeError") } : m)),
      );
    } finally {
      setLoading(false);
    }
  }, [siteName, locale, t]);

  // Auto-send the initial query once per mount (as soon as one is available).
  useEffect(() => {
    if (autoSentRef.current) return;
    const q = initialQueryRef.current?.trim();
    if (!q || q === "*") return;
    autoSentRef.current = true;
    askAi(q);
    onInitialQueryConsumedRef.current?.();
  }, [askAi, initialQuery]);

  useImperativeHandle(ref, () => ({ ask: askAi }), [askAi]);

  const handleSubmit = () => {
    if (!input.trim() || loading) return;
    askAi(input);
  };

  const isEmpty = messages.length === 0;

  return (
    <div className="flex flex-col min-h-[calc(100vh-10rem)]">
      {isEmpty ? (
        <div className="flex-1 flex flex-col items-center justify-center text-center px-4 py-12">
          <div className="mb-4 rounded-2xl bg-gradient-to-br from-blue-600 to-indigo-600 p-3">
            <IconSparkles className="size-7 text-white" />
          </div>
          <h2 className="text-2xl font-semibold mb-2 bg-gradient-to-r from-blue-600 to-indigo-600 bg-clip-text text-transparent dark:from-blue-400 dark:to-indigo-400">
            {t("search.aiModeEmptyTitle")}
          </h2>
          <p className="text-sm text-muted-foreground max-w-md">
            {t("search.aiModeEmptyDescription")}
          </p>
        </div>
      ) : (
        <div className="flex-1 max-w-3xl w-full mx-auto py-6 space-y-6">
          {messages.map((message) => (
            <div key={message.id} className="flex gap-3">
              <div className="shrink-0 pt-0.5">
                <GradientAvatar className="size-7">
                  <GradientAvatarFallback
                    variant={message.role === "assistant" ? "info" : "secondary"}
                  >
                    {message.role === "assistant" ? (
                      <IconCpu2 className="size-4" />
                    ) : (
                      <IconUser className="size-4" />
                    )}
                  </GradientAvatarFallback>
                </GradientAvatar>
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground mb-1">
                  <span>
                    {message.role === "assistant"
                      ? t("search.aiModeAssistant")
                      : t("search.aiModeYou")}
                  </span>
                  {message.role === "assistant" &&
                    loading &&
                    message === messages.at(-1) &&
                    !message.content && (
                      <span className="inline-flex items-center gap-1">
                        <IconLoader2 className="size-3 animate-spin" />
                        <span>{t("search.aiModeTyping")}</span>
                      </span>
                    )}
                </div>
                {message.role === "assistant" ? (
                  <div className="text-sm leading-relaxed prose prose-sm dark:prose-invert prose-neutral max-w-none break-words prose-p:my-2 prose-pre:my-2 prose-ul:my-2 prose-ol:my-2 prose-headings:my-3 prose-a:text-blue-600 dark:prose-a:text-blue-400">
                    {message.content ? (
                      <ReactMarkdown
                        remarkPlugins={[remarkGfm]}
                        rehypePlugins={[rehypeHighlight]}
                      >
                        {encodeMarkdownLinkUrls(message.content)}
                      </ReactMarkdown>
                    ) : null}
                  </div>
                ) : (
                  <div className="text-sm leading-relaxed whitespace-pre-wrap break-words">
                    {message.content}
                  </div>
                )}
              </div>
            </div>
          ))}
          <div ref={endRef} className="scroll-mb-24" />
        </div>
      )}

      <div className="sticky bottom-0 bg-background/90 backdrop-blur-xl border-t border-border/50 mt-6 -mx-4 lg:-mx-8 px-4 lg:px-8 py-4">
        <div className="max-w-3xl mx-auto">
          <div className="relative group">
            <div className="absolute -inset-0.5 bg-gradient-to-r from-blue-600/20 to-indigo-600/20 rounded-xl opacity-0 group-focus-within:opacity-100 transition-opacity blur-sm" />
            <div className="relative flex items-end gap-2 rounded-xl border border-border bg-muted/50 p-2">
              <textarea
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    handleSubmit();
                  }
                }}
                rows={1}
                className="flex-1 resize-none bg-transparent text-sm placeholder:text-muted-foreground/60 focus:outline-none min-h-[32px] max-h-40 py-1.5 px-2"
                placeholder={t("search.aiModePlaceholder")}
                disabled={loading}
              />
              <button
                type="button"
                onClick={handleSubmit}
                disabled={!input.trim() || loading}
                className="shrink-0 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 p-1.5 text-white hover:shadow-md transition-shadow disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                title={t("search.aiMode")}
              >
                {loading ? (
                  <IconLoader2 className="size-4 animate-spin" />
                ) : (
                  <IconArrowUp className="size-4" />
                )}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
});
