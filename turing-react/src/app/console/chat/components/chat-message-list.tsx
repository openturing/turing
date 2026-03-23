import { Icon } from "@iconify/react"
import { GradientAvatar, GradientAvatarFallback, GradientAvatarImage } from "@/components/ui/gradient-avatar"
import { useCurrentUser } from "@/contexts/user.context"
import { IconCheck, IconCopy, IconCpu2, IconFile, IconLoader2, IconUser } from "@tabler/icons-react"
import { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import ReactMarkdown from "react-markdown"
import rehypeHighlight from "rehype-highlight"
import remarkGfm from "remark-gfm"
import { type ChatMessage, formatFileSize } from "../chat.types"
import { D2Diagram } from "./d2-diagram"
import { SandboxPlayer } from "./sandbox-player"

interface ChatMessageListProps {
  messages: ChatMessage[]
  loading: boolean
  assistantName: string
  assistantIcon?: string | null
  endRef: React.RefObject<HTMLDivElement | null>
}

type ContentSegment =
  | { type: "markdown"; text: string }
  | { type: "d2"; code: string }
  | { type: "html"; code: string }

const SPECIAL_BLOCK_RE = /```(d2|html)\n([\s\S]*?)```/g

function splitContent(content: string): ContentSegment[] {
  const segments: ContentSegment[] = []
  let lastIndex = 0
  for (const match of content.matchAll(SPECIAL_BLOCK_RE)) {
    const before = content.slice(lastIndex, match.index)
    if (before) segments.push({ type: "markdown", text: before })
    const lang = match[1] as "d2" | "html"
    segments.push({ type: lang, code: match[2].replace(/\n$/, "") })
    lastIndex = match.index + match[0].length
  }
  const after = content.slice(lastIndex)
  if (after) segments.push({ type: "markdown", text: after })
  return segments
}

function encodeMarkdownLinkUrls(text: string): string {
  return text.replaceAll(
    /\[([^\]]*)\]\(([^)]+)\)/g,
    (_match, label: string, url: string) => {
      const encoded = url.replaceAll(" ", "%20")
      return `[${label}](<${encoded}>)`
    }
  )
}

function MarkdownBlock({ text }: Readonly<{ text: string }>) {
  return (
    <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
      {encodeMarkdownLinkUrls(text)}
    </ReactMarkdown>
  )
}

function AssistantContent({ content }: Readonly<{ content: string }>) {
  const segments = useMemo(() => splitContent(content), [content])
  return (
    <>
      {segments.map((seg, i) =>
        seg.type === "d2" ? <D2Diagram key={i} code={seg.code} />
        : seg.type === "html" ? <SandboxPlayer key={i} htmlCode={seg.code} />
        : <MarkdownBlock key={i} text={seg.text} />
      )}
    </>
  )
}

export function ChatMessageList({ messages, loading, assistantName, assistantIcon, endRef }: Readonly<ChatMessageListProps>) {
  const { t } = useTranslation()
  const { user } = useCurrentUser()
  const [copiedId, setCopiedId] = useState<string | null>(null)

  const handleCopy = (id: string, content: string) => {
    navigator.clipboard.writeText(content)
    setCopiedId(id)
    setTimeout(() => setCopiedId((prev) => prev === id ? null : prev), 2000)
  }

  return (
    <div className="max-w-3xl mx-auto py-6 px-4 space-y-6">
      {messages.map((message) => (
        <div key={message.id} className="flex gap-3">
          <div className="shrink-0 pt-0.5">
            <GradientAvatar className="size-7">
              {message.role === "user" && user?.avatarUrl && (
                <GradientAvatarImage src={user.avatarUrl} alt={user.username} />
              )}
              <GradientAvatarFallback variant={message.role === "assistant" ? "info" : "secondary"}>
                {message.role === "assistant"
                  ? (assistantIcon ? <Icon icon={assistantIcon} className="size-4" /> : <IconCpu2 className="size-4" />)
                  : <IconUser className="size-4" />
                }
              </GradientAvatarFallback>
            </GradientAvatar>
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground mb-1">
              <span>{message.role === "assistant" ? assistantName : t("chat.you")}</span>
              {message.role === "assistant" && loading && message === messages.at(-1) && (
                <IconLoader2 className="size-3 animate-spin" />
              )}
            </div>
            {message.attachments && message.attachments.length > 0 && (
              <div className="flex flex-wrap gap-2 mb-2">
                {message.attachments.map((att, idx) => (
                  <div key={idx} className="flex items-center gap-1.5 rounded-lg border bg-muted/50 px-2.5 py-1.5 text-xs text-muted-foreground">
                    <IconFile className="size-3.5 shrink-0" />
                    <span className="truncate max-w-[150px]">{att.name}</span>
                    <span className="text-muted-foreground/60">{formatFileSize(att.size)}</span>
                  </div>
                ))}
              </div>
            )}
            {message.role === "assistant" ? (
              <div className="relative group/msg text-sm leading-relaxed prose prose-sm dark:prose-invert prose-neutral max-w-none break-words prose-p:my-2 prose-pre:my-2 prose-ul:my-2 prose-ol:my-2 prose-headings:my-3 prose-headings:text-foreground prose-strong:text-foreground prose-code:before:content-none prose-code:after:content-none prose-code:bg-muted prose-code:text-foreground prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded-md prose-code:text-[13px] prose-code:font-semibold prose-pre:bg-muted prose-pre:border prose-pre:rounded-lg prose-th:text-foreground prose-a:text-primary prose-blockquote:border-primary/40 prose-blockquote:text-muted-foreground prose-li:marker:text-muted-foreground">
                <button
                  type="button"
                  onClick={() => handleCopy(message.id, message.content)}
                  title={t("chat.copyToClipboard")}
                  className={`not-prose absolute top-1 right-1 z-10 rounded-md p-1.5 hover:bg-muted/80 transition-all ${copiedId === message.id ? "opacity-100 text-emerald-500" : "opacity-0 group-hover/msg:opacity-100 text-muted-foreground/60 hover:text-foreground"}`}
                >
                  {copiedId === message.id ? <IconCheck className="size-4" /> : <IconCopy className="size-4" />}
                </button>
                <AssistantContent content={message.content} />
              </div>
            ) : (
              <div className="text-sm leading-relaxed whitespace-pre-wrap break-words">{message.content}</div>
            )}
          </div>
        </div>
      ))}
      <div ref={endRef} />
    </div>
  )
}
