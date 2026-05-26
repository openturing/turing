"use client";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { postLlmChat, type TurChatConversationMessage } from "@viglet/turing-react-sdk";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";
import { Icon } from "@iconify/react";
import { IconLoader2, IconSearch, IconSparkles, IconTrash } from "@tabler/icons-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

const COLLECTIONS = ["lucide", "tabler", "mdi", "ph", "solar", "heroicons"];
const ICONIFY_PREFIXES = COLLECTIONS.join(",");

const globalSettingsService = new TurGlobalSettingsService();

async function searchIconify(query: string, limit: number): Promise<string[]> {
  try {
    const res = await fetch(
      `https://api.iconify.design/search?query=${encodeURIComponent(query)}&limit=${limit}&prefixes=${ICONIFY_PREFIXES}`,
    );
    if (res.ok) {
      const data = (await res.json()) as { icons?: string[] };
      return data.icons ?? [];
    }
  } catch { /* ignore */ }
  return [];
}

interface AiKeywordGroup {
  keyword: string;
  icons: string[];
}

async function resolveAiKeywords(llmResponse: string): Promise<AiKeywordGroup[]> {
  const jsonMatch = /\[[\s\S]*\]/.exec(llmResponse);
  if (!jsonMatch) return [];
  try {
    const keywords = JSON.parse(jsonMatch[0]) as string[];
    const valid = keywords.filter((s) => typeof s === "string" && s.trim()).slice(0, 5);
    const groups: AiKeywordGroup[] = [];
    for (const kw of valid) {
      const icons = await searchIconify(kw.trim(), 30);
      groups.push({ keyword: kw.trim(), icons });
    }
    return groups;
  } catch {
    return [];
  }
}

export interface IconPickerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  value?: string | null;
  /** Called when an icon is selected. Closes the dialog automatically. */
  onSelect: (icon: string) => void;
  /** Optional clear-icon callback. Renders a "Remove" button when set + an icon is selected. */
  onClear?: () => void;
  /** Title of the entity — used for AI suggestion context. */
  title?: string;
  /** Description of the entity — used for AI suggestion context. */
  description?: string;
}

/**
 * Controlled icon-picker dialog. Pulled out of `IconPicker` so multiple
 * triggers can share the same UI: the legacy preview-circle picker uses it,
 * and bento pages plug it into custom triggers (hero icon chip, etc.).
 */
export function IconPickerDialog({
  open,
  onOpenChange,
  value,
  onSelect,
  onClear,
  title,
  description,
}: Readonly<IconPickerDialogProps>) {
  const { t } = useTranslation();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(null);

  const [defaultLlmId, setDefaultLlmId] = useState<string | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiGroups, setAiGroups] = useState<AiKeywordGroup[]>([]);
  const [activeKeyword, setActiveKeyword] = useState<string | null>(null);

  useEffect(() => {
    globalSettingsService.query()
      .then((s) => setDefaultLlmId(s.defaultLlmId ?? null))
      .catch(() => setDefaultLlmId(null));
  }, []);

  const searchIcons = useCallback(async (q: string) => {
    if (!q.trim()) {
      setResults([]);
      return;
    }
    setLoading(true);
    try {
      setResults(await searchIconify(q, 60));
    } catch {
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => searchIcons(query), 350);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query, searchIcons]);

  function handleSelect(iconName: string) {
    onSelect(iconName);
    onOpenChange(false);
    setQuery("");
    setResults([]);
    setAiGroups([]);
    setActiveKeyword(null);
  }

  async function handleAiSuggest() {
    if (!defaultLlmId || aiLoading) return;
    const context = [title, description].filter(Boolean).join(" — ");
    if (!context.trim()) return;

    setAiLoading(true);
    setAiGroups([]);
    setActiveKeyword(null);

    const prompt = `Given this UI element context: "${context}"

Suggest 5 single English keywords (nouns) that best represent this concept as an icon search term.
Return ONLY a JSON array of strings, nothing else. Example: ["briefcase","calendar","target","rocket","chart"]`;

    const messages: TurChatConversationMessage[] = [{ role: "user", content: prompt }];

    try {
      const res = await postLlmChat(defaultLlmId, messages);
      const groups = await resolveAiKeywords(res.content);
      setAiGroups(groups);
      setActiveKeyword(null);
    } catch {
      // Generation failed — surface a clear empty state instead of stale results.
      setAiGroups([]);
    } finally {
      setAiLoading(false);
    }
  }

  const visibleAiIcons = activeKeyword
    ? aiGroups.find((g) => g.keyword === activeKeyword)?.icons ?? []
    : aiGroups.flatMap((g) => g.icons.slice(0, 3));

  const showAiButton = !!defaultLlmId && !!(title?.trim() || description?.trim());

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[80vh] flex-col sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t("forms.iconPicker.chooseAnIcon")}</DialogTitle>
        </DialogHeader>
        <div className="flex gap-2">
          <div className="relative flex-1">
            <IconSearch className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
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
              {aiLoading ? <IconLoader2 className="size-4 animate-spin" /> : <IconSparkles className="size-4" />}
              {t("forms.iconPicker.aiSuggest")}
            </GradientButton>
          )}
        </div>

        {aiGroups.length > 0 && (
          <div className="rounded-lg border border-blue-500/20 bg-blue-500/5 p-3">
            <div className="mb-2 flex items-center gap-1.5 text-xs font-medium text-blue-600 dark:text-blue-400">
              <IconSparkles className="size-3.5" />
              {t("forms.iconPicker.aiSuggestions")}
            </div>
            <div className="mb-3 flex flex-wrap gap-1.5">
              {aiGroups.map((g) => (
                <button
                  key={g.keyword}
                  type="button"
                  onClick={() => setActiveKeyword(activeKeyword === g.keyword ? null : g.keyword)}
                  className={`cursor-pointer rounded-full px-3 py-1 text-xs font-medium transition-all ${
                    activeKeyword === g.keyword
                      ? "bg-blue-600 text-white dark:bg-blue-500"
                      : "bg-blue-500/10 text-blue-700 hover:bg-blue-500/20 dark:text-blue-300"
                  }`}
                >
                  {g.keyword}
                  <span className="ml-1 opacity-60">{g.icons.length}</span>
                </button>
              ))}
            </div>
            <div className="flex max-h-60 flex-wrap gap-2 overflow-y-auto">
              {visibleAiIcons.map((iconName) => (
                <button
                  key={iconName}
                  type="button"
                  onClick={() => handleSelect(iconName)}
                  className={`flex cursor-pointer flex-col items-center gap-1 rounded-lg p-2 transition-all hover:bg-blue-500/10 ${
                    value === iconName
                      ? "border border-blue-500/50 bg-blue-500/15 text-blue-600 dark:text-blue-400"
                      : "border border-blue-500/20 hover:border-blue-500/40"
                  }`}
                  title={iconName}
                >
                  <Icon icon={iconName} className="size-7" />
                  <span className="max-w-[80px] truncate text-center text-[9px] leading-tight text-muted-foreground">
                    {iconName.split(":")[1] ?? iconName}
                  </span>
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="max-h-[400px] min-h-[200px] flex-1 overflow-y-auto">
          {loading && (
            <div className="flex h-32 items-center justify-center text-sm text-muted-foreground">
              {t("forms.iconPicker.searching")}
            </div>
          )}
          {!loading && query && results.length === 0 && (
            <div className="flex h-32 items-center justify-center text-sm text-muted-foreground">
              {t("forms.iconPicker.noIconsFound", { query })}
            </div>
          )}
          {!loading && !query && results.length === 0 && aiGroups.length === 0 && (
            <div className="flex h-32 flex-col items-center justify-center gap-2 px-4 text-center text-sm text-muted-foreground">
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
                  className={`flex cursor-pointer flex-col items-center justify-center gap-1 rounded-lg p-2 transition-all hover:bg-muted ${
                    value === iconName
                      ? "border border-blue-500/50 bg-blue-500/10 text-blue-600 dark:text-blue-400"
                      : "border border-transparent hover:border-border"
                  }`}
                  title={iconName}
                >
                  <Icon icon={iconName} className="size-6" />
                  <span className="w-full truncate text-center text-[9px] leading-tight text-muted-foreground">
                    {iconName.split(":")[1] ?? iconName}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>

        {value && (
          <div className="flex items-center justify-between border-t pt-2">
            <div className="flex items-center gap-2">
              <Icon icon={value} className="size-5" />
              <span className="font-mono text-xs text-muted-foreground">{value}</span>
            </div>
            <div className="flex gap-2">
              {onClear && (
                <GradientButton
                  size="sm"
                  type="button"
                  variant="outline"
                  onClick={() => {
                    onClear();
                    onOpenChange(false);
                  }}
                >
                  <IconTrash className="size-4" />
                  {t("forms.common.remove")}
                </GradientButton>
              )}
              <GradientButton size="sm" type="button" onClick={() => onOpenChange(false)}>
                {t("forms.iconPicker.done")}
              </GradientButton>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
