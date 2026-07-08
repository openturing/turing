"use client"
import { useUpdateAiAgent } from "@/api/queries/ai-agent.queries"
import { Switch } from "@/components/ui/switch"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts"
import type { TurCapabilityDescriptor, TurCapabilityProvider } from "@/models/genai/capability.model.ts"
import { TurCapabilityRegistryService } from "@/services/genai/capability.service"
import { IconAdjustments, IconLock } from "@tabler/icons-react"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { BentoFormSection } from "../bento"
import { GradientButton } from "../ui/gradient-button"
import { SectionCard } from "../ui/section-card"

const capabilityService = new TurCapabilityRegistryService();

interface Props {
  value: TurAIAgent;
  /** Render inside the bento shell (frosted BentoFormSection) instead of the console SectionCard. */
  chrome?: "console" | "bento";
}

function providerLabel(provider: TurCapabilityProvider): string {
  switch (provider) {
    case "OPENAI": return "OpenAI";
    case "ANTHROPIC": return "Anthropic";
    case "TURING": return "Turing";
    default: return "Any";
  }
}

/** Parse the agent's requestOptionsJson into a string-keyed map (tolerant of bad JSON). */
function parseOptions(json?: string | null): Record<string, string> {
  if (!json) return {};
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>;
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(parsed)) {
      if (v !== null && v !== undefined) out[k] = String(v);
    }
    return out;
  } catch {
    return {};
  }
}

/** Serialize the map back to JSON, dropping empty values so cleared knobs don't linger. */
function serializeOptions(map: Record<string, string>): string {
  const cleaned: Record<string, string> = {};
  for (const [k, v] of Object.entries(map)) {
    if (v !== "" && v !== "false") cleaned[k] = v;
  }
  return JSON.stringify(cleaned);
}

export const AIAgentRequestOptionsForm: React.FC<Props> = ({ value, chrome = "console" }) => {
  const { t } = useTranslation();
  const isBento = chrome === "bento";
  const [descriptors, setDescriptors] = useState<TurCapabilityDescriptor[]>([]);
  const [options, setOptions] = useState<Record<string, string>>({});
  const [savedJson, setSavedJson] = useState<string>("");
  const updateMutation = useUpdateAiAgent();

  useEffect(() => {
    capabilityService.query().then((all) =>
      setDescriptors(all.filter((d) => d.kind === "REQUEST_OPTION")));
  }, []);

  useEffect(() => {
    const parsed = parseOptions(value.requestOptionsJson);
    setOptions(parsed);
    setSavedJson(serializeOptions(parsed));
  }, [value]);

  const agentProviders = useMemo(() => {
    const set = new Set<string>();
    for (const instance of value.llmInstances ?? []) {
      const vendorId = instance.turLLMVendor?.id;
      if (vendorId) set.add(vendorId.toUpperCase());
    }
    return set;
  }, [value.llmInstances]);

  /** REQUEST_OPTIONs grouped by category for visual layout. */
  const categories = useMemo(() => {
    const byCategory = new Map<string, TurCapabilityDescriptor[]>();
    for (const d of descriptors) {
      const list = byCategory.get(d.category) ?? [];
      list.push(d);
      byCategory.set(d.category, list);
    }
    return Array.from(byCategory.entries())
      .map(([category, list]) => ({ category, list }))
      .sort((a, b) => a.category.localeCompare(b.category));
  }, [descriptors]);

  function available(d: TurCapabilityDescriptor): boolean {
    if (d.provider === "TURING" || d.provider === "ANY") return true;
    return agentProviders.has(d.provider);
  }

  function setValue(key: string, v: string) {
    setOptions((prev) => {
      const next = { ...prev };
      if (v === "" || v === "false") delete next[key];
      else next[key] = v;
      return next;
    });
  }

  const currentJson = serializeOptions(options);
  const isDirty = currentJson !== savedJson;

  async function onSave() {
    const payload: TurAIAgent = { ...value, requestOptionsJson: currentJson };
    try {
      const result = await updateMutation.mutateAsync(payload);
      if (result) {
        toast.success(t("forms.agentRequestOptions.updated"));
        setSavedJson(currentJson);
      } else {
        toast.error(t("forms.agentRequestOptions.updateFailed"));
      }
    } catch (error) {
      console.error("Save error", error);
      toast.error(t("forms.agentRequestOptions.updateFailed"));
    }
  }

  function onReset() {
    setOptions(parseOptions(savedJson));
  }

  const Section = ({ title, description, children }: {
    readonly title: string; readonly description?: string; readonly children: React.ReactNode;
  }) =>
    isBento ? (
      <BentoFormSection icon={IconAdjustments} tone="blue" title={title} description={description}>
        {children}
      </BentoFormSection>
    ) : (
      <SectionCard variant="blue">
        <SectionCard.Header icon={IconAdjustments} title={title} description={description} />
        <SectionCard.Content>{children}</SectionCard.Content>
      </SectionCard>
    );

  return (
    <TooltipProvider>
      <div className={isBento ? "space-y-4 md:space-y-5" : "px-6"}>
        <div className={isBento ? "space-y-4 md:space-y-5" : "space-y-4 py-8"}>
          {categories.length === 0 ? (
            <Section title={t("forms.agentRequestOptions.title")}
              description={t("forms.agentRequestOptions.description")}>
              <div className="text-center py-8">
                <IconAdjustments className="mx-auto size-10 text-muted-foreground/30 mb-3" />
                <p className="text-sm text-muted-foreground">{t("forms.agentRequestOptions.empty")}</p>
              </div>
            </Section>
          ) : (
            categories.map(({ category, list }) => (
              <Section key={category} title={t(`forms.agentRequestOptions.categories.${category}`, { defaultValue: category })}
                description={t("forms.agentRequestOptions.description")}>
                  <div className="space-y-1.5">
                    {list.map((d) => {
                      const enabled = available(d);
                      const current = options[d.key] ?? "";
                      const row = (
                        <div className={`flex items-center gap-3 rounded-lg border px-3 py-2.5 ${enabled
                          ? "border-border" : "opacity-55 border-border"}`}>
                          <div className="flex flex-col min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-medium truncate">{d.label}</span>
                              <span className="text-[10px] rounded-full bg-slate-100 text-slate-500 border border-slate-200 px-1.5 py-0.5 dark:bg-slate-800 dark:border-slate-700 dark:text-slate-400">
                                {providerLabel(d.provider)}
                              </span>
                            </div>
                            {d.description && (
                              <span className="text-xs text-muted-foreground truncate">{d.description}</span>
                            )}
                          </div>
                          {!enabled && <IconLock className="size-4 text-muted-foreground/50 shrink-0" />}
                          {enabled && d.valueType === "BOOLEAN" && (
                            <Switch
                              checked={current === "true"}
                              onCheckedChange={(c) => setValue(d.key, c ? "true" : "false")}
                            />
                          )}
                          {enabled && d.valueType === "SELECT" && (
                            <Select value={current || "__none__"}
                              onValueChange={(v) => setValue(d.key, v === "__none__" ? "" : v)}>
                              <SelectTrigger className="w-40">
                                <SelectValue placeholder={t("forms.agentRequestOptions.off")} />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value="__none__">{t("forms.agentRequestOptions.off")}</SelectItem>
                                {(d.options ?? []).map((opt) => (
                                  <SelectItem key={opt} value={opt}>{opt}</SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          )}
                        </div>
                      );
                      if (enabled) return <div key={d.key}>{row}</div>;
                      return (
                        <Tooltip key={d.key}>
                          <TooltipTrigger asChild>{row}</TooltipTrigger>
                          <TooltipContent>
                            {t("forms.agentRequestOptions.vendorMismatch",
                              { provider: providerLabel(d.provider) })}
                          </TooltipContent>
                        </Tooltip>
                      );
                    })}
                  </div>
              </Section>
            ))
          )}

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
    </TooltipProvider>
  );
};
