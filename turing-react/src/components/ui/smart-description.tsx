"use client"
import { useEffect, useState } from "react"
import { IconLoader2, IconPencil, IconArrowsMaximize, IconCut, IconAbc, IconSparkles } from "@tabler/icons-react"
import { Textarea } from "@/components/ui/textarea"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import { TurChatService, type ChatMessageItem } from "@/services/chat/chat.service"
import { cn } from "@/lib/utils"

const globalSettingsService = new TurGlobalSettingsService()
const chatService = new TurChatService()

type AiAction = "refine" | "expand" | "summarize" | "fix" | "generate"

interface ActionDef {
  key: AiAction
  label: string
  tooltip: string
  icon: typeof IconPencil
  needsText: boolean
  prompt: (ctx: string, maxLen?: number) => string
}

function charLimitHint(maxLen?: number): string {
  if (!maxLen) return ""
  const softLimit = Math.floor(maxLen * 0.8)
  return " LIMIT: Max " + softLimit + " characters. If it exceeds " + maxLen + " characters, it will be rejected."
}

const AI_ACTIONS: ActionDef[] = [
  {
    key: "generate",
    label: "Generate",
    tooltip: "Create a new description from scratch using the title as context",
    icon: IconSparkles,
    needsText: false,
    prompt: (ctx, maxLen) =>
      "Write a professional, concise description for " + ctx + "." + charLimitHint(maxLen) + " Return ONLY the text.",
  },
  {
    key: "refine",
    label: "Refine",
    tooltip: "Improve grammar and style without changing the length",
    icon: IconPencil,
    needsText: true,
    prompt: (_ctx, maxLen) =>
      "Refine the following text: improve grammar, clarity, and style while keeping a similar length." + charLimitHint(maxLen) + " Return ONLY the text.",
  },
  {
    key: "expand",
    label: "Expand",
    tooltip: "Make the text more detailed and informative",
    icon: IconArrowsMaximize,
    needsText: true,
    prompt: (_ctx, maxLen) =>
      "Expand the following text into a more detailed description. Keep it professional and informative." + charLimitHint(maxLen) + " Return ONLY the text.",
  },
  {
    key: "summarize",
    label: "Summarize",
    tooltip: "Shorten the text while keeping the key meaning",
    icon: IconCut,
    needsText: true,
    prompt: (_ctx, maxLen) =>
      "Summarize the following text into a shorter, more concise version while preserving the key meaning." + charLimitHint(maxLen) + " Return ONLY the text.",
  },
  {
    key: "fix",
    label: "Fix",
    tooltip: "Fix only spelling and grammar errors",
    icon: IconAbc,
    needsText: true,
    prompt: (_ctx, maxLen) =>
      "Fix only the spelling and grammar errors in the following text. Do not change the style or meaning." + charLimitHint(maxLen) + " Return ONLY the text.",
  },
]

interface SmartDescriptionProps {
  value?: string | null
  onChange: (value: string) => void
  placeholder?: string
  className?: string
  maxLength?: number
  rows?: number
  /** Title of the entity — used as context for AI generation */
  title?: string
  /** Type of entity (e.g. "Language Model", "AI Agent") — used as context for AI generation */
  entityType?: string
  children?: React.ReactNode
}

function SmartDescriptionComponent({
  value,
  onChange,
  placeholder,
  className,
  maxLength,
  rows,
  title,
  entityType,
  children,
}: SmartDescriptionProps) {
  const text = value ?? ""
  const [defaultLlmId, setDefaultLlmId] = useState<string | null>(null)
  const [activeAction, setActiveAction] = useState<AiAction | null>(null)

  useEffect(() => {
    globalSettingsService.query()
      .then((s) => setDefaultLlmId(s.defaultLlmId ?? null))
      .catch(() => setDefaultLlmId(null))
  }, [])

  const contextLabel = [entityType, title].filter(Boolean).join(" called ") || "item"

  async function handleAction(action: ActionDef) {
    if (!defaultLlmId || activeAction) return
    if (action.needsText && !text.trim()) return
    setActiveAction(action.key)

    const promptText = action.needsText
      ? `${action.prompt(contextLabel, maxLength)}\n\n${text}`
      : action.prompt(contextLabel, maxLength)

    const messages: ChatMessageItem[] = [
      { role: "user", content: promptText },
    ]
    let result = ""

    await chatService.sendStream(
      defaultLlmId,
      messages,
      (token) => {
        result += token
        onChange(maxLength ? result.slice(0, maxLength) : result)
      },
      () => { setActiveAction(null) },
      () => { setActiveAction(null) },
    )
  }

  const hasLlm = !!defaultLlmId
  const hasText = !!text.trim()
  const hasContext = !!(title?.trim() || entityType?.trim())
  const charCount = text.length
  const expandDisabled = !!maxLength && charCount >= maxLength * 0.9

  function isActionDisabled(action: ActionDef): boolean {
    if (activeAction) return true
    if (action.key === "generate") return !hasContext
    if (action.key === "expand") return !hasText || expandDisabled
    return !hasText
  }

  return (
    <div>
      {children}
      <Textarea
        value={text}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={cn("resize-none", className)}
        maxLength={maxLength}
        rows={rows}
      />
      <div className="flex items-center justify-between mt-1.5 gap-2">
        {hasLlm ? (
          <div className="flex flex-wrap gap-1.5">
            {AI_ACTIONS.map((action) => {
              const isActive = activeAction === action.key
              const ActionIcon = action.icon
              return (
                <button
                  key={action.key}
                  type="button"
                  title={action.tooltip}
                  onClick={() => handleAction(action)}
                  disabled={isActionDisabled(action)}
                  className={cn(
                    "inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium transition-all cursor-pointer",
                    "border border-border hover:border-blue-500/40 hover:bg-blue-500/5",
                    "disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:border-border disabled:hover:bg-transparent",
                    isActive && "border-blue-500/50 bg-blue-500/10 text-blue-600 dark:text-blue-400",
                  )}
                >
                  {isActive ? (
                    <IconLoader2 className="size-3.5 animate-spin" />
                  ) : (
                    <ActionIcon className="size-3.5" />
                  )}
                  {action.label}
                </button>
              )
            })}
          </div>
        ) : <div />}
        {maxLength != null && (
          <span className={cn(
            "text-xs tabular-nums shrink-0",
            charCount > maxLength && "text-destructive font-medium",
            charCount <= maxLength && charCount >= maxLength * 0.9 && "text-amber-500 dark:text-amber-400",
            charCount < maxLength * 0.9 && "text-muted-foreground",
          )}>
            {charCount}/{maxLength}
          </span>
        )}
      </div>
    </div>
  )
}

function SmartDescriptionLabel({ children }: { children: React.ReactNode }) {
  return <div className="text-sm font-medium leading-none mb-1.5">{children}</div>
}

function SmartDescriptionDescription({ children }: { children: React.ReactNode }) {
  return <p className="text-xs text-muted-foreground mb-2">{children}</p>
}

export const SmartDescription = Object.assign(SmartDescriptionComponent, {
  Label: SmartDescriptionLabel,
  Description: SmartDescriptionDescription,
})
