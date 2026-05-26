"use client"
import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  IconLoader2,
  IconSparkles,
  IconWand,
  type Icon,
} from "@tabler/icons-react"

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
import { cn } from "@/lib/utils"
import { postLlmChat, type TurChatConversationMessage } from "@viglet/turing-react-sdk"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"

/**
 * Reusable "Help me write" / "Vibe coding" affordance: opens a side
 * Sheet with a brief textarea, calls the default LLM with an explicit
 * system instruction, and streams the result into a target field.
 *
 * Persistence is optional — when {@code onMetaPromptChange} is omitted,
 * the brief lives only in component-local state and is lost on unmount.
 *
 * Two trigger styles ship out of the box:
 * <ul>
 *   <li>{@code chip} — small rounded pill, suitable for inline action toolbars
 *       (e.g. inside SmartDescription next to other AI chips).</li>
 *   <li>{@code button} — standard outline button, suitable for being placed
 *       next to other form actions (e.g. next to a "Validate" button).</li>
 * </ul>
 *
 * @since 2026.2.5
 */

const globalSettingsService = new TurGlobalSettingsService()

export type MetaPromptTone = "violet" | "fuchsia" | "blue" | "emerald"

const TONE_CHIP_CLASSES: Record<MetaPromptTone, string> = {
  violet: "border-violet-500/40 text-violet-600 dark:text-violet-300 hover:border-violet-500/60 hover:bg-violet-500/10",
  fuchsia: "border-fuchsia-500/40 text-fuchsia-600 dark:text-fuchsia-300 hover:border-fuchsia-500/60 hover:bg-fuchsia-500/10",
  blue: "border-blue-500/40 text-blue-600 dark:text-blue-300 hover:border-blue-500/60 hover:bg-blue-500/10",
  emerald: "border-emerald-500/40 text-emerald-600 dark:text-emerald-300 hover:border-emerald-500/60 hover:bg-emerald-500/10",
}

const TONE_ACTIVE_BG: Record<MetaPromptTone, string> = {
  violet: "bg-violet-500/5",
  fuchsia: "bg-fuchsia-500/5",
  blue: "bg-blue-500/5",
  emerald: "bg-emerald-500/5",
}

const TONE_ICON_CLASS: Record<MetaPromptTone, string> = {
  violet: "text-violet-500",
  fuchsia: "text-fuchsia-500",
  blue: "text-blue-500",
  emerald: "text-emerald-500",
}

export interface MetaPromptSheetProps {
  /** Current value of the target field (description, code, prompt, etc). */
  readonly value: string
  /** Stream sink — called once per token with the running result. */
  readonly onChange: (value: string) => void

  /** Persisted brief; when null/empty the textarea opens blank. */
  readonly metaPrompt?: string | null
  /**
   * When provided, the brief is persisted on every Generate click. When
   * omitted, the brief is purely local and lost when the parent unmounts.
   */
  readonly onMetaPromptChange?: (value: string) => void

  /**
   * System message describing the field's purpose, audience, language, and
   * any hard constraints. Sent as {@code role:"system"} so it carries more
   * weight than the user-typed brief.
   */
  readonly fieldInstruction: string

  /** Trigger button copy. */
  readonly triggerLabel: string
  /** Optional title attribute on the trigger. */
  readonly triggerTooltip?: string
  /** Trigger icon — defaults to {@link IconWand}. */
  readonly triggerIcon?: Icon
  /** Pre-bundled color tone. Ignored when {@link triggerClassName} is set. */
  readonly tone?: MetaPromptTone
  /** Hard override for the trigger className (skips tone presets). */
  readonly triggerClassName?: string
  /** {@code chip} (small pill) or {@code button} (regular outline). */
  readonly triggerVariant?: "chip" | "button"

  /** Sheet header copy. */
  readonly title: string
  readonly description: string
  /** Textarea placeholder + helper text under it. */
  readonly placeholder: string
  readonly hint?: string
  /** Generate button copy. */
  readonly generateLabel: string

  /**
   * Hard cap clamped on the streamed result before {@link onChange}. Useful
   * for description fields with a column maxLength.
   */
  readonly maxLength?: number
  /**
   * Post-processing applied to every accumulated chunk before
   * {@link onChange}. Default = identity. Use to strip ``` fences when
   * generating code.
   */
  readonly postProcess?: (raw: string) => string
  /**
   * Builds the user message body sent to the LLM. Default produces a
   * "Current text / User instructions / Return ONLY..." template. Override
   * for code generation (different framing) or tighter contracts.
   */
  readonly buildUserMessage?: (ctx: { value: string; brief: string; maxLength?: number }) => string

  readonly disabled?: boolean
}

export function MetaPromptSheet({
  value,
  onChange,
  metaPrompt,
  onMetaPromptChange,
  fieldInstruction,
  triggerLabel,
  triggerTooltip,
  triggerIcon,
  tone = "violet",
  triggerClassName,
  triggerVariant = "button",
  title,
  description,
  placeholder,
  hint,
  generateLabel,
  maxLength,
  postProcess,
  buildUserMessage,
  disabled,
}: MetaPromptSheetProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState(metaPrompt ?? "")
  const [running, setRunning] = useState(false)
  const [defaultLlmId, setDefaultLlmId] = useState<string | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const TriggerIcon = triggerIcon ?? IconWand
  const hasBrief = !!metaPrompt?.trim() || !!draft.trim()

  useEffect(() => {
    globalSettingsService.query()
      .then((s) => setDefaultLlmId(s.defaultLlmId ?? null))
      .catch(() => setDefaultLlmId(null))
  }, [])

  // Re-sync local draft when the parent prop changes (e.g. form reset).
  useEffect(() => {
    setDraft(metaPrompt ?? "")
  }, [metaPrompt])

  async function handleRun() {
    if (!defaultLlmId || running) return
    const brief = draft.trim()
    if (!brief) return
    setRunning(true)
    onMetaPromptChange?.(draft)

    const userContent = (buildUserMessage ?? defaultBuildUserMessage)({
      value,
      brief,
      maxLength,
    })

    const messages: TurChatConversationMessage[] = [
      { role: "system", content: fieldInstruction },
      { role: "user", content: userContent },
    ]

    let result = ""
    const apply = postProcess ?? identity
    try {
      await postLlmChat(defaultLlmId, messages, {
        onToken: (token) => {
          result += token
          const processed = apply(result)
          onChange(maxLength ? processed.slice(0, maxLength) : processed)
        },
      })
      setOpen(false)
    } catch {
      // leave whatever tokens already streamed; the sheet stays open so the user can retry
    } finally {
      setRunning(false)
    }
  }

  const triggerToneClass = triggerClassName ?? TONE_CHIP_CLASSES[tone]
  const activeBg = hasBrief ? TONE_ACTIVE_BG[tone] : ""

  return (
    <>
      {triggerVariant === "chip" ? (
        <button
          type="button"
          title={triggerTooltip}
          onClick={() => setOpen(true)}
          disabled={disabled || !defaultLlmId}
          className={cn(
            "inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium transition-all cursor-pointer border",
            triggerToneClass,
            "disabled:opacity-40 disabled:cursor-not-allowed",
            activeBg,
          )}
        >
          <TriggerIcon className="size-3.5" />
          {triggerLabel}
        </button>
      ) : (
        <Button
          type="button"
          variant="outline"
          size="sm"
          title={triggerTooltip}
          onClick={() => setOpen(true)}
          disabled={disabled || !defaultLlmId}
          className={cn("gap-2", triggerToneClass, activeBg)}
        >
          <TriggerIcon className="size-4" />
          {triggerLabel}
        </Button>
      )}

      <Sheet open={open} onOpenChange={(o) => !running && setOpen(o)}>
        <SheetContent
          side="right"
          className="flex w-full flex-col gap-0 p-0 sm:max-w-xl"
          onOpenAutoFocus={(e) => {
            e.preventDefault()
            textareaRef.current?.focus()
          }}
        >
          <SheetHeader className="border-b">
            <SheetTitle className="flex items-center gap-2">
              <TriggerIcon className={cn("size-4", TONE_ICON_CLASS[tone])} />
              {title}
            </SheetTitle>
            <SheetDescription>{description}</SheetDescription>
          </SheetHeader>

          <div className="flex flex-1 min-h-0 flex-col gap-2 overflow-auto p-4">
            <Textarea
              ref={textareaRef}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder={placeholder}
              className="min-h-70 flex-1 resize-none"
              disabled={running}
            />
            {hint && (
              <p className="shrink-0 text-xs text-muted-foreground">{hint}</p>
            )}
          </div>

          <SheetFooter className="flex-row justify-end gap-2 border-t bg-background p-4">
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
              disabled={running}
            >
              {t("common.cancel")}
            </Button>
            <Button
              type="button"
              onClick={handleRun}
              disabled={running || !draft.trim() || !defaultLlmId}
              className="gap-2"
            >
              {running ? (
                <IconLoader2 className="size-4 animate-spin" />
              ) : (
                <IconSparkles className="size-4" />
              )}
              {generateLabel}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </>
  )
}

function identity(s: string): string {
  return s
}

function defaultBuildUserMessage({ value, brief }: { value: string; brief: string; maxLength?: number }): string {
  return [
    value.trim()
      ? `Current text:\n${value}\n`
      : "There is no current text yet.",
    "User instructions (apply them strictly):",
    brief,
    "",
    "Return ONLY the resulting text, with no preamble, quotes, or commentary.",
  ].join("\n")
}
