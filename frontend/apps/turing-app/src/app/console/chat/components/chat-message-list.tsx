import { useState } from "react"
import { Icon } from "@iconify/react"
import { GradientAvatar, GradientAvatarFallback, GradientAvatarImage } from "@/components/ui/gradient-avatar"
import { useCurrentUser } from "@/contexts/user.context"
import { sanitizeFragment } from "@/lib/sanitize-html"
import { IconAlertTriangle, IconArrowsMinimize, IconBulb, IconCheck, IconChevronDown, IconCopy, IconCpu2, IconFile, IconFileText, IconLoader2, IconThumbDown, IconThumbUp, IconTool, IconUser } from "@tabler/icons-react"
import { TuringChatMessage, TuringCitedAnswer, TuringCopyButton, TuringD2Diagram, TuringMarkdown, TuringRichContent, TuringSourceChips, TuringToolActivity, type TuringCitedMark } from "@viglet/turing-react-sdk"
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card"
import { useTranslation } from "react-i18next"
import rehypeHighlight from "rehype-highlight"
import remarkGfm from "remark-gfm"
import { type ChatMessage, formatFileSize } from "../chat.types"
import { SandboxPlayer } from "./sandbox-player"

interface ChatMessageListProps {
  messages: ChatMessage[]
  loading: boolean
  assistantName: string
  assistantIcon?: string | null
  endRef: React.RefObject<HTMLDivElement | null>
  /**
   * Optional handler invoked when the user clicks one of the {@code suggestedOptions} chips
   * rendered below an assistant message. Receives the chip's label, which the caller should
   * dispatch as the next user message (same effect as typing it in the input). When omitted,
   * chips render disabled.
   *
   * @since 2026.2.7
   */
  onOptionClick?: (label: string) => void
  /**
   * F.9 / T170 — optional thumb-up/down handler. When provided, assistant
   * bubbles render a thumbs control whose click records an operator preference
   * (the DPO signal). The caller resolves the prompt the answer responded to.
   * Omitted → no thumbs render (other chat surfaces are unchanged).
   *
   * @since 2026.3.4
   */
  onFeedback?: (message: ChatMessage, rating: "UP" | "DOWN") => void
  /**
   * F.10 / T172 — optional "rephrase" handler. When provided, assistant bubbles
   * render a "Shorter" action that asks the backend to rewrite the answer
   * (accelerated by OpenAI Predicted Outputs when the agent opted in). The
   * caller resolves the prompt the answer responded to and applies the rewrite.
   * Omitted → no rephrase action renders.
   *
   * @since 2026.3.4
   */
  onRephrase?: (message: ChatMessage, style: string) => void | Promise<void>
}

function MarkdownBlock({ text }: Readonly<{ text: string }>) {
  // Shared markdown contract (`@viglet/turing-react-ui`): handles `sandbox:`
  // artifact URLs (resolve + XSS-safe sanitize) and space-escaping; we just
  // supply this app's plugin set. Code-interpreter URLs arrive same-origin
  // (backend strips `sandbox:` via TurChatArtifactUrls), so no baseUrl needed.
  return (
    <TuringMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
      {text}
    </TuringMarkdown>
  )
}

function D2Block({ code }: Readonly<{ code: string }>) {
  // Admin skin over the shared headless TuringD2Diagram. The D2 engine
  // (@terrastruct/d2) is lazy-imported inside the shared component, so this
  // chunk is only fetched when a reply actually contains a ```d2 block; until
  // it resolves we show the loading caption, and on failure the source.
  return (
    <TuringD2Diagram
      code={code}
      labels={{ loading: "Rendering diagram…", error: "Could not render diagram" }}
      classNames={{
        root: "not-prose my-2 rounded-lg border bg-background p-3 overflow-auto",
        diagram: "flex justify-center [&_svg]:max-w-full [&_svg]:h-auto",
        loading: "text-xs text-muted-foreground",
        error: "block text-xs text-muted-foreground mb-1",
        code: "overflow-auto text-xs bg-muted/30 rounded-md p-2",
      }}
    />
  )
}

function AssistantContent({ content }: Readonly<{ content: string }>) {
  // Shared headless dispatcher (`@viglet/turing-react-ui`): splits the reply into
  // markdown / ```html / ```d2 and renders each — prose via this app's
  // MarkdownBlock skin, ```html via the SandboxPlayer skin, ```d2 via the
  // D2Block skin. viglet.com renders the same segments with its own skins.
  return (
    <TuringRichContent content={content} markdown={MarkdownBlock} html={SandboxPlayer} d2={D2Block} />
  )
}

/**
 * T154 / §X.7.c — citation-aware answer. When Claude returns per-sentence
 * citations (Anthropic Citations, T152/T153) the answer is rendered as text
 * with each grounded claim underlined; hovering shows the source quote + a deep
 * link in a popover. Markdown isn't rendered here (the cited spans are char
 * offsets into the raw text) — `AssistantContent` handles the no-citation path.
 */
function CitedAssistantContent({ message }: Readonly<{ message: ChatMessage }>) {
  const { t } = useTranslation()
  const renderMark = (mark: TuringCitedMark) => (
    <HoverCard openDelay={120} closeDelay={80}>
      <HoverCardTrigger asChild>
        <mark className="cursor-help rounded-sm bg-blue-500/10 underline decoration-blue-500/50 decoration-dotted underline-offset-4 text-foreground">
          {mark.text}
        </mark>
      </HoverCardTrigger>
      <HoverCardContent className="w-80 text-xs">
        <div className="mb-1.5 text-[11px] font-medium uppercase tracking-wide text-muted-foreground/70">
          {t("chat.citations.heading")}
        </div>
        <div className="space-y-2">
          {mark.citations.map((c, i) => (
            <div key={`${c.sourceId ?? c.documentIndex}-${i}`} className="space-y-1">
              <div className="font-medium text-foreground">
                {c.documentTitle || c.sourceId || t("chat.citations.source")}
                {c.locationType === "page" && c.startIndex != null
                  ? ` · ${t("chat.citations.page")} ${c.startIndex}`
                  : ""}
              </div>
              <blockquote className="border-l-2 border-blue-500/40 pl-2 italic text-muted-foreground">
                {c.citedText}
              </blockquote>
              {c.url ? (
                <a
                  className="text-primary hover:underline"
                  href={c.url}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {t("chat.citations.open")}
                </a>
              ) : null}
            </div>
          ))}
        </div>
      </HoverCardContent>
    </HoverCard>
  )
  return (
    <TuringCitedAnswer
      text={message.content}
      citations={message.citations ?? []}
      renderMark={renderMark}
      className="whitespace-pre-wrap break-words"
    />
  )
}

/**
 * Admin skin over the shared headless {@code TuringSourceChips} (T293). Renders
 * the RAG provenance behind an answer as confidence-graded chips with a
 * one-click "why did you say this?" expansion to the cited passages. The
 * `data-confidence` attribute drives the dot color.
 */
function SourceChips({ message }: Readonly<{ message: ChatMessage }>) {
  const { t } = useTranslation()
  if (!message.sources || message.sources.length === 0) return null
  return (
    <TuringSourceChips
      sources={message.sources}
      labels={{
        heading: t("chat.sources.heading"),
        why: t("chat.sources.why"),
        open: t("chat.sources.open"),
        chunk: t("chat.sources.passage"),
        keywordOnly: t("chat.sources.keywordOnly"),
        confidenceHigh: t("chat.sources.confidenceHigh"),
        confidenceMedium: t("chat.sources.confidenceMedium"),
        confidenceLow: t("chat.sources.confidenceLow"),
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
        link: "text-primary hover:underline shrink-0",
      }}
    />
  )
}

/**
 * T490 / §X.19 — Google Search Suggestion chips for a Gemini `google_search`
 * grounded answer. Google's terms of service **require** rendering these chips
 * whenever the grounded answer is shown; `renderedContent` is a self-contained
 * HTML/CSS fragment returned by the API and rendered verbatim. When the API
 * omits the rendered fragment we fall back to plain query chips.
 */
function SearchSuggestions({ message }: Readonly<{ message: ChatMessage }>) {
  const { t } = useTranslation()
  const suggestions = message.searchSuggestions
  if (!suggestions) return null
  const rendered = suggestions.renderedContent?.trim()
  const queries = suggestions.queries ?? []
  if (!rendered && queries.length === 0) return null
  return (
    <div className="not-prose mt-3 border-t border-border/60 pt-2">
      <div className="mb-1.5 text-[11px] font-medium uppercase tracking-wide text-muted-foreground/70">
        {t("chat.searchSuggestions.heading")}
      </div>
      {rendered ? (
        // Google-mandated chips: a self-contained HTML/CSS fragment. Sanitised
        // (T653 / §XXXVII.15) to strip script/iframe/event-handlers/js: URLs
        // while preserving the chip markup + inline styles Google requires.
        // eslint-disable-next-line react/no-danger
        <div
          className="overflow-x-auto"
          dangerouslySetInnerHTML={{ __html: sanitizeFragment(rendered) }}
        />
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {queries.map((q: string) => (
            <a
              key={q}
              href={`https://www.google.com/search?q=${encodeURIComponent(q)}`}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 rounded-full border border-border bg-muted/40 px-2.5 py-1 text-xs text-foreground/80 transition hover:bg-muted hover:border-border/80"
            >
              {q}
            </a>
          ))}
        </div>
      )}
    </div>
  )
}

/**
 * Admin skin over the shared headless {@code TuringToolActivity} (T437). Renders
 * the agent's live tool calls (running → done/failed) above the answer when the
 * agent has `toolCallEventsEnabled`. The `data-status` attribute drives row
 * styling; running rows carry `aria-busy`.
 */
function ToolActivity({ message }: Readonly<{ message: ChatMessage }>) {
  const { t } = useTranslation()
  if (!message.toolCalls || message.toolCalls.length === 0) return null
  return (
    <TuringToolActivity
      toolCalls={message.toolCalls}
      labels={{
        heading: t("chat.tools.heading"),
        calling: t("chat.tools.calling"),
        running: t("chat.tools.running"),
        done: t("chat.tools.done"),
        error: t("chat.tools.error"),
      }}
      icons={{
        tool: <IconTool className="size-3.5 shrink-0" />,
        running: <IconLoader2 className="size-3.5 shrink-0 animate-spin" />,
        done: <IconCheck className="size-3.5 shrink-0 text-emerald-500" />,
        error: <IconAlertTriangle className="size-3.5 shrink-0 text-red-500" />,
      }}
      classNames={{
        container: "not-prose mb-2 border-b border-border/60 pb-2",
        heading: "text-[11px] font-medium uppercase tracking-wide text-muted-foreground/70 mb-1.5",
        list: "flex flex-col gap-1",
        item: "flex items-center gap-2 text-xs text-foreground/80 data-[status=running]:text-foreground",
        name: "font-medium truncate max-w-[200px]",
        status:
          "ml-auto text-[10px] uppercase tracking-wide rounded-full px-1.5 py-0.5 data-[status=running]:bg-blue-500/10 data-[status=running]:text-blue-600 data-[status=done]:bg-emerald-500/10 data-[status=done]:text-emerald-600 data-[status=error]:bg-red-500/10 data-[status=error]:text-red-600",
        duration: "text-[10px] font-mono text-muted-foreground/70",
        args: "sr-only",
      }}
    />
  )
}

/**
 * T178 / §X.13.a — collapsible "Why this answer" panel. When an OpenAI
 * reasoning model (o-series / GPT-5) returns a reasoning summary (the agent
 * opted into the `reasoning-summary` request option), it streams in as a
 * `"reasoning"` SSE event and lands on {@code message.reasoning}. The summary is
 * the model's own gist of its reasoning — never raw chain-of-thought — so it is
 * safe to surface. Collapsed by default; the markdown body renders on expand.
 */
function ReasoningPanel({ message }: Readonly<{ message: ChatMessage }>) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState(false)
  const reasoning = message.reasoning?.trim()
  if (!reasoning) return null
  return (
    <div className="not-prose mt-3 border-t border-border/60 pt-2">
      <button
        type="button"
        aria-expanded={expanded ? "true" : "false"}
        onClick={() => setExpanded((v) => !v)}
        className="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wide text-muted-foreground/70 transition hover:text-foreground"
      >
        <IconBulb className="size-3.5 shrink-0" />
        <span>{t("chat.reasoning.heading", { defaultValue: "Why this answer" })}</span>
        <IconChevronDown
          className={`size-3 shrink-0 opacity-60 transition-transform ${expanded ? "rotate-180" : ""}`}
        />
      </button>
      {expanded ? (
        <div className="mt-1.5 rounded-lg border border-border/60 bg-muted/20 p-2.5 text-xs leading-relaxed text-muted-foreground prose prose-xs dark:prose-invert max-w-none prose-p:my-1">
          <TuringMarkdown remarkPlugins={[remarkGfm]}>{reasoning}</TuringMarkdown>
        </div>
      ) : null}
    </div>
  )
}

/**
 * T516 / §XXVIII.12 — the answer-grounding guardrail verdict badge. Present only
 * when the guardrail is enabled and flagged the answer (ungrounded / unsafe /
 * PII); a clean answer emits no `"grounding"` event so this renders nothing.
 * Surfaced as a compact amber/red chip beside the bubble, with the grounding
 * score and flagged categories.
 */
function GroundingBadge({ message }: Readonly<{ message: ChatMessage }>) {
  const { t } = useTranslation()
  const grounding = message.grounding
  if (!grounding) return null
  const ungrounded = grounding.grounded === false
  const tone = ungrounded
    ? "border-red-500/40 bg-red-500/10 text-red-600 dark:text-red-400"
    : "border-amber-500/40 bg-amber-500/10 text-amber-600 dark:text-amber-400"
  const label = ungrounded
    ? t("chat.grounding.ungrounded", { defaultValue: "Not grounded in sources" })
    : t("chat.grounding.flagged", { defaultValue: "Flagged by guardrail" })
  const score =
    typeof grounding.groundingScore === "number"
      ? ` · ${Math.round(grounding.groundingScore * 100)}%`
      : ""
  const categories = (grounding.categories ?? []).filter((c: string) => c !== "ungrounded")
  return (
    <div
      className={`not-prose mt-3 inline-flex flex-wrap items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs ${tone}`}
      title={t("chat.grounding.tooltip", {
        defaultValue: "Answer-grounding guardrail verdict",
      })}
    >
      <IconAlertTriangle className="size-3.5 shrink-0" />
      <span className="font-medium">
        {label}
        {score}
      </span>
      {categories.length > 0 ? (
        <span className="opacity-80">({categories.join(", ")})</span>
      ) : null}
    </div>
  )
}

/**
 * T522 / §XXVIII.18 — the cross-vendor "second opinion" badge. Present only when
 * the check is enabled and a different-vendor critic returned a verdict. A green
 * "agrees" chip or an amber "disagrees" chip (with the critic's reason), plus the
 * critic vendor and optional confidence.
 */
function SecondOpinionBadge({ message }: Readonly<{ message: ChatMessage }>) {
  const { t } = useTranslation()
  const opinion = message.secondOpinion
  if (!opinion) return null
  const agree = opinion.agree === true
  const tone = agree
    ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
    : "border-amber-500/40 bg-amber-500/10 text-amber-600 dark:text-amber-400"
  const label = agree
    ? t("chat.secondOpinion.agree", { defaultValue: "Second opinion agrees" })
    : t("chat.secondOpinion.disagree", { defaultValue: "Second opinion disagrees" })
  const vendor = opinion.criticVendor ? ` · ${opinion.criticVendor}` : ""
  const confidence =
    typeof opinion.confidence === "number" ? ` · ${Math.round(opinion.confidence * 100)}%` : ""
  return (
    <div
      className={`not-prose mt-3 inline-flex flex-wrap items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs ${tone}`}
      title={opinion.rationale ?? t("chat.secondOpinion.tooltip", {
        defaultValue: "A different-vendor model reviewed this answer",
      })}
    >
      {agree ? (
        <IconThumbUp className="size-3.5 shrink-0" />
      ) : (
        <IconAlertTriangle className="size-3.5 shrink-0" />
      )}
      <span className="font-medium">
        {label}
        {vendor}
        {confidence}
      </span>
    </div>
  )
}

/**
 * F.9 / T170 — operator thumb-up/down on an assistant turn. The click records a
 * DPO preference signal; the selected thumb stays highlighted so the operator
 * sees their rating landed. Only rendered when the list is given an `onFeedback`
 * handler.
 */
function FeedbackButtons({
  message,
  onFeedback,
}: Readonly<{ message: ChatMessage; onFeedback: (m: ChatMessage, r: "UP" | "DOWN") => void }>) {
  const { t } = useTranslation()
  const [rated, setRated] = useState<"UP" | "DOWN" | null>(null)
  const rate = (rating: "UP" | "DOWN") => {
    setRated(rating)
    onFeedback(message, rating)
  }
  return (
    <>
      <button
        type="button"
        aria-label={t("chat.feedback.up", { defaultValue: "Good answer" })}
        title={t("chat.feedback.up", { defaultValue: "Good answer" })}
        onClick={() => rate("UP")}
        className={`rounded-md p-1 transition hover:bg-muted/80 ${rated === "UP" ? "text-emerald-500" : "text-muted-foreground/60 hover:text-foreground"}`}
      >
        <IconThumbUp className="size-3.5" />
      </button>
      <button
        type="button"
        aria-label={t("chat.feedback.down", { defaultValue: "Bad answer" })}
        title={t("chat.feedback.down", { defaultValue: "Bad answer" })}
        onClick={() => rate("DOWN")}
        className={`rounded-md p-1 transition hover:bg-muted/80 ${rated === "DOWN" ? "text-rose-500" : "text-muted-foreground/60 hover:text-foreground"}`}
      >
        <IconThumbDown className="size-3.5" />
      </button>
    </>
  )
}

/**
 * F.10 / T172 — "Rephrase shorter" action on an assistant turn. The click asks
 * the backend to rewrite the answer more concisely; when the agent's LLM is
 * OpenAI-backed with Predicted Outputs (T171) enabled, the previous answer is
 * the prediction so the rewrite is near-instant. Disabled while in flight.
 */
function RephraseButton({
  message,
  onRephrase,
}: Readonly<{
  message: ChatMessage
  onRephrase: (m: ChatMessage, style: string) => void | Promise<void>
}>) {
  const { t } = useTranslation()
  const [busy, setBusy] = useState(false)
  const run = async () => {
    if (busy) return
    setBusy(true)
    try {
      await onRephrase(message, "shorter")
    } finally {
      setBusy(false)
    }
  }
  return (
    <button
      type="button"
      disabled={busy}
      aria-label={t("chat.rephrase.shorter", { defaultValue: "Rephrase shorter" })}
      title={t("chat.rephrase.shorter", { defaultValue: "Rephrase shorter" })}
      onClick={() => void run()}
      className="ml-0.5 inline-flex items-center gap-1 rounded-md px-1.5 py-1 text-[11px] font-medium text-muted-foreground/60 transition hover:bg-muted/80 hover:text-foreground disabled:opacity-60"
    >
      {busy ? <IconLoader2 className="size-3.5 animate-spin" /> : <IconArrowsMinimize className="size-3.5" />}
      <span>{t("chat.rephrase.shorterLabel", { defaultValue: "Shorter" })}</span>
    </button>
  )
}

/** The assistant bubble body: copy button, content (cited-aware), and source chips. */
function AssistantBody({
  message,
  onFeedback,
  onRephrase,
}: Readonly<{
  message: ChatMessage
  onFeedback?: (message: ChatMessage, rating: "UP" | "DOWN") => void
  onRephrase?: (message: ChatMessage, style: string) => void | Promise<void>
}>) {
  const { t } = useTranslation()
  const hasCitations = Boolean(message.citations && message.citations.length > 0)
  return (
    <div className="relative group/msg text-sm leading-relaxed prose prose-sm dark:prose-invert prose-neutral max-w-none break-words prose-p:my-2 prose-pre:my-2 prose-ul:my-2 prose-ol:my-2 prose-headings:my-3 prose-headings:text-foreground prose-strong:text-foreground prose-code:before:content-none prose-code:after:content-none prose-code:bg-muted prose-code:text-foreground prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded-md prose-code:text-[13px] prose-code:font-semibold prose-pre:bg-muted prose-pre:border prose-pre:rounded-lg prose-th:text-foreground prose-a:text-primary prose-blockquote:border-primary/40 prose-blockquote:text-muted-foreground prose-li:marker:text-muted-foreground">
      <TuringCopyButton
        value={message.content}
        labels={{ copy: t("chat.copyToClipboard"), copied: t("chat.copyToClipboard") }}
        icons={{ copy: <IconCopy className="size-4" />, copied: <IconCheck className="size-4" /> }}
        classNames={{
          button: "not-prose absolute top-1 right-1 z-10 rounded-md p-1.5 hover:bg-muted/80 transition-all",
          idle: "opacity-0 group-hover/msg:opacity-100 text-muted-foreground/60 hover:text-foreground",
          copied: "opacity-100 text-emerald-500",
        }}
      />
      <ToolActivity message={message} />
      {hasCitations
        ? <CitedAssistantContent message={message} />
        : <AssistantContent content={message.content} />}
      <SourceChips message={message} />
      <GroundingBadge message={message} />
      <SecondOpinionBadge message={message} />
      <SearchSuggestions message={message} />
      <ReasoningPanel message={message} />
      {(onFeedback || onRephrase) && message.content ? (
        <div className="not-prose mt-2 flex items-center gap-1">
          {onFeedback ? <FeedbackButtons message={message} onFeedback={onFeedback} /> : null}
          {onRephrase ? <RephraseButton message={message} onRephrase={onRephrase} /> : null}
        </div>
      ) : null}
    </div>
  )
}

export function ChatMessageList({ messages, loading, assistantName, assistantIcon, endRef, onOptionClick, onFeedback, onRephrase }: Readonly<ChatMessageListProps>) {
  const { t } = useTranslation()
  const { user } = useCurrentUser()

  return (
    <div className="max-w-5xl mx-auto py-6 px-4 space-y-6">
      {messages.map((message) => {
        const isAssistant = message.role === "assistant"
        const avatar = (
          <GradientAvatar className="size-7">
            {message.role === "user" && user?.avatarUrl && (
              <GradientAvatarImage src={user.avatarUrl} alt={user.username} />
            )}
            <GradientAvatarFallback variant={isAssistant ? "info" : "secondary"}>
              {isAssistant
                ? (assistantIcon ? <Icon icon={assistantIcon} className="size-4" /> : <IconCpu2 className="size-4" />)
                : <IconUser className="size-4" />
              }
            </GradientAvatarFallback>
          </GradientAvatar>
        )
        const attachments = message.attachments && message.attachments.length > 0
          ? message.attachments.map((att, idx) => (
              <div key={idx} className="flex items-center gap-1.5 rounded-lg border bg-muted/50 px-2.5 py-1.5 text-xs text-muted-foreground">
                <IconFile className="size-3.5 shrink-0" />
                <span className="truncate max-w-[150px]">{att.name}</span>
                <span className="text-muted-foreground/60">{formatFileSize(att.size)}</span>
              </div>
            ))
          : null
        const footer = isAssistant && message.suggestedOptions && message.suggestedOptions.length > 0
          ? message.suggestedOptions.map((label) => (
              <button
                key={label}
                type="button"
                onClick={() => onOptionClick?.(label)}
                disabled={!onOptionClick}
                className="inline-flex items-center rounded-full border border-blue-500/40 bg-blue-500/5 px-3 py-1 text-xs font-medium text-blue-700 transition hover:bg-blue-500/15 hover:border-blue-500/70 disabled:opacity-50 disabled:cursor-not-allowed dark:text-blue-300 dark:bg-blue-500/10 dark:border-blue-400/40 dark:hover:bg-blue-500/20"
              >
                {label}
              </button>
            ))
          : null

        return (
          <TuringChatMessage
            key={message.id}
            role={isAssistant ? "assistant" : "user"}
            avatar={avatar}
            name={isAssistant ? assistantName : t("chat.you")}
            status={isAssistant && loading && message === messages.at(-1)
              ? <IconLoader2 className="size-3 animate-spin" />
              : undefined}
            attachments={attachments}
            footer={footer}
            classNames={{
              root: "flex gap-3",
              avatar: "shrink-0 pt-0.5",
              body: "flex-1 min-w-0",
              header: "flex items-center gap-2 text-xs font-medium text-muted-foreground mb-1",
              attachments: "flex flex-wrap gap-2 mb-2",
              footer: "mt-2 flex flex-wrap gap-1.5",
            }}
          >
            {isAssistant ? (
              <AssistantBody message={message} onFeedback={onFeedback} onRephrase={onRephrase} />
            ) : (
              <div className="text-sm leading-relaxed whitespace-pre-wrap break-words">{message.content}</div>
            )}
          </TuringChatMessage>
        )
      })}
      <div ref={endRef} />
    </div>
  )
}
