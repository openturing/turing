"use client"
import type {
  TurLlmModelKind,
  TurLlmModelOption,
} from "@/models/llm/llm-model-option.model.ts"
import { cn } from "@/lib/utils"
import { useTranslation } from "react-i18next"
import { MODEL_KIND_ORDER, ModelKindDot, modelKindLabelKey, resolveModelKind } from "./model-kind-badge"

/** The active filter — a specific kind, or "ALL" for everything. */
export type ModelKindFilterValue = TurLlmModelKind | "ALL"

/** Distinct kinds present in a set of options, in canonical display order. */
export function distinctKinds(options: TurLlmModelOption[]): TurLlmModelKind[] {
  const present = new Set(options.map(resolveModelKind))
  return MODEL_KIND_ORDER.filter((k) => present.has(k))
}

/**
 * Segmented control that filters a model picker by {@link TurLlmModelKind}
 * (T751). It only renders the kinds actually present in {@code options} (plus an
 * "All" segment), and hides itself entirely when there's nothing to filter (one
 * kind or fewer) — the common single-kind case stays uncluttered.
 */
export function ModelKindFilter({
  options,
  value,
  onChange,
}: {
  options: TurLlmModelOption[]
  value: ModelKindFilterValue
  onChange: (value: ModelKindFilterValue) => void
}) {
  const { t } = useTranslation()
  const kinds = distinctKinds(options)
  if (kinds.length < 2) return null

  const segment = (
    active: boolean,
    key: string,
    label: React.ReactNode,
    onClick: () => void,
  ) => (
    <button
      key={key}
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium transition-colors",
        active
          ? "bg-background text-foreground shadow-sm"
          : "text-muted-foreground hover:text-foreground",
      )}
    >
      {label}
    </button>
  )

  return (
    <div
      role="tablist"
      aria-label={t("forms.llm.modelsFilterLabel", { defaultValue: "Filter models by type" })}
      className="flex flex-wrap items-center gap-0.5 rounded-full bg-muted p-0.5"
    >
      {segment(value === "ALL", "ALL", t("forms.llm.modelsFilterAll", { defaultValue: "All" }), () =>
        onChange("ALL"),
      )}
      {kinds.map((kind) =>
        segment(
          value === kind,
          kind,
          <>
            <ModelKindDot kind={kind} />
            {t(modelKindLabelKey(kind))}
          </>,
          () => onChange(kind),
        ),
      )}
    </div>
  )
}
