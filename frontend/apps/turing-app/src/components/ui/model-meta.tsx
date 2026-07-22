"use client"
import type {
  TurLlmModelMetadata,
  TurLlmModelOption,
} from "@/models/llm/llm-model-option.model.ts"
import { cn } from "@/lib/utils"
import {
  IconBolt,
  IconBulb,
  IconEye,
  IconTool,
} from "@tabler/icons-react"
import { useTranslation } from "react-i18next"

/**
 * Enriched model metadata presentation (T779): a tier badge, a compact one-line
 * summary of the catalog facts (price / context / max-output / intelligence /
 * TTFT / cutoff + capability icons), and the sort helpers that let a picker order
 * models by price, context or intelligence. Shared by {@code ModelCombobox} and
 * {@code LlmModelMultiSelect} so every model picker reads identically.
 *
 * <p>All facts come from {@link TurLlmModelMetadata} (populated only for catalog
 * models); a model surfaced by a live vendor listing has no metadata and renders
 * nothing here — never a zeroed placeholder.
 */

/** Catalog tier → colour-coded pill class (Frontier = brand blue→indigo family). */
const TIER_META: Record<string, string> = {
  Frontier: "bg-indigo-100 text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300",
  High: "bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300",
  Mid: "bg-teal-100 text-teal-700 dark:bg-teal-500/15 dark:text-teal-300",
  Light: "bg-slate-200 text-slate-700 dark:bg-slate-500/20 dark:text-slate-300",
}

/** How a picker list is ordered. */
export type ModelSortValue = "relevance" | "price" | "context" | "intelligence"

export const MODEL_SORT_VALUES: ModelSortValue[] = ["relevance", "price", "context", "intelligence"]

function formatK(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1)}M`
  if (n >= 1_000) return `${Math.round(n / 1_000)}K`
  return String(n)
}

function formatPricePart(n: number): string {
  if (n === 0) return "$0"
  if (n < 1) return `$${n.toFixed(2)}`
  return `$${n % 1 === 0 ? n : n.toFixed(2)}`
}

/** Known capability slug → icon + i18n label key, for the capability chips. */
const CAPABILITY_ICON: Record<string, { icon: typeof IconEye; labelKey: string }> = {
  vision: { icon: IconEye, labelKey: "forms.llm.modelCapVision" },
  tools: { icon: IconTool, labelKey: "forms.llm.modelCapTools" },
  reasoning: { icon: IconBulb, labelKey: "forms.llm.modelCapReasoning" },
}

/**
 * T782 — is the model end-of-life per the catalog (`deprecated` flag, or
 * `status` DEPRECATED/RETIRED)? Used to badge it in the picker.
 */
export function isDeprecatedModel(metadata?: TurLlmModelMetadata | null): boolean {
  if (!metadata) return false
  if (metadata.deprecated) return true
  const status = metadata.status?.trim().toUpperCase()
  return status === "DEPRECATED" || status === "RETIRED"
}

/** T782 — amber "Deprecated" pill for an end-of-life model in the picker. */
export function ModelDeprecatedBadge({ metadata }: Readonly<{ metadata?: TurLlmModelMetadata | null }>) {
  const { t } = useTranslation()
  if (!isDeprecatedModel(metadata)) return null
  return (
    <span className="inline-flex shrink-0 items-center rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-medium text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">
      {t("forms.llm.modelDeprecatedBadge", { defaultValue: "Deprecated" })}
    </span>
  )
}

/** Coloured tier pill (Frontier / High / Mid / Light); nothing when tier absent. */
export function ModelTierBadge({ tier, className }: Readonly<{ tier?: string | null; className?: string }>) {
  if (!tier) return null
  const cls = TIER_META[tier] ?? "bg-muted text-muted-foreground"
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium",
        cls,
        className,
      )}
    >
      {tier}
    </span>
  )
}

/**
 * A compact, wrap-friendly line of catalog facts for one model. Renders nothing
 * when the option has no metadata. A full breakdown is available via each chip's
 * native tooltip (`title`).
 */
export function ModelMetaLine({ metadata, className }: Readonly<{
  metadata?: TurLlmModelMetadata | null
  className?: string
}>) {
  const { t } = useTranslation()
  if (!metadata) return null

  const chips: React.ReactNode[] = []
  const price = metadata.pricing
  if (price && (price.inputPer1M != null || price.outputPer1M != null)) {
    const inTxt = price.inputPer1M != null ? formatPricePart(price.inputPer1M) : "?"
    const outTxt = price.outputPer1M != null ? formatPricePart(price.outputPer1M) : "?"
    chips.push(
      <span key="price" title={t("forms.llm.modelMetaPrice", { input: inTxt, output: outTxt })}>
        {inTxt}/{outTxt}
      </span>,
    )
  }
  if (metadata.contextWindow != null) {
    chips.push(
      <span key="ctx" title={t("forms.llm.modelMetaContext", { tokens: metadata.contextWindow.toLocaleString() })}>
        {formatK(metadata.contextWindow)} ctx
      </span>,
    )
  }
  if (metadata.maxOutputTokens != null) {
    chips.push(
      <span key="out" title={t("forms.llm.modelMetaMaxOut", { tokens: metadata.maxOutputTokens.toLocaleString() })}>
        {formatK(metadata.maxOutputTokens)} out
      </span>,
    )
  }
  const ii = metadata.benchmarks?.intelligenceIndex
  if (ii != null) {
    chips.push(
      <span key="ii" title={t("forms.llm.modelMetaIntelligence", { value: ii })}>
        II {Math.round(ii)}
      </span>,
    )
  }
  const ttft = metadata.performance?.latencyTtftSec
  if (ttft != null) {
    chips.push(
      <span key="ttft" className="inline-flex items-center gap-0.5"
        title={t("forms.llm.modelMetaTtft", { seconds: ttft })}>
        <IconBolt className="size-3" />{ttft}s
      </span>,
    )
  }
  if (metadata.knowledgeCutoff) {
    chips.push(
      <span key="cutoff" title={t("forms.llm.modelMetaCutoff", { date: metadata.knowledgeCutoff })}>
        {metadata.knowledgeCutoff}
      </span>,
    )
  }

  const caps = (metadata.capabilities ?? [])
    .map((c) => c.toLowerCase())
    .filter((c) => CAPABILITY_ICON[c])

  if (chips.length === 0 && caps.length === 0) return null

  return (
    <span className={cn("text-muted-foreground flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[10px]", className)}>
      {chips}
      {caps.map((c) => {
        const { icon: Icon, labelKey } = CAPABILITY_ICON[c]
        return <Icon key={c} className="size-3" title={t(labelKey)} />
      })}
    </span>
  )
}

/** True when at least one option carries metadata worth sorting/enriching by. */
export function hasEnrichedMetadata(options: TurLlmModelOption[]): boolean {
  return options.some((o) => o.metadata != null)
}

/**
 * Compact sort control for a model picker. Hidden when no option carries catalog
 * metadata (a live-vendor list has nothing to sort by), so live-only pickers stay
 * uncluttered. A native `<select>` — reliable inside the picker's Popover.
 */
export function ModelSortSelect({ options, value, onChange, className }: Readonly<{
  options: TurLlmModelOption[]
  value: ModelSortValue
  onChange: (value: ModelSortValue) => void
  className?: string
}>) {
  const { t } = useTranslation()
  if (!hasEnrichedMetadata(options)) return null
  return (
    <label className={cn("text-muted-foreground flex items-center gap-1.5 text-xs", className)}>
      <span className="shrink-0">{t("forms.llm.modelSortLabel")}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value as ModelSortValue)}
        className="border-input bg-background h-7 flex-1 rounded-md border px-1.5 text-xs focus-visible:outline-none"
      >
        {MODEL_SORT_VALUES.map((v) => (
          <option key={v} value={v}>{t(`forms.llm.modelSort_${v}`)}</option>
        ))}
      </select>
    </label>
  )
}

// T781 — catalog `modalities.input` slugs that mean the model accepts a
// file/image on input, so file-upload can safely be enabled.
const FILE_INPUT_MODALITIES = new Set(["image", "file", "pdf", "document", "audio", "video"])

/**
 * T781 — does the catalog say the model supports tool calling? `undefined` when
 * unknown (no metadata, or the entry lists no capabilities) so callers can avoid
 * warning on a guess — only on positive evidence the model lacks it.
 */
export function catalogSupportsTools(meta?: TurLlmModelMetadata | null): boolean | undefined {
  if (!meta?.capabilities?.length) return undefined
  return meta.capabilities.some((c) => c.toLowerCase() === "tools")
}

/**
 * T781 — does the catalog say the model can accept file/image input (the `vision`
 * capability or a file-like input modality)? `undefined` when the entry carries
 * neither capabilities nor modalities (unknown → no warning).
 */
export function catalogSupportsFileInput(meta?: TurLlmModelMetadata | null): boolean | undefined {
  if (!meta) return undefined
  const caps = (meta.capabilities ?? []).map((c) => c.toLowerCase())
  const inputs = (meta.modalities?.input ?? []).map((m) => m.toLowerCase())
  if (caps.length === 0 && inputs.length === 0) return undefined
  return caps.includes("vision") || inputs.some((m) => FILE_INPUT_MODALITIES.has(m))
}

/** Numeric key a sort reads from an option, or `undefined` when unknown. */
function sortKey(option: TurLlmModelOption, sort: ModelSortValue): number | undefined {
  const m = option.metadata
  if (!m) return undefined
  switch (sort) {
    case "price":
      return m.pricing?.inputPer1M ?? undefined
    case "context":
      return m.contextWindow ?? undefined
    case "intelligence":
      return m.benchmarks?.intelligenceIndex ?? undefined
    default:
      return undefined
  }
}

/**
 * Returns a new list ordered by the chosen sort. `relevance` keeps the incoming
 * order. `price` is ascending (cheapest first); `context` and `intelligence` are
 * descending (biggest/best first). Options missing the metric always sink to the
 * end so the list never hides a model, and ties keep their original order.
 */
export function sortModelOptions(options: TurLlmModelOption[], sort: ModelSortValue): TurLlmModelOption[] {
  if (sort === "relevance") return options
  const descending = sort !== "price"
  return options
    .map((option, index) => ({ option, index, key: sortKey(option, sort) }))
    .sort((a, b) => {
      if (a.key == null && b.key == null) return a.index - b.index
      if (a.key == null) return 1
      if (b.key == null) return -1
      if (a.key === b.key) return a.index - b.index
      return descending ? b.key - a.key : a.key - b.key
    })
    .map((entry) => entry.option)
}
