"use client"
import { useTranslation } from "react-i18next"
import { IconWand } from "@tabler/icons-react"

import { MetaPromptSheet } from "@/components/ui/meta-prompt-sheet"

/**
 * "Vibe coding" trigger for the Groovy code editor in the Custom Tool form.
 * Thin preset over {@link MetaPromptSheet}: button-style fuchsia trigger,
 * code-aware user message template, defensive code-fence stripping on the
 * streamed output.
 *
 * @since 2026.2.5
 */

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

  return (
    <MetaPromptSheet
      value={value}
      onChange={onChange}
      metaPrompt={metaPrompt}
      onMetaPromptChange={onMetaPromptChange}
      fieldInstruction={fieldInstruction}
      triggerVariant="button"
      tone="fuchsia"
      triggerIcon={IconWand}
      triggerLabel={t("forms.customTool.vibeCoding")}
      title={t("forms.customTool.vibeCodingTitle")}
      description={t("forms.customTool.vibeCodingDescription")}
      placeholder={t("forms.customTool.vibeCodingPlaceholder")}
      hint={t("forms.customTool.vibeCodingHint")}
      generateLabel={t("forms.customTool.vibeCodingGenerate")}
      buildUserMessage={buildCodeUserMessage}
      postProcess={stripCodeFences}
      disabled={disabled}
    />
  )
}

function buildCodeUserMessage({ value, brief }: { value: string; brief: string }): string {
  return [
    value.trim()
      ? `Current Groovy script:\n${value}\n`
      : "There is no current script yet — generate one from scratch.",
    "User instructions (apply them strictly):",
    brief,
    "",
    "Return ONLY the resulting Groovy source code, with NO markdown code fences, NO preamble, and NO commentary.",
  ].join("\n")
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
