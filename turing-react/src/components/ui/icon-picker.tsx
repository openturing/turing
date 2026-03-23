"use client"
import { useCallback, useEffect, useRef, useState } from "react"
import { Icon } from "@iconify/react"
import { IconLoader2, IconPalette, IconSearch, IconSparkles, IconTrash } from "@tabler/icons-react"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { GradientButton } from "@/components/ui/gradient-button"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import { TurChatService, type ChatMessageItem } from "@/services/chat/chat.service"
import { useTranslation } from "react-i18next"

interface IconPickerProps {
  value?: string | null
  onChange: (icon: string) => void
  onRemove?: () => void
  /** Title of the entity — used for AI suggestion context */
  title?: string
  /** Description of the entity — used for AI suggestion context */
  description?: string
  children?: React.ReactNode
}


const COLLECTIONS = ["lucide", "tabler", "mdi", "ph", "solar", "heroicons"]
const ICONIFY_PREFIXES = COLLECTIONS.join(",")

const globalSettingsService = new TurGlobalSettingsService()
const chatService = new TurChatService()

async function searchIconify(query: string, limit: number): Promise<string[]> {
  try {
    const res = await fetch(
      `https://api.iconify.design/search?query=${encodeURIComponent(query)}&limit=${limit}&prefixes=${ICONIFY_PREFIXES}`
    )
    if (res.ok) {
      const data = await res.json() as { icons?: string[] }
      return data.icons ?? []
    }
  } catch { /* ignore */ }
  return []
}

interface AiKeywordGroup {
  keyword: string
  icons: string[]
}

async function resolveAiKeywords(llmResponse: string): Promise<AiKeywordGroup[]> {
  const jsonMatch = /\[[\s\S]*\]/.exec(llmResponse)
  if (!jsonMatch) return []
  try {
    const keywords = JSON.parse(jsonMatch[0]) as string[]
    const valid = keywords.filter((s) => typeof s === "string" && s.trim()).slice(0, 5)
    const groups: AiKeywordGroup[] = []
    for (const kw of valid) {
      const icons = await searchIconify(kw.trim(), 30)
      groups.push({ keyword: kw.trim(), icons })
    }
    return groups
  } catch {
    return []
  }
}

function IconPickerComponent({ value, onChange, onRemove, title, description, children }: IconPickerProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState("")
  const [results, setResults] = useState<string[]>([])
  const [loading, setLoading] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(null)

  // AI suggestion state
  const [defaultLlmId, setDefaultLlmId] = useState<string | null>(null)
  const [aiLoading, setAiLoading] = useState(false)
  const [aiGroups, setAiGroups] = useState<AiKeywordGroup[]>([])
  const [activeKeyword, setActiveKeyword] = useState<string | null>(null)

  useEffect(() => {
    globalSettingsService.query()
      .then((s) => setDefaultLlmId(s.defaultLlmId ?? null))
      .catch(() => setDefaultLlmId(null))
  }, [])

  const searchIcons = useCallback(async (q: string) => {
    if (!q.trim()) {
      setResults([])
      return
    }
    setLoading(true)
    try {
      setResults(await searchIconify(q, 60))
    } catch {
      setResults([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => searchIcons(query), 350)
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [query, searchIcons])

  function handleSelect(iconName: string) {
    onChange(iconName)
    setOpen(false)
    setQuery("")
    setResults([])
    setAiGroups([])
    setActiveKeyword(null)
  }

  async function handleAiSuggest() {
    if (!defaultLlmId || aiLoading) return
    const context = [title, description].filter(Boolean).join(" — ")
    if (!context.trim()) return

    setAiLoading(true)
    setAiGroups([])
    setActiveKeyword(null)

    const prompt = `Given this UI element context: "${context}"

Suggest 5 single English keywords (nouns) that best represent this concept as an icon search term.
Return ONLY a JSON array of strings, nothing else. Example: ["briefcase","calendar","target","rocket","chart"]`

    const messages: ChatMessageItem[] = [{ role: "user", content: prompt }]
    let fullResponse = ""

    await chatService.sendStream(
      defaultLlmId,
      messages,
      (token) => { fullResponse += token },
      async () => {
        const groups = await resolveAiKeywords(fullResponse)
        setAiGroups(groups)
        setActiveKeyword(null)
        setAiLoading(false)
      },
      () => { setAiLoading(false) },
    )
  }

  // Derived: icons to show in AI section
  const visibleAiIcons = activeKeyword
    ? aiGroups.find((g) => g.keyword === activeKeyword)?.icons ?? []
    : aiGroups.flatMap((g) => g.icons.slice(0, 3))

  const showAiButton = !!defaultLlmId && !!(title?.trim() || description?.trim())

  return (
    <div className="flex items-center gap-4">
      {/* Preview circle — same style as AvatarField */}
      <div className="relative group">
        <div className="size-16 rounded-full bg-linear-to-br from-blue-600 to-indigo-600 dark:from-blue-500 dark:to-indigo-500 flex items-center justify-center shadow-sm">
          {value ? (
            <Icon icon={value} className="size-7 text-white" />
          ) : (
            <IconSearch className="size-6 text-white/60" />
          )}
        </div>
        <button
          type="button"
          title={t("forms.iconPicker.chooseIcon")}
          onClick={() => setOpen(true)}
          className="absolute inset-0 flex items-center justify-center rounded-full bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
        >
          <IconPalette className="size-5 text-white" />
        </button>
      </div>

      {/* Label + actions — same style as AvatarField */}
      <div>
        {children}
        {value ? (
          <span className="text-xs text-muted-foreground font-mono">{value}</span>
        ) : (
          <span className="text-sm text-muted-foreground">{t("forms.iconPicker.noIconSelected")}</span>
        )}
        <div className="flex items-center gap-2 mt-1">
          <button
            type="button"
            onClick={() => setOpen(true)}
            className="text-xs text-primary hover:underline cursor-pointer"
          >
            {t("forms.iconPicker.chooseIcon")}
          </button>
          {value && onRemove && (
            <button
              type="button"
              onClick={onRemove}
              className="text-xs text-destructive hover:underline cursor-pointer flex items-center gap-0.5"
            >
              <IconTrash className="size-3" />
              {t("forms.common.remove")}
            </button>
          )}
        </div>
      </div>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-lg max-h-[80vh] flex flex-col">
          <DialogHeader>
            <DialogTitle>{t("forms.iconPicker.chooseAnIcon")}</DialogTitle>
          </DialogHeader>
          <div className="flex gap-2">
            <div className="relative flex-1">
              <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder={t("forms.iconPicker.searchIcons")}
                className="pl-9"
                autoFocus
              />
            </div>
            {showAiButton && (
              <GradientButton
                type="button"
                size="sm"
                variant="outline"
                onClick={handleAiSuggest}
                disabled={aiLoading}
                title="Uses title and description to suggest relevant icons"
                className="shrink-0 gap-1.5"
              >
                {aiLoading ? (
                  <IconLoader2 className="size-4 animate-spin" />
                ) : (
                  <IconSparkles className="size-4" />
                )}
                {t("forms.iconPicker.aiSuggest")}
              </GradientButton>
            )}
          </div>

          {aiGroups.length > 0 && (
            <div className="rounded-lg border border-blue-500/20 bg-blue-500/5 p-3">
              <div className="text-xs font-medium text-blue-600 dark:text-blue-400 mb-2 flex items-center gap-1.5">
                <IconSparkles className="size-3.5" />
                {t("forms.iconPicker.aiSuggestions")}
              </div>
              <div className="flex flex-wrap gap-1.5 mb-3">
                {aiGroups.map((g) => (
                  <button
                    key={g.keyword}
                    type="button"
                    onClick={() => setActiveKeyword(activeKeyword === g.keyword ? null : g.keyword)}
                    className={`rounded-full px-3 py-1 text-xs font-medium transition-all cursor-pointer ${
                      activeKeyword === g.keyword
                        ? "bg-blue-600 text-white dark:bg-blue-500"
                        : "bg-blue-500/10 text-blue-700 dark:text-blue-300 hover:bg-blue-500/20"
                    }`}
                  >
                    {g.keyword}
                    <span className="ml-1 opacity-60">{g.icons.length}</span>
                  </button>
                ))}
              </div>
              <div className="flex flex-wrap gap-2 max-h-60 overflow-y-auto">
                {visibleAiIcons.map((iconName) => (
                  <button
                    key={iconName}
                    type="button"
                    onClick={() => handleSelect(iconName)}
                    className={`flex flex-col items-center gap-1 rounded-lg p-2 transition-all cursor-pointer hover:bg-blue-500/10 ${
                      value === iconName
                        ? "bg-blue-500/15 border border-blue-500/50 text-blue-600 dark:text-blue-400"
                        : "border border-blue-500/20 hover:border-blue-500/40"
                    }`}
                    title={iconName}
                  >
                    <Icon icon={iconName} className="size-7" />
                    <span className="text-[9px] text-muted-foreground truncate max-w-[80px] text-center leading-tight">
                      {iconName.split(":")[1] ?? iconName}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="flex-1 overflow-y-auto min-h-[200px] max-h-[400px]">
            {loading && (
              <div className="flex items-center justify-center h-32 text-muted-foreground text-sm">
                {t("forms.iconPicker.searching")}
              </div>
            )}
            {!loading && query && results.length === 0 && (
              <div className="flex items-center justify-center h-32 text-muted-foreground text-sm">
                {t("forms.iconPicker.noIconsFound", { query })}
              </div>
            )}
            {!loading && !query && results.length === 0 && aiGroups.length === 0 && (
              <div className="flex flex-col items-center justify-center h-32 text-muted-foreground text-sm gap-2 text-center px-4">
                <span>{t("forms.iconPicker.typeToSearch", { count: COLLECTIONS.length })}</span>
                {showAiButton && (
                  <span className="text-xs leading-relaxed">
                    {t("forms.iconPicker.orClickAi")}
                  </span>
                )}
              </div>
            )}
            {results.length > 0 && (
              <div className="grid grid-cols-6 gap-1.5 p-1">
                {results.map((iconName) => (
                  <button
                    key={iconName}
                    type="button"
                    onClick={() => handleSelect(iconName)}
                    className={`flex flex-col items-center justify-center gap-1 rounded-lg p-2 transition-all cursor-pointer hover:bg-muted ${
                      value === iconName
                        ? "bg-blue-500/10 border border-blue-500/50 text-blue-600 dark:text-blue-400"
                        : "border border-transparent hover:border-border"
                    }`}
                    title={iconName}
                  >
                    <Icon icon={iconName} className="size-6" />
                    <span className="text-[9px] text-muted-foreground truncate w-full text-center leading-tight">
                      {iconName.split(":")[1] ?? iconName}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </div>
          {value && (
            <div className="flex items-center justify-between pt-2 border-t">
              <div className="flex items-center gap-2">
                <Icon icon={value} className="size-5" />
                <span className="text-xs font-mono text-muted-foreground">{value}</span>
              </div>
              <GradientButton size="sm" type="button" onClick={() => setOpen(false)}>
                {t("forms.iconPicker.done")}
              </GradientButton>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}

/* ── Composite sub-components ── */

function IconPickerLabel({ children }: { children: React.ReactNode }) {
  return <div className="text-sm font-medium leading-none">{children}</div>
}

function IconPickerDescription({ children }: { children: React.ReactNode }) {
  return <p className="text-xs text-muted-foreground mt-0.5">{children}</p>
}

export const IconPicker = Object.assign(IconPickerComponent, {
  Label: IconPickerLabel,
  Description: IconPickerDescription,
})
