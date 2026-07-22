"use client"
import { Input } from "@/components/ui/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import type {
  TurLlmModelKind,
  TurLlmModelMetadata,
  TurLlmModelOption,
  TurLlmModelSource,
} from "@/models/llm/llm-model-option.model.ts"
import { ModelKindBadge, resolveModelKind } from "@/components/ui/model-kind-badge"
import { ModelKindFilter, type ModelKindFilterValue } from "@/components/ui/model-kind-filter"
import {
  ModelDeprecatedBadge,
  ModelMetaLine,
  ModelSortSelect,
  ModelTierBadge,
  sortModelOptions,
  type ModelSortValue,
} from "@/components/ui/model-meta"
import { cn } from "@/lib/utils"
import {
  IconCheck,
  IconChevronDown,
  IconLoader2,
  IconPlus,
  IconStar,
  IconStarFilled,
  IconX,
} from "@tabler/icons-react"
import { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"

interface Props {
  /** Currently selected model ids. */
  selected: string[]
  /** The default model id — must be one of `selected`. */
  defaultModel: string
  /** Emits the next (selected, defaultModel) pair. */
  onChange: (selected: string[], defaultModel: string) => void
  /** Known models for the vendor (still accepts free-text ids not in the list). */
  options: TurLlmModelOption[]
  isLoading?: boolean
  source?: TurLlmModelSource
  disabled?: boolean
  id?: string
  /** Called when the add-popover opens — parent can refetch with the latest key/url. */
  onOpen?: () => void
  /** The kind this surface expects; the type filter defaults to it when present. */
  expectedKind?: TurLlmModelKind
}

/**
 * Multi-model picker for an LLM instance: select one or more models and mark
 * exactly one as the <strong>default</strong> (the single model the rest of the
 * platform consumes). Built on the design-system Popover + a filterable,
 * free-text-tolerant list — the multi-select sibling of {@link ModelCombobox}.
 *
 * <p>Selected models render as rows with a star toggle (set default), a
 * "Default" badge on the active one, and a remove button. Removing the default
 * promotes the first remaining model so a non-empty selection always has a
 * default.</p>
 */
export function LlmModelMultiSelect({
  selected,
  defaultModel,
  onChange,
  options,
  isLoading = false,
  source,
  disabled = false,
  id,
  onOpen,
  expectedKind,
}: Props) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState("")
  const [kindFilter, setKindFilter] = useState<ModelKindFilterValue>(expectedKind ?? "ALL")
  const [sort, setSort] = useState<ModelSortValue>("relevance")

  const labelFor = (modelId: string) =>
    options.find((o) => o.id === modelId)?.label ?? modelId

  const kindFor = (modelId: string): TurLlmModelKind => {
    const opt = options.find((o) => o.id === modelId)
    return opt ? resolveModelKind(opt) : "UNKNOWN"
  }

  const metaFor = (modelId: string): TurLlmModelMetadata | null =>
    options.find((o) => o.id === modelId)?.metadata ?? null

  // If the chosen kind isn't present in the current options, fall back to "ALL"
  // so the list is never silently empty (e.g. an all-chat vendor on a chat surface).
  const effectiveFilter = useMemo<ModelKindFilterValue>(() => {
    if (kindFilter === "ALL") return "ALL"
    return options.some((o) => resolveModelKind(o) === kindFilter) ? kindFilter : "ALL"
  }, [options, kindFilter])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    const matched = options.filter((o) => {
      if (effectiveFilter !== "ALL" && resolveModelKind(o) !== effectiveFilter) return false
      if (!q) return true
      return o.id.toLowerCase().includes(q) || o.label.toLowerCase().includes(q)
    })
    return sortModelOptions(matched, sort)
  }, [options, query, effectiveFilter, sort])

  const typed = query.trim()
  const hasExactMatch = options.some((o) => o.id === typed) || selected.includes(typed)

  const emit = (nextSelected: string[], nextDefault: string) => {
    // A non-empty selection must always carry a valid default.
    const fallback = nextSelected.includes(nextDefault) ? nextDefault : nextSelected[0] ?? ""
    onChange(nextSelected, fallback)
  }

  const addModel = (modelId: string) => {
    const clean = modelId.trim()
    if (!clean) return
    if (selected.includes(clean)) {
      // Toggling an already-selected row off from the list.
      removeModel(clean)
      return
    }
    const next = [...selected, clean]
    // First model added becomes the default automatically.
    emit(next, selected.length === 0 ? clean : defaultModel)
    setQuery("")
  }

  const removeModel = (modelId: string) => {
    emit(
      selected.filter((m) => m !== modelId),
      defaultModel === modelId ? "" : defaultModel,
    )
  }

  const makeDefault = (modelId: string) => emit(selected, modelId)

  const handleOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) onOpen?.()
    else setQuery("")
  }

  return (
    <div className="grid w-full gap-2">
      {/* Add-model popover */}
      <Popover open={open} onOpenChange={handleOpenChange}>
        <PopoverTrigger asChild>
          <button
            id={id}
            type="button"
            aria-haspopup="listbox"
            aria-label={t("forms.llm.modelsAdd", { defaultValue: "Add model" })}
            disabled={disabled}
            className={cn(
              "border-input bg-background ring-offset-background focus-visible:ring-ring flex h-9 w-full items-center justify-between rounded-md border px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50",
            )}
          >
            <span className="text-muted-foreground flex items-center gap-2">
              <IconPlus className="size-4" />
              {selected.length === 0
                ? t("forms.llm.modelsAddFirst", { defaultValue: "Add a model…" })
                : t("forms.llm.modelsAddMore", { defaultValue: "Add another model…" })}
            </span>
            <IconChevronDown className="ml-2 size-4 shrink-0 opacity-50" />
          </button>
        </PopoverTrigger>
        <PopoverContent className="w-(--radix-popper-anchor-width) min-w-56 p-0" align="start">
          <div className="space-y-2 border-b p-2">
            <Input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t("forms.llm.modelSearchPlaceholder")}
              className="h-8"
              onKeyDown={(e) => {
                if (e.key === "Enter" && typed) {
                  e.preventDefault()
                  addModel(typed)
                }
              }}
            />
            <ModelKindFilter
              options={options}
              value={effectiveFilter}
              onChange={setKindFilter}
            />
            <ModelSortSelect options={options} value={sort} onChange={setSort} />
          </div>

          <div className="max-h-64 overflow-y-auto p-1">
            {isLoading && (
              <div className="text-muted-foreground flex items-center gap-2 px-2 py-3 text-sm">
                <IconLoader2 className="size-4 animate-spin" />
                {t("forms.llm.modelsLoading")}
              </div>
            )}

            {!isLoading && typed && !hasExactMatch && (
              <button
                type="button"
                onClick={() => addModel(typed)}
                className="hover:bg-accent hover:text-accent-foreground flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm"
              >
                <IconPlus className="size-4" />
                <span className="truncate">
                  {t("forms.llm.modelUseCustom", { model: typed })}
                </span>
              </button>
            )}

            {!isLoading &&
              filtered.map((option) => {
                const isSel = selected.includes(option.id)
                return (
                  <button
                    key={option.id}
                    type="button"
                    onClick={() => addModel(option.id)}
                    className="hover:bg-accent hover:text-accent-foreground flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm"
                  >
                    <IconCheck
                      className={cn("size-4 shrink-0", isSel ? "opacity-100 text-primary" : "opacity-0")}
                    />
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="truncate">{option.label}</span>
                      {option.label !== option.id && (
                        <span className="text-muted-foreground truncate text-xs">{option.id}</span>
                      )}
                      <ModelMetaLine metadata={option.metadata} />
                    </span>
                    <span className="flex shrink-0 flex-col items-end gap-0.5">
                      <ModelKindBadge kind={resolveModelKind(option)} />
                      <ModelTierBadge tier={option.metadata?.tier} />
                      <ModelDeprecatedBadge metadata={option.metadata} />
                    </span>
                  </button>
                )
              })}

            {!isLoading && filtered.length === 0 && !typed && (
              <div className="text-muted-foreground px-2 py-3 text-sm">
                {t("forms.llm.modelsEmpty")}
              </div>
            )}
          </div>

          {!isLoading && source && source !== "NONE" && (
            <div className="text-muted-foreground border-t px-3 py-1.5 text-xs">
              {source === "LIVE" ? t("forms.llm.modelsSourceLive") : t("forms.llm.modelsSourceCatalog")}
            </div>
          )}
        </PopoverContent>
      </Popover>

      {/* Selected rows */}
      {selected.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border/60 bg-card/40 px-3 py-2.5 text-sm text-muted-foreground">
          {t("forms.llm.modelsNoneSelected", {
            defaultValue: "No models selected yet — add at least one.",
          })}
        </p>
      ) : (
        <ul className="grid gap-1.5">
          {selected.map((modelId) => {
            const isDefault = modelId === defaultModel
            const label = labelFor(modelId)
            return (
              <li
                key={modelId}
                className={cn(
                  "flex items-center gap-2 rounded-lg border px-2.5 py-2 transition-colors",
                  isDefault
                    ? "border-primary/50 bg-primary/10"
                    : "border-border/60 bg-card/60",
                )}
              >
                <button
                  type="button"
                  aria-pressed={isDefault}
                  aria-label={t("forms.llm.modelsMakeDefault", {
                    defaultValue: "Set {{model}} as default",
                    model: label,
                  })}
                  onClick={() => makeDefault(modelId)}
                  className={cn(
                    "grid size-6 shrink-0 place-items-center rounded-md transition-colors",
                    isDefault
                      ? "text-amber-500"
                      : "text-muted-foreground hover:text-amber-500",
                  )}
                >
                  {isDefault ? <IconStarFilled className="size-4" /> : <IconStar className="size-4" />}
                </button>

                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="truncate text-sm font-medium">{label}</span>
                  {label !== modelId && (
                    <span className="text-muted-foreground truncate text-xs">{modelId}</span>
                  )}
                  <ModelMetaLine metadata={metaFor(modelId)} />
                </span>

                <ModelDeprecatedBadge metadata={metaFor(modelId)} />
                <ModelTierBadge tier={metaFor(modelId)?.tier} />
                <ModelKindBadge kind={kindFor(modelId)} />

                {isDefault && (
                  <span className="rounded-full bg-linear-to-br from-blue-600 to-indigo-600 px-2 py-0.5 text-[11px] font-medium text-white">
                    {t("forms.llm.modelsDefaultBadge", { defaultValue: "Default" })}
                  </span>
                )}

                <button
                  type="button"
                  aria-label={t("forms.llm.modelsRemove", { defaultValue: "Remove {{model}}", model: label })}
                  onClick={() => removeModel(modelId)}
                  disabled={disabled}
                  className="grid size-6 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                >
                  <IconX className="size-4" />
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
