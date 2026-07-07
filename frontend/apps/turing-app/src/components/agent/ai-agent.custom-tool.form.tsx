"use client"
import { useUpdateAiAgent } from "@/api/queries/ai-agent.queries"
import { useCustomTools } from "@/api/queries/custom-tool.queries"
import { Checkbox } from "@/components/ui/checkbox"
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts"
import { IconBraces, IconCheck } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { GradientButton } from "../ui/gradient-button"
import { SectionCard } from "../ui/section-card"
import { BentoFormSection } from "../bento"

/**
 * @since 2026.2.5
 */

interface Props {
  readonly value: TurAIAgent;
  /** Render inside the bento shell (frosted BentoFormSection) instead of the console SectionCard. */
  readonly chrome?: "console" | "bento";
}

export const AIAgentCustomToolForm: React.FC<Props> = ({ value, chrome = "console" }) => {
  const { t } = useTranslation();
  const { data: tools = [] } = useCustomTools();
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set());
  const updateMutation = useUpdateAiAgent();

  useEffect(() => {
    const ids = new Set((value.customTools ?? []).map((s) => s.id));
    setSelectedIds(ids);
    setSavedIds(ids);
  }, [value]);

  const isDirty = (() => {
    if (selectedIds.size !== savedIds.size) return true;
    for (const id of selectedIds) {
      if (!savedIds.has(id)) return true;
    }
    return false;
  })();

  function toggle(id: string, checked: boolean) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  async function onSave() {
    const selected = tools.filter((s) => selectedIds.has(s.id));
    const payload: TurAIAgent = { ...value, customTools: selected };
    try {
      const result = await updateMutation.mutateAsync(payload);
      if (result) {
        toast.success(t("forms.agentCustomTool.updated"));
        setSavedIds(new Set(selectedIds));
      } else {
        toast.error(t("forms.agentCustomTool.updateFailed"));
      }
    } catch (error) {
      console.error("Save error", error);
      toast.error(t("forms.agentCustomTool.updateFailed"));
    }
  }

  function onReset() {
    setSelectedIds(new Set(savedIds));
  }

  const Section = ({ children }: { readonly children: React.ReactNode }) =>
    chrome === "bento" ? (
      <BentoFormSection icon={IconBraces} tone="rose" title={t("forms.agentCustomTool.available")} description={t("forms.agentCustomTool.availableDesc")}>
        {children}
      </BentoFormSection>
    ) : (
      <SectionCard variant="emerald">
        <SectionCard.Header
          icon={IconBraces}
          title={t("forms.agentCustomTool.available")}
          description={t("forms.agentCustomTool.availableDesc")}
        />
        <SectionCard.Content>{children}</SectionCard.Content>
      </SectionCard>
    );

  return (
    <div className={chrome === "bento" ? "space-y-4 md:space-y-5" : "px-6"}>
      <div className={chrome === "bento" ? "space-y-4 md:space-y-5" : "space-y-4 py-8"}>
        <Section>
            {tools.length > 0 ? (
              <div className="space-y-2">
                {tools.map((tool) => {
                  const isSelected = selectedIds.has(tool.id);
                  return (
                    <label
                      key={tool.id}
                      className={`flex items-center gap-4 rounded-lg border p-4 cursor-pointer transition-all ${isSelected
                        ? "border-emerald-500/40 bg-emerald-500/5 dark:border-emerald-400/30 dark:bg-emerald-500/10"
                        : "border-border hover:border-emerald-500/20 hover:bg-accent/30"
                        }`}
                    >
                      <Checkbox
                        checked={isSelected}
                        onCheckedChange={(checked) => toggle(tool.id, !!checked)}
                      />
                      <div className="flex flex-1 items-center gap-3 min-w-0">
                        <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg transition-colors ${isSelected
                          ? "bg-emerald-500/15 dark:bg-emerald-500/25"
                          : "bg-muted"
                          }`}>
                          <IconBraces className={`size-5 ${isSelected
                            ? "text-emerald-600 dark:text-emerald-400"
                            : "text-muted-foreground"
                            }`} />
                        </div>
                        <div className="flex flex-col min-w-0">
                          <span className="text-sm font-medium truncate">{tool.title}</span>
                          {tool.description && (
                            <span className="text-xs text-muted-foreground truncate">{tool.description}</span>
                          )}
                          <div className="flex items-center gap-2 mt-1">
                            <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium border ${tool.enabled === 1
                              ? "bg-emerald-500/10 text-emerald-600 border-emerald-500/20 dark:text-emerald-400"
                              : "bg-slate-500/10 text-slate-500 border-slate-500/20"
                              }`}>
                              {tool.enabled === 1 ? t("forms.common.enabled") : t("forms.common.disabled")}
                            </span>
                            <span className="text-[10px] text-muted-foreground/70 truncate">
                              {tool.returnType}
                            </span>
                          </div>
                        </div>
                      </div>
                      {isSelected && (
                        <IconCheck className="size-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
                      )}
                    </label>
                  );
                })}
              </div>
            ) : (
              <div className="text-center py-8">
                <IconBraces className="mx-auto size-10 text-muted-foreground/30 mb-3" />
                <p className="text-sm text-muted-foreground">{t("forms.agentCustomTool.noTools")}</p>
                <p className="text-xs text-muted-foreground/70 mt-1">{t("forms.agentCustomTool.createFirst")}</p>
              </div>
            )}

            {tools.length > 0 && (
              <div className="flex items-center gap-2 mt-3 pt-3 border-t">
                <div className={`flex items-center gap-1.5 px-2 py-1 rounded-full border text-[10px] font-bold transition-all ${selectedIds.size > 0
                  ? "bg-emerald-500/10 text-emerald-600 border-emerald-500/20 dark:text-emerald-400"
                  : "bg-slate-100 text-slate-400 border-slate-200 dark:bg-slate-800 dark:border-slate-700"
                  }`}>
                  <div className={`w-1.5 h-1.5 rounded-full ${selectedIds.size > 0
                    ? "bg-emerald-500 animate-pulse"
                    : "bg-slate-300 dark:bg-slate-600"
                    }`} />
                  {selectedIds.size} {t("forms.common.selected")}
                </div>
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
  );
};
