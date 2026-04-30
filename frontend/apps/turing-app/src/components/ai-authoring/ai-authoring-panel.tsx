"use client"
import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { IconLoader2, IconSend, IconSparkles } from "@tabler/icons-react"

import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { cn } from "@/lib/utils"
import type { UseAiAuthoringReturn } from "@/hooks/use-ai-authoring"

/**
 * Right-side chat panel for the AI Authoring layout. Stateless on its own
 * — receives the {@link UseAiAuthoringReturn} from a parent and renders
 * the message history + input.
 *
 * @since 2026.2.5
 */

interface Props {
  readonly chat: UseAiAuthoringReturn
  readonly title: string
  readonly subtitle?: string
  readonly placeholder: string
  readonly emptyState?: string
}

export function AiAuthoringPanel({
  chat,
  title,
  subtitle,
  placeholder,
  emptyState,
}: Props) {
  const { t } = useTranslation()
  const [draft, setDraft] = useState("")
  const scrollRef = useRef<HTMLDivElement>(null)
  const taRef = useRef<HTMLTextAreaElement>(null)

  // Auto-scroll to the bottom whenever a message is appended or the
  // assistant is mid-turn (the spinner placeholder can grow the list).
  useEffect(() => {
    const node = scrollRef.current
    if (node) node.scrollTop = node.scrollHeight
  }, [chat.messages.length, chat.sending])

  async function submit() {
    const trimmed = draft.trim()
    if (!trimmed) return
    setDraft("")
    await chat.send(trimmed)
    taRef.current?.focus()
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <div className="flex h-full flex-col bg-muted/10">
      <header className="flex items-center gap-2 border-b px-4 py-3">
        <IconSparkles className="size-4 text-violet-500" />
        <div className="flex flex-col leading-tight min-w-0">
          <span className="text-sm font-semibold truncate">{title}</span>
          {subtitle && (
            <span className="text-[11px] text-muted-foreground truncate">{subtitle}</span>
          )}
        </div>
      </header>

      <div ref={scrollRef} className="flex-1 min-h-0 overflow-auto px-4 py-3 space-y-3">
        {chat.messages.length === 0 && !chat.sending && (
          <div className="text-xs text-muted-foreground italic py-6 text-center">
            {emptyState ?? t("aiAuthoring.empty")}
          </div>
        )}
        {chat.messages.map((m, i) => (
          <ChatBubble key={i} role={m.role} content={m.content} />
        ))}
        {chat.sending && (
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <IconLoader2 className="size-3.5 animate-spin" />
            {t("aiAuthoring.thinking")}
          </div>
        )}
        {chat.error && (
          <div className="rounded-md border border-destructive/40 bg-destructive/5 p-2 text-xs text-destructive">
            {chat.error}
          </div>
        )}
      </div>

      <footer className="border-t bg-background p-3 space-y-2">
        <Textarea
          ref={taRef}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={placeholder}
          rows={3}
          className="resize-none text-sm"
          disabled={chat.sending}
        />
        <div className="flex justify-between items-center gap-2">
          <span className="text-[10px] text-muted-foreground">
            {t("aiAuthoring.sendHint")}
          </span>
          <Button
            type="button"
            size="sm"
            onClick={submit}
            disabled={chat.sending || !draft.trim()}
            className="gap-2"
          >
            {chat.sending ? (
              <IconLoader2 className="size-4 animate-spin" />
            ) : (
              <IconSend className="size-4" />
            )}
            {t("aiAuthoring.send")}
          </Button>
        </div>
      </footer>
    </div>
  )
}

function ChatBubble({ role, content }: { role: string; content: string }) {
  const isUser = role === "user"
  return (
    <div className={cn("flex", isUser ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "max-w-[85%] rounded-lg px-3 py-2 text-sm whitespace-pre-wrap break-words",
          isUser
            ? "bg-blue-600 text-white"
            : "border border-border bg-background text-foreground",
        )}
      >
        {content || <span className="opacity-60 italic">…</span>}
      </div>
    </div>
  )
}
