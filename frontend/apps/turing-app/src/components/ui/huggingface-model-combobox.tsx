"use client"
import { Input } from "@/components/ui/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import type { TurHuggingFaceModelOption, TurHuggingFaceModelSource } from "@/models/embedding/huggingface-model.model.ts"
import { cn } from "@/lib/utils"
import { IconCheck, IconChevronDown, IconDownload, IconLoader2, IconRosetteDiscountCheck } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

interface Props {
  value: string
  onChange: (value: string) => void
  options: TurHuggingFaceModelOption[]
  isLoading?: boolean
  source?: TurHuggingFaceModelSource
  /** Server-side search: called (debounced) as the user types. */
  onQueryChange?: (query: string) => void
  placeholder?: string
  disabled?: boolean
  id?: string
  className?: string
  /** Called when the picker opens — parent can refetch. */
  onOpen?: () => void
}

function formatDownloads(n?: number | null): string | null {
  if (n == null) return null
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return String(n)
}

/**
 * Searchable HuggingFace embedding-model picker (T626). Like the LLM
 * {@code ModelCombobox} (T577) but oriented to repo ids and their metadata
 * (downloads, dimensions, ONNX-verified check) with server-side search. Stays
 * editable so an unlisted repo id can still be typed (air-gapped mirrors,
 * private repos, brand-new models).
 */
export function HuggingFaceModelCombobox({
  value,
  onChange,
  options,
  isLoading = false,
  source,
  onQueryChange,
  placeholder,
  disabled = false,
  id,
  className,
  onOpen,
}: Props) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState("")

  // Debounce the typed query up to the server-side search.
  useEffect(() => {
    const handle = setTimeout(() => onQueryChange?.(query.trim()), 300)
    return () => clearTimeout(handle)
  }, [query, onQueryChange])

  const typed = query.trim()
  const hasExactMatch = options.some((o) => o.repoId === typed)

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
          aria-label={t("forms.embedding.hfModel")}
          disabled={disabled}
          className={cn(
            "border-input bg-background ring-offset-background focus-visible:ring-ring flex h-9 w-full items-center justify-between rounded-md border px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50",
            className,
          )}
        >
          <span className={cn("truncate", !value && "text-muted-foreground")}>
            {value || (placeholder ?? t("forms.embedding.hfModelPlaceholder"))}
          </span>
          <IconChevronDown className="ml-2 size-4 shrink-0 opacity-50" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-(--radix-popper-anchor-width) min-w-72 p-0" align="start">
        <div className="border-b p-2">
          <Input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t("forms.embedding.hfSearchPlaceholder")}
            className="h-8"
            onKeyDown={(e) => {
              if (e.key === "Enter" && typed) {
                e.preventDefault()
                commit(typed)
              }
            }}
          />
        </div>

        <div className="max-h-72 overflow-y-auto p-1">
          {isLoading && (
            <div className="text-muted-foreground flex items-center gap-2 px-2 py-3 text-sm">
              <IconLoader2 className="size-4 animate-spin" />
              {t("forms.embedding.hfModelsLoading")}
            </div>
          )}

          {!isLoading && typed && !hasExactMatch && (
            <button
              type="button"
              onClick={() => commit(typed)}
              className="hover:bg-accent hover:text-accent-foreground flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm"
            >
              <IconCheck className="size-4 opacity-0" />
              <span className="truncate">{t("forms.embedding.hfUseCustom", { repo: typed })}</span>
            </button>
          )}

          {!isLoading &&
            options.map((option) => {
              const downloads = formatDownloads(option.downloads)
              return (
                <button
                  key={option.repoId}
                  type="button"
                  onClick={() => commit(option.repoId)}
                  className="hover:bg-accent hover:text-accent-foreground flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm"
                >
                  <IconCheck
                    className={cn("size-4 shrink-0", option.repoId === value ? "opacity-100" : "opacity-0")}
                  />
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="truncate">{option.repoId}</span>
                    <span className="text-muted-foreground flex items-center gap-2 text-xs">
                      {option.onnxVerified && (
                        <span className="text-emerald-600 dark:text-emerald-400 inline-flex items-center gap-0.5">
                          <IconRosetteDiscountCheck className="size-3" />
                          {t("forms.embedding.hfOnnxVerified")}
                        </span>
                      )}
                      {option.dimensions != null && <span>{option.dimensions} dim</span>}
                      {downloads && (
                        <span className="inline-flex items-center gap-0.5">
                          <IconDownload className="size-3" />
                          {downloads}
                        </span>
                      )}
                    </span>
                  </span>
                </button>
              )
            })}

          {!isLoading && options.length === 0 && !typed && (
            <div className="text-muted-foreground px-2 py-3 text-sm">
              {t("forms.embedding.hfModelsEmpty")}
            </div>
          )}
        </div>

        {!isLoading && source && source !== "NONE" && (
          <div className="text-muted-foreground border-t px-3 py-1.5 text-xs">
            {source === "LIVE" ? t("forms.embedding.hfSourceLive") : t("forms.embedding.hfSourceCatalog")}
          </div>
        )}
      </PopoverContent>
    </Popover>
  )
}
