"use client"
import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { IconLoader2, IconSparkles, IconWand } from "@tabler/icons-react"

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
import { TurChatService, type ChatMessageItem } from "@/services/chat/chat.service"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"

/**
 * "Vibe coding" trigger for the Groovy code editor in the Custom Tool form.
 * Mirrors the SmartDescription meta-prompt sheet but streams its output
 * straight into the script field. The {@code fieldInstruction} is sent as
 * a system message so the LLM knows the runtime contract (bindings, return
 * coercion, no markdown fences) regardless of what the user typed in the
 * brief.
 *
 * @since 2026.2.5
 */

const chatService = new TurChatService()
const globalSettingsService = new TurGlobalSettingsService()

interface Props {
  readonly value: string
  readonly onChange: (value: string) => void
  readonly metaPrompt?: string | null
  readonly onMetaPromptChange?: (value: string) => void
  readonly fieldInstruction: string
  readonly disabled?: boolean
}

export function VibeCodingButton({
  value,
  onChange,
  metaPrompt,
  onMetaPromptChange,
  fieldInstruction,
  disabled,
}: Props) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState(metaPrompt ?? "")
  const [running, setRunning] = useState(false)
  const [defaultLlmId, setDefaultLlmId] = useState<string | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    globalSettingsService.query()
      .then((s) => setDefaultLlmId(s.defaultLlmId ?? null))
      .catch(() => setDefaultLlmId(null))
  }, [])

  // Re-sync local draft when the parent value changes (e.g. form reset)
  useEffect(() => {
    setDraft(metaPrompt ?? "")
  }, [metaPrompt])

  if (!onMetaPromptChange) return null

  async function handleRun() {
    if (!defaultLlmId || running) return
    const brief = draft.trim()
    if (!brief) return
    setRunning(true)
    onMetaPromptChange?.(draft)

    const userContent = [
      value.trim()
        ? `Current Groovy script:\n${value}\n`
        : "There is no current script yet — generate one from scratch.",
      "User instructions (apply them strictly):",
      brief,
      "",
      "Return ONLY the resulting Groovy source code, with NO markdown code fences, NO preamble, and NO commentary.",
    ].filter(Boolean).join("\n")

    const messages: ChatMessageItem[] = [
      { role: "system", content: fieldInstruction },
      { role: "user", content: userContent },
    ]

    let result = ""
    await chatService.sendStream(
      defaultLlmId,
      messages,
      (token) => {
        result += token
        onChange(stripCodeFences(result))
      },
      () => { setRunning(false); setOpen(false) },
      () => { setRunning(false) },
    )
  }

  return (
    <>
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={() => setOpen(true)}
        disabled={disabled || !defaultLlmId}
        className={cn(
          "gap-2 border-fuchsia-500/40 text-fuchsia-600 dark:text-fuchsia-300",
          "hover:border-fuchsia-500/60 hover:bg-fuchsia-500/10",
          metaPrompt?.trim() && "bg-fuchsia-500/5",
        )}
      >
        <IconWand className="size-4" />
        {t("forms.customTool.vibeCoding")}
      </Button>

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
              <IconWand className="size-4 text-fuchsia-500" />
              {t("forms.customTool.vibeCodingTitle")}
            </SheetTitle>
            <SheetDescription>
              {t("forms.customTool.vibeCodingDescription")}
            </SheetDescription>
          </SheetHeader>

          <div className="flex flex-1 min-h-0 flex-col gap-2 overflow-auto p-4">
            <Textarea
              ref={textareaRef}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder={t("forms.customTool.vibeCodingPlaceholder")}
              className="min-h-70 flex-1 resize-none"
              disabled={running}
            />
            <p className="shrink-0 text-xs text-muted-foreground">
              {t("forms.customTool.vibeCodingHint")}
            </p>
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
              {t("forms.customTool.vibeCodingGenerate")}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </>
  )
}

/**
 * LLMs sometimes return ```groovy fences despite the system prompt; strip
 * them defensively so the editor never displays them. Run on every chunk
 * so the streamed output looks clean from the first token.
 */
function stripCodeFences(text: string): string {
  return text
    .replace(/^```(?:groovy|java)?\s*\n?/i, "")
    .replace(/\n?```\s*$/i, "")
}
