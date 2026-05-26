"use client"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { IconLoader2, IconPencil, IconArrowsMaximize, IconCut, IconAbc, IconSparkles, IconWand } from "@tabler/icons-react"
import { MetaPromptSheet } from "@/components/ui/meta-prompt-sheet"
import { Textarea } from "@/components/ui/textarea"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import { postLlmChat, type TurChatConversationMessage } from "@viglet/turing-react-sdk"
import { cn } from "@/lib/utils"

const globalSettingsService = new TurGlobalSettingsService()

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
   * Structural instruction about the field being edited (purpose, target
   * language, audience, hard constraints). Sent as a system message before
   * EVERY LLM call (generate / refine / expand / summarize / fix / metaPrompt)
   * so it carries more weight than user input — the user shouldn't be able
   * to override field-level rules through the meta-prompt.
   */
  fieldInstruction?: string | null
  /**
   * Explicitly enables the "Help me write" meta-prompt action. Useful when
   * the brief should NOT be persisted (ephemeral, lives only while the
   * component is mounted). When {@link onMetaPromptChange} is provided the
   * action is also enabled automatically — this flag is for the
   * persistence-less case.
   */
  enableMetaPrompt?: boolean
  /**
   * User-written meta-prompt — instructions describing how the description
   * should be generated (e.g. tone, target language, content focus). The
   * "Help me write" action is shown when this is set OR when
   * {@link enableMetaPrompt} is true OR when {@link onMetaPromptChange}
   * is provided. Persistence is optional: without
   * {@link onMetaPromptChange} the brief lives only in local state.
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
  fieldInstruction,
  enableMetaPrompt,
  metaPrompt,
  onMetaPromptChange,
  children,
}: SmartDescriptionProps) {
  const { t } = useTranslation()
  const text = value ?? ""
  const [defaultLlmId, setDefaultLlmId] = useState<string | null>(null)
  const [activeAction, setActiveAction] = useState<AiAction | null>(null)

  useEffect(() => {
    globalSettingsService.query()
      .then((s) => setDefaultLlmId(s.defaultLlmId ?? null))
      .catch(() => setDefaultLlmId(null))
  }, [])

  const contextLabel = [entityType, title].filter(Boolean).join(" called ") || "item"

  /** Prepends a system message with the field-level instruction (if any). */
  function buildMessages(userContent: string): TurChatConversationMessage[] {
    const messages: TurChatConversationMessage[] = []
    const instruction = fieldInstruction?.trim()
    if (instruction) {
      messages.push({ role: "system", content: instruction })
    }
    messages.push({ role: "user", content: userContent })
    return messages
  }

  async function handleAction(action: ActionDef) {
    if (!defaultLlmId || activeAction) return
    if (action.needsText && !text.trim()) return
    setActiveAction(action.key)

    const promptText = action.needsText
      ? `${action.prompt(contextLabel, maxLength)}\n\n${text}`
      : action.prompt(contextLabel, maxLength)

    let result = ""

    try {
      await postLlmChat(defaultLlmId, buildMessages(promptText), {
        onToken: (token) => {
          result += token
          onChange(maxLength ? result.slice(0, maxLength) : result)
        },
      })
    } catch {
      // streaming failure leaves whatever tokens already arrived in place
    } finally {
      setActiveAction(null)
    }
  }

  function buildMetaPromptUserMessage({ value, brief }: { value: string; brief: string; maxLength?: number }): string {
    return [
      `You are helping a user write a description for ${contextLabel}.`,
      charLimitHint(maxLength).trim(),
      "",
      value.trim()
        ? `Current description:\n${value}\n`
        : "There is no current description yet.",
      "User instructions (apply them strictly):",
      brief,
      "",
      "Return ONLY the resulting description text, with no preamble or quotes.",
    ].filter(Boolean).join("\n")
  }

  const hasLlm = !!defaultLlmId
  const hasText = !!text.trim()
  const hasContext = !!(title?.trim() || entityType?.trim())
  const charCount = text.length
  const expandDisabled = !!maxLength && charCount >= maxLength * 0.9
  // Persistence is optional — explicit opt-in via enableMetaPrompt OR a
  // persistence callback OR an initial brief is enough to surface the action.
  const metaSupported = enableMetaPrompt === true || !!onMetaPromptChange || (metaPrompt ?? "").length > 0

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
              <MetaPromptSheet
                value={text}
                onChange={onChange}
                metaPrompt={metaPrompt}
                onMetaPromptChange={onMetaPromptChange}
                fieldInstruction={fieldInstruction ?? ""}
                triggerVariant="chip"
                tone="violet"
                triggerIcon={IconWand}
                triggerLabel={t("forms.smartDescription.metaPrompt")}
                triggerTooltip={t("forms.smartDescription.metaPromptTooltip")}
                title={t("forms.smartDescription.metaPromptTitle")}
                description={t("forms.smartDescription.metaPromptDescription")}
                placeholder={t("forms.smartDescription.metaPromptPlaceholder")}
                hint={t("forms.smartDescription.metaPromptHint")}
                generateLabel={t("forms.smartDescription.metaPromptGenerate")}
                maxLength={maxLength}
                buildUserMessage={buildMetaPromptUserMessage}
                disabled={!!activeAction}
              />
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
