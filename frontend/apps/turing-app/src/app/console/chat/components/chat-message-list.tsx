import { Icon } from "@iconify/react"
import { GradientAvatar, GradientAvatarFallback, GradientAvatarImage } from "@/components/ui/gradient-avatar"
import { useCurrentUser } from "@/contexts/user.context"
import { IconCheck, IconCopy, IconCpu2, IconFile, IconLoader2, IconUser } from "@tabler/icons-react"
import { TuringChatMessage, TuringCopyButton, TuringD2Diagram, TuringMarkdown, TuringRichContent } from "@viglet/turing-react-ui"
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

export function ChatMessageList({ messages, loading, assistantName, assistantIcon, endRef, onOptionClick }: Readonly<ChatMessageListProps>) {
  const { t } = useTranslation()
  const { user } = useCurrentUser()

  return (
    <div className="max-w-3xl mx-auto py-6 px-4 space-y-6">
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
                <AssistantContent content={message.content} />
              </div>
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
