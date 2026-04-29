"use client"
import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { IconLoader2, IconPencil, IconArrowsMaximize, IconCut, IconAbc, IconSparkles, IconWand } from "@tabler/icons-react"
import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { Textarea } from "@/components/ui/textarea"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import { TurChatService, type ChatMessageItem } from "@/services/chat/chat.service"
import { cn } from "@/lib/utils"

const globalSettingsService = new TurGlobalSettingsService()
const chatService = new TurChatService()

type AiAction = "refine" | "expand" | "summarize" | "fix" | "generate"

interface ActionDef {
  key: AiAction
  labelKey: string
  tooltipKey: string
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
    labelKey: "forms.smartDescription.generate",
    tooltipKey: "forms.smartDescription.generateTooltip",
    icon: IconSparkles,
    needsText: false,
    prompt: (ctx, maxLen) =>
      "Write a professional, concise description for " + ctx + "." + charLimitHint(maxLen) + " Return ONLY the text.",
  },
  {
    key: "refine",
    labelKey: "forms.smartDescription.refine",
    tooltipKey: "forms.smartDescription.refineTooltip",
    icon: IconPencil,
    needsText: true,
    prompt: (_ctx, maxLen) =>
      "Refine the following text: improve grammar, clarity, and style while keeping a similar length." + charLimitHint(maxLen) + " Return ONLY the text.",
  },
  {
    key: "expand",
    labelKey: "forms.smartDescription.expand",
    tooltipKey: "forms.smartDescription.expandTooltip",
    icon: IconArrowsMaximize,
    needsText: true,
    prompt: (_ctx, maxLen) =>
      "Expand the following text into a more detailed description. Keep it professional and informative." + charLimitHint(maxLen) + " Return ONLY the text.",
  },
  {
    key: "summarize",
    labelKey: "forms.smartDescription.summarize",
    tooltipKey: "forms.smartDescription.summarizeTooltip",
    icon: IconCut,
    needsText: true,
    prompt: (_ctx, maxLen) =>
      "Summarize the following text into a shorter, more concise version while preserving the key meaning." + charLimitHint(maxLen) + " Return ONLY the text.",
  },
  {
    key: "fix",
    labelKey: "forms.smartDescription.fix",
    tooltipKey: "forms.smartDescription.fixTooltip",
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
  /**
   * Persisted user-written meta-prompt — instructions describing how the
   * description should be generated (e.g. tone, target language, content
   * focus). When this prop AND {@link onMetaPromptChange} are provided, a
   * "Help me write" action becomes available; clicking it opens a modal
   * to edit the meta-prompt and call the LLM with it as the brief.
   */
  metaPrompt?: string | null
  onMetaPromptChange?: (value: string) => void
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
  metaPrompt,
  onMetaPromptChange,
  children,
}: SmartDescriptionProps) {
  const { t } = useTranslation()
  const text = value ?? ""
  const [defaultLlmId, setDefaultLlmId] = useState<string | null>(null)
  const [activeAction, setActiveAction] = useState<AiAction | null>(null)
  const [metaOpen, setMetaOpen] = useState(false)
  const [metaDraft, setMetaDraft] = useState(metaPrompt ?? "")
  const [metaRunning, setMetaRunning] = useState(false)
  const metaTextareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    globalSettingsService.query()
      .then((s) => setDefaultLlmId(s.defaultLlmId ?? null))
      .catch(() => setDefaultLlmId(null))
  }, [])

  // Re-sync local draft when the parent value changes from outside (e.g. form reset)
  useEffect(() => {
    setMetaDraft(metaPrompt ?? "")
  }, [metaPrompt])

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

  async function handleMetaPromptRun() {
    if (!defaultLlmId || metaRunning) return
    const brief = metaDraft.trim()
    if (!brief) return
    setMetaRunning(true)
    onMetaPromptChange?.(metaDraft)

    const promptText = [
      `You are helping a user write a description for ${contextLabel}.`,
      charLimitHint(maxLength).trim(),
      "",
      text.trim()
        ? `Current description:\n${text}\n`
        : "There is no current description yet.",
      "User instructions (apply them strictly):",
      brief,
      "",
      "Return ONLY the resulting description text, with no preamble or quotes.",
    ].filter(Boolean).join("\n")

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
      () => { setMetaRunning(false); setMetaOpen(false) },
      () => { setMetaRunning(false) },
    )
  }

  const hasLlm = !!defaultLlmId
  const hasText = !!text.trim()
  const hasContext = !!(title?.trim() || entityType?.trim())
  const charCount = text.length
  const expandDisabled = !!maxLength && charCount >= maxLength * 0.9
  const metaSupported = !!onMetaPromptChange

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
                  title={t(action.tooltipKey)}
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
                  {t(action.labelKey)}
                </button>
              )
            })}
            {metaSupported && (
              <button
                type="button"
                title={t("forms.smartDescription.metaPromptTooltip")}
                onClick={() => setMetaOpen(true)}
                disabled={!!activeAction}
                className={cn(
                  "inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium transition-all cursor-pointer",
                  "border border-violet-500/40 text-violet-600 dark:text-violet-300",
                  "hover:border-violet-500/60 hover:bg-violet-500/10",
                  "disabled:opacity-40 disabled:cursor-not-allowed",
                  (metaPrompt?.trim()) && "bg-violet-500/5",
                )}
              >
                <IconWand className="size-3.5" />
                {t("forms.smartDescription.metaPrompt")}
              </button>
            )}
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

      {metaSupported && (
        <Sheet open={metaOpen} onOpenChange={(o) => !metaRunning && setMetaOpen(o)}>
          <SheetContent
            side="right"
            className="flex w-full flex-col gap-0 p-0 sm:max-w-xl"
            onOpenAutoFocus={(e) => {
              e.preventDefault();
              metaTextareaRef.current?.focus();
            }}
          >
            <SheetHeader className="border-b">
              <SheetTitle className="flex items-center gap-2">
                <IconWand className="size-4 text-violet-500" />
                {t("forms.smartDescription.metaPromptTitle")}
              </SheetTitle>
              <SheetDescription>
                {t("forms.smartDescription.metaPromptDescription")}
              </SheetDescription>
            </SheetHeader>

            <div className="flex flex-1 min-h-0 flex-col gap-2 overflow-auto p-4">
              <Textarea
                ref={metaTextareaRef}
                value={metaDraft}
                onChange={(e) => setMetaDraft(e.target.value)}
                placeholder={t("forms.smartDescription.metaPromptPlaceholder")}
                className="min-h-70 flex-1 resize-none"
                disabled={metaRunning}
              />
              <p className="shrink-0 text-xs text-muted-foreground">
                {t("forms.smartDescription.metaPromptHint")}
              </p>
            </div>

            <SheetFooter className="flex-row justify-end gap-2 border-t bg-background p-4">
              <Button
                type="button"
                variant="outline"
                onClick={() => setMetaOpen(false)}
                disabled={metaRunning}
              >
                {t("common.cancel")}
              </Button>
              <Button
                type="button"
                onClick={handleMetaPromptRun}
                disabled={metaRunning || !metaDraft.trim() || !defaultLlmId}
                className="gap-2"
              >
                {metaRunning ? (
                  <IconLoader2 className="size-4 animate-spin" />
                ) : (
                  <IconSparkles className="size-4" />
                )}
                {t("forms.smartDescription.metaPromptGenerate")}
              </Button>
            </SheetFooter>
          </SheetContent>
        </Sheet>
      )}
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
