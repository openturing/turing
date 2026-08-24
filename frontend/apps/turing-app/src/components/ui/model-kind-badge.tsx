"use client"
import type { TurLlmModelKind } from "@/models/llm/llm-model-option.model.ts"
import { KIND_META } from "./model-kind-badge.utils"
import { cn } from "@/lib/utils"
import { useTranslation } from "react-i18next"

/**
 * Visual metadata for a model {@link TurLlmModelKind} (T751): a colour-coded
 * pill class + an i18n label key. The colours reuse the brand palette (CHAT =
 * blue→indigo family) with a distinct, accessible tone per kind in both light
 * and dark mode. Shared by {@code LlmModelMultiSelect} and {@code ModelCombobox}
 * so the badge + type filter look identical across every model picker.
 */




/**
 * A small, colour-coded pill naming a model's {@link TurLlmModelKind} — the
 * answer to "what am I selecting?" on every model option and selected row.
 */
export function ModelKindBadge({
  kind,
  className,
}: {
  kind: TurLlmModelKind
  className?: string
}) {
  const { t } = useTranslation()
  const meta = KIND_META[kind]
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium",
        meta.badge,
        className,
      )}
    >
      {t(meta.labelKey)}
    </span>
  )
}

/** The coloured dot used inside the type-filter segmented control. */
export function ModelKindDot({ kind }: { kind: TurLlmModelKind }) {
  return <span className={cn("size-1.5 shrink-0 rounded-full", KIND_META[kind].dot)} />
}
