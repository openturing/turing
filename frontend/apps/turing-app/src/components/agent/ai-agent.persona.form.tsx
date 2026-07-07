"use client"
import { useUpdateAiAgent } from "@/api/queries/ai-agent.queries"
import { usePersonas } from "@/api/queries/persona.queries"
import { Checkbox } from "@/components/ui/checkbox"
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts"
import { IconCheck, IconUserCircle, IconStarFilled } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { BentoFormSection } from "../bento"
import { GradientButton } from "../ui/gradient-button"
import { SectionCard } from "../ui/section-card"

interface Props {
  value: TurAIAgent;
  /** Render inside the bento shell (frosted BentoFormSection) instead of the console SectionCard. */
  chrome?: "console" | "bento";
}

export const AIAgentPersonaForm: React.FC<Props> = ({ value, chrome = "console" }) => {
  const { t } = useTranslation();
  const isBento = chrome === "bento";
  const { data: personas = [] } = usePersonas();
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set());
  const [defaultId, setDefaultId] = useState<string | null>(null);
  const [savedDefaultId, setSavedDefaultId] = useState<string | null>(null);
  const updateMutation = useUpdateAiAgent();

  useEffect(() => {
    const ids = new Set((value.personas ?? []).map((p) => p.id));
    setSelectedIds(ids);
    setSavedIds(ids);
    const fallbackDefault = value.defaultPersona?.id ?? null;
    setDefaultId(fallbackDefault);
    setSavedDefaultId(fallbackDefault);
  }, [value]);

  const isDirty = (() => {
    if (defaultId !== savedDefaultId) return true;
    if (selectedIds.size !== savedIds.size) return true;
    for (const id of selectedIds) {
      if (!savedIds.has(id)) return true;
    }
    return false;
  })();

  function toggle(id: string, checked: boolean) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
        // Removing a persona that was the default means clearing the default.
        // Saving without a default is OK — agent simply has no fallback voice.
        if (defaultId === id) setDefaultId(null);
      }
      return next;
    });
  }

  function pickDefault(id: string) {
    // Selecting as default also adds it to the catalog if not already there —
    // the backend rejects a default that's outside the catalog, so this avoids
    // a confusing error and matches the user's intent.
    setSelectedIds((prev) => {
      if (prev.has(id)) return prev;
      const next = new Set(prev);
      next.add(id);
      return next;
    });
    setDefaultId(id);
  }

  async function onSave() {
    const selected = personas.filter((p) => selectedIds.has(p.id));
    const defaultPersona = defaultId ? personas.find((p) => p.id === defaultId) ?? null : null;
    const payload: TurAIAgent = {
      ...value,
      personas: selected,
      defaultPersona,
    };
    try {
      const result = await updateMutation.mutateAsync(payload);
      if (result) {
        toast.success(t("forms.agentPersona.updated"));
        setSavedIds(new Set(selectedIds));
        setSavedDefaultId(defaultId);
      } else {
        toast.error(t("forms.agentPersona.updateFailed"));
      }
    } catch (error) {
      console.error("Save error", error);
      toast.error(t("forms.agentPersona.updateFailed"));
    }
  }

  function onReset() {
    setSelectedIds(new Set(savedIds));
    setDefaultId(savedDefaultId);
  }

  const Section = ({ children }: { readonly children: React.ReactNode }) =>
    isBento ? (
      <BentoFormSection icon={IconUserCircle} tone="violet"
        title={t("forms.agentPersona.available")} description={t("forms.agentPersona.availableDesc")}>
        {children}
      </BentoFormSection>
    ) : (
      <SectionCard variant="violet">
        <SectionCard.Header
          icon={IconUserCircle}
          title={t("forms.agentPersona.available")}
          description={t("forms.agentPersona.availableDesc")}
        />
        <SectionCard.Content>{children}</SectionCard.Content>
      </SectionCard>
    );

  return (
    <div className={isBento ? "space-y-4 md:space-y-5" : "px-6"}>
      <div className={isBento ? "space-y-4 md:space-y-5" : "space-y-4 py-8"}>
        <Section>
            {personas.length > 0 ? (
              <div className="space-y-2">
                {personas.map((persona) => {
                  const isSelected = selectedIds.has(persona.id);
                  const isDefault = defaultId === persona.id;
                  return (
                    // Outer container is a plain div, NOT a label — having
                    // the "Set as default" button inside a label causes the
                    // click to bubble and toggle the checkbox, which then
                    // resets defaultId to null. The label only wraps the
                    // checkbox + persona info (the things meant to toggle
                    // selection); the default button stays a sibling.
                    <div
                      key={persona.id}
                      className={`flex items-center gap-4 rounded-lg border p-4 transition-all ${isSelected
                        ? "border-violet-500/40 bg-violet-500/5 dark:border-violet-400/30 dark:bg-violet-500/10"
                        : "border-border hover:border-violet-500/20 hover:bg-accent/30"
                        }`}
                    >
                      <label className="flex flex-1 items-center gap-4 cursor-pointer min-w-0">
                        <Checkbox
                          checked={isSelected}
                          onCheckedChange={(checked) => toggle(persona.id, !!checked)}
                        />
                        <div className="flex flex-1 items-center gap-3 min-w-0">
                          <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg transition-colors ${isSelected
                            ? "bg-violet-500/15 dark:bg-violet-500/25"
                            : "bg-muted"
                            }`}>
                            <IconUserCircle className={`size-5 ${isSelected
                              ? "text-violet-600 dark:text-violet-400"
                              : "text-muted-foreground"
                              }`} />
                          </div>
                          <div className="flex flex-col min-w-0">
                            <span className="text-sm font-medium truncate">{persona.name}</span>
                            {persona.description && (
                              <span className="text-xs text-muted-foreground truncate">{persona.description}</span>
                            )}
                            <span className="text-[10px] text-muted-foreground/70 mt-0.5">
                              {persona.tone ?? "—"} · v{persona.verbosity ?? 3} · {persona.languageStyle ?? "NEUTRAL"}
                            </span>
                          </div>
                        </div>
                      </label>
                      <button
                        type="button"
                        onClick={() => pickDefault(persona.id)}
                        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full border text-[11px] font-semibold shrink-0 transition-all cursor-pointer ${isDefault
                          ? "bg-amber-500/10 text-amber-700 border-amber-500/30 dark:bg-amber-500/15 dark:text-amber-300"
                          : "bg-transparent text-muted-foreground border-border hover:border-amber-500/30 hover:text-amber-600"
                          }`}
                        title={t("forms.agentPersona.setAsDefault")}
                      >
                        <IconStarFilled className="size-3" />
                        {isDefault
                          ? t("forms.agentPersona.default")
                          : t("forms.agentPersona.setAsDefault")}
                      </button>
                      {isSelected && !isDefault && (
                        <IconCheck className="size-4 text-violet-600 dark:text-violet-400 shrink-0" />
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="text-center py-8">
                <IconUserCircle className="mx-auto size-10 text-muted-foreground/30 mb-3" />
                <p className="text-sm text-muted-foreground">{t("forms.agentPersona.noPersonas")}</p>
                <p className="text-xs text-muted-foreground/70 mt-1">{t("forms.agentPersona.createFirst")}</p>
              </div>
            )}

            {personas.length > 0 && (
              <div className="flex items-center gap-2 mt-3 pt-3 border-t">
                <div className={`flex items-center gap-1.5 px-2 py-1 rounded-full border text-[10px] font-bold transition-all ${selectedIds.size > 0
                  ? "bg-violet-500/10 text-violet-600 border-violet-500/20 dark:text-violet-400"
                  : "bg-slate-100 text-slate-400 border-slate-200 dark:bg-slate-800 dark:border-slate-700"
                  }`}>
                  <div className={`w-1.5 h-1.5 rounded-full ${selectedIds.size > 0
                    ? "bg-violet-500 animate-pulse"
                    : "bg-slate-300 dark:bg-slate-600"
                    }`} />
                  {selectedIds.size} {t("forms.common.selected")}
                </div>
                {defaultId && (
                  <div className="flex items-center gap-1.5 px-2 py-1 rounded-full border text-[10px] font-bold bg-amber-500/10 text-amber-700 border-amber-500/30 dark:text-amber-300">
                    <IconStarFilled className="size-3" />
                    {personas.find((p) => p.id === defaultId)?.name ?? "—"}
                  </div>
                )}
              </div>
            )}
        </Section>

        <div className="flex items-center justify-end gap-3 pt-4 border-t">
          <GradientButton type="button" variant="outline" onClick={onReset} disabled={!isDirty}>
            {t("forms.common.reset")}
          </GradientButton>
          <GradientButton type="button" onClick={onSave} disabled={!isDirty}>
            {t("forms.common.saveChanges")}
          </GradientButton>
        </div>
      </div>
    </div>
  )
}
