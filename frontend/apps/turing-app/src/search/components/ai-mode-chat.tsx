import { GradientAvatar, GradientAvatarFallback } from "@/components/ui/gradient-avatar";
import { TuringMarkdown, TuringSourceChips, useTuringChat } from "@viglet/turing-react-sdk";
import {
  IconArrowUp,
  IconChevronDown,
  IconCpu2,
  IconFileText,
  IconLoader2,
  IconPlus,
  IconSparkles,
  IconUser,
} from "@tabler/icons-react";
import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
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

/**
 * Public-chat skin over the shared headless {@code TuringSourceChips} (T332).
 * Renders the RAG provenance behind a grounded AI-mode answer as
 * confidence-graded chips with a one-click "why did you say this?" trace,
 * fed by the {@code sources[]} SSE event the SN chat now emits (T327).
 */
function AiModeSourceChips({ message }: Readonly<{ message: { sources?: unknown[] } }>) {
  const { t } = useTranslation();
  const sources = message.sources;
  if (!sources || sources.length === 0) return null;
  return (
    <TuringSourceChips
      sources={sources as Parameters<typeof TuringSourceChips>[0]["sources"]}
      labels={{
        heading: t("search.aiModeSources.heading"),
        why: t("search.aiModeSources.why"),
        open: t("search.aiModeSources.open"),
        chunk: t("search.aiModeSources.passage"),
        keywordOnly: t("search.aiModeSources.keywordOnly"),
        confidenceHigh: t("search.aiModeSources.confidenceHigh"),
        confidenceMedium: t("search.aiModeSources.confidenceMedium"),
        confidenceLow: t("search.aiModeSources.confidenceLow"),
      }}
      icons={{
        source: <IconFileText className="size-3.5 shrink-0" />,
        expand: <IconChevronDown className="size-3 shrink-0 opacity-60" />,
      }}
      classNames={{
        container: "not-prose mt-3 border-t border-border/60 pt-2",
        heading: "text-[11px] font-medium uppercase tracking-wide text-muted-foreground/70 mb-1.5",
        list: "flex flex-wrap gap-1.5",
        chip: "inline-flex items-center gap-1.5 rounded-full border border-border bg-muted/40 px-2.5 py-1 text-xs text-foreground/80 transition hover:bg-muted hover:border-border/80 max-w-full [&>span:nth-child(2)]:truncate [&>span:nth-child(2)]:max-w-[180px]",
        chipExpanded: "bg-muted border-blue-500/50",
        chipKeyword: "border-amber-500/40",
        confidence:
          "size-2 rounded-full shrink-0 data-[confidence=high]:bg-emerald-500 data-[confidence=medium]:bg-amber-500 data-[confidence=low]:bg-muted-foreground/40",
        panel: "mt-1.5 w-full space-y-1 rounded-lg border border-border/60 bg-muted/20 p-2.5",
        panelCaption: "text-[11px] font-medium text-muted-foreground mb-1",
        chunk: "flex items-center justify-between gap-3 text-xs text-muted-foreground py-0.5",
        chunkMeta: "font-mono text-[11px]",
        link: "text-blue-600 dark:text-blue-400 hover:underline shrink-0",
      }}
    />
  );
}

export const AiModeChat = forwardRef<AiModeChatHandle, AiModeChatProps>(function AiModeChat(
  { locale, initialQuery, onInitialQueryConsumed }: Readonly<AiModeChatProps>,
  ref,
) {
  const { t } = useTranslation();
  // siteName is implicit — `useTuringChat` reads it from the surrounding
  // TuringProvider config, so we don't need to thread it through here.
  const { messages, send, isStreaming, error, status, reset, resetFlow } = useTuringChat({ locale });

  const [input, setInput] = useState("");
  const endRef = useRef<HTMLDivElement>(null);
  const autoSentRef = useRef(false);
  const initialQueryRef = useRef(initialQuery);
  initialQueryRef.current = initialQuery;
  const onInitialQueryConsumedRef = useRef(onInitialQueryConsumed);
  onInitialQueryConsumedRef.current = onInitialQueryConsumed;

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, isStreaming]);

  const askAi = useCallback(
    (query: string) => {
      const trimmed = query.trim();
      if (!trimmed || isStreaming) return;
      setInput("");
      void send(trimmed);
    },
    [send, isStreaming],
  );

  // "New chat" — clears local history AND any server-side chat-flow runtime
  // state attached to the current conversation, so the next question starts
  // from a clean slate. resetFlow is best-effort (the auto-router may not
  // have created any state yet); we don't block the local reset on it.
  const handleNewChat = useCallback(() => {
    if (isStreaming) return;
    void resetFlow();
    reset();
    setInput("");
    autoSentRef.current = true;
  }, [isStreaming, resetFlow, reset]);

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

  const handleSubmit = () => askAi(input);

  const isEmpty = messages.length === 0;
  // The hook commits the user message immediately and only appends the
  // assistant reply once the SSE stream closes. While the request is
  // in-flight (or after a transport failure), render a placeholder bubble so
  // the user sees the typing indicator / error feedback inline with the chat.
  const lastMessage = messages.at(-1);
  const showAssistantPlaceholder =
    !isEmpty && lastMessage?.role === "user" && (isStreaming || status === "error");
  const placeholderError = status === "error" ? error ?? t("search.aiModeError") : null;

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
          <div className="flex justify-end">
            <button
              type="button"
              onClick={handleNewChat}
              disabled={isStreaming}
              className="inline-flex items-center gap-1.5 rounded-lg border border-border/60 bg-background px-3 py-1.5 text-xs font-medium text-muted-foreground hover:text-foreground hover:bg-muted/60 transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
              title={t("search.aiModeNewChat")}
            >
              <IconPlus className="size-3.5" />
              {t("search.aiModeNewChat")}
            </button>
          </div>
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
                </div>
                {message.role === "assistant" ? (
                  <div className="text-sm leading-relaxed prose prose-sm dark:prose-invert prose-neutral max-w-none break-words prose-p:my-2 prose-pre:my-2 prose-ul:my-2 prose-ol:my-2 prose-headings:my-3 prose-a:text-blue-600 dark:prose-a:text-blue-400">
                    {message.content ? (
                      <TuringMarkdown
                        remarkPlugins={[remarkGfm]}
                        rehypePlugins={[rehypeHighlight]}
                      >
                        {message.content}
                      </TuringMarkdown>
                    ) : null}
                    <AiModeSourceChips message={message} />
                    {message.options && message.options.length > 0 ? (
                      <div className="not-prose mt-3 flex flex-wrap gap-1.5">
                        {message.options.map((q) => (
                          <button
                            key={q}
                            type="button"
                            onClick={() => askAi(q)}
                            disabled={isStreaming}
                            className="inline-flex items-center gap-1 rounded-full border border-blue-500/40 bg-blue-500/5 px-3 py-1 text-xs font-medium text-blue-700 transition hover:bg-blue-500/15 hover:border-blue-500/70 disabled:opacity-50 disabled:cursor-not-allowed dark:text-blue-300 dark:bg-blue-500/10 dark:border-blue-400/40 dark:hover:bg-blue-500/20"
                          >
                            <IconSparkles className="size-3 shrink-0" />
                            {q}
                          </button>
                        ))}
                      </div>
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
          {showAssistantPlaceholder && (
            <div className="flex gap-3">
              <div className="shrink-0 pt-0.5">
                <GradientAvatar className="size-7">
                  <GradientAvatarFallback variant="info">
                    <IconCpu2 className="size-4" />
                  </GradientAvatarFallback>
                </GradientAvatar>
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground mb-1">
                  <span>{t("search.aiModeAssistant")}</span>
                  {isStreaming && (
                    <span className="inline-flex items-center gap-1">
                      <IconLoader2 className="size-3 animate-spin" />
                      <span>{t("search.aiModeTyping")}</span>
                    </span>
                  )}
                </div>
                {placeholderError && (
                  <div className="text-sm leading-relaxed text-destructive">
                    {placeholderError}
                  </div>
                )}
              </div>
            </div>
          )}
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
                disabled={isStreaming}
              />
              <button
                type="button"
                onClick={handleSubmit}
                disabled={!input.trim() || isStreaming}
                className="shrink-0 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 p-1.5 text-white hover:shadow-md transition-shadow disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                title={t("search.aiMode")}
              >
                {isStreaming ? (
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
