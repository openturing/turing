"use client"
import { Input } from "@/components/ui/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import type {
  TurLlmModelKind,
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
import { IconCheck, IconChevronDown, IconLoader2 } from "@tabler/icons-react"
import { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"

interface Props {
  value: string
  onChange: (value: string) => void
  options: TurLlmModelOption[]
  isLoading?: boolean
  source?: TurLlmModelSource
  placeholder?: string
  disabled?: boolean
  id?: string
  className?: string
  /** Called when the picker opens — parent can refetch with the latest typed key/url. */
  onOpen?: () => void
  /** The kind this surface expects; the type filter defaults to it when present. */
  expectedKind?: TurLlmModelKind
}

/**
 * Editable model picker (T577): a combobox that lists known models for the
 * chosen vendor while still accepting a free-text id the list doesn't have
 * (models in preview, private fine-tunes, etc.). Self-contained — built on the
 * design-system Popover + a filterable list, no cmdk dependency.
 */
export function ModelCombobox({
  value,
  onChange,
  options,
  isLoading = false,
  source,
  placeholder,
  disabled = false,
  id,
  className,
  onOpen,
  expectedKind,
}: Props) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState("")
  const [kindFilter, setKindFilter] = useState<ModelKindFilterValue>(expectedKind ?? "ALL")
  const [sort, setSort] = useState<ModelSortValue>("relevance")

  // Fall back to "ALL" when the chosen kind isn't present, so the list never empties.
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
  const hasExactMatch = options.some((o) => o.id === typed)
  const selectedOption = options.find((o) => o.id === value)
  const selectedLabel = selectedOption?.label ?? value

  const commit = (next: string) => {
    onChange(next)
    setOpen(false)
    setQuery("")
  }

  const handleOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) onOpen?.()
    else setQuery("")
  }

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>
        <button
          id={id}
          type="button"
          aria-haspopup="listbox"
          aria-label={t("forms.llm.modelName")}
          disabled={disabled}
          className={cn(
            "border-input bg-background ring-offset-background focus-visible:ring-ring flex h-9 w-full items-center justify-between rounded-md border px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50",
            className,
          )}
        >
          <span className="flex min-w-0 items-center gap-2">
            <span className={cn("truncate", !value && "text-muted-foreground")}>
              {value ? selectedLabel : placeholder ?? t("forms.llm.modelNamePlaceholder")}
            </span>
            {value && selectedOption && <ModelKindBadge kind={resolveModelKind(selectedOption)} />}
            {value && selectedOption?.metadata?.tier && (
              <ModelTierBadge tier={selectedOption.metadata.tier} />
            )}
          </span>
          <IconChevronDown className="ml-2 size-4 shrink-0 opacity-50" />
        </button>
      </PopoverTrigger>
      <PopoverContent
        className="w-(--radix-popper-anchor-width) min-w-56 p-0"
        align="start"
      >
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
                commit(typed)
              }
            }}
          />
          <ModelKindFilter options={options} value={effectiveFilter} onChange={setKindFilter} />
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
              onClick={() => commit(typed)}
              className="hover:bg-accent hover:text-accent-foreground flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm"
            >
              <IconCheck className="size-4 opacity-0" />
              <span className="truncate">
                {t("forms.llm.modelUseCustom", { model: typed })}
              </span>
            </button>
          )}

          {!isLoading &&
            filtered.map((option) => (
              <button
                key={option.id}
                type="button"
                onClick={() => commit(option.id)}
                className="hover:bg-accent hover:text-accent-foreground flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm"
              >
                <IconCheck
                  className={cn("size-4 shrink-0", option.id === value ? "opacity-100" : "opacity-0")}
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
            ))}

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
  )
}
