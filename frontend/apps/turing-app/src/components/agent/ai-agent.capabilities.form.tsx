"use client"
import { useUpdateAiAgent } from "@/api/queries/ai-agent.queries"
import { Switch } from "@/components/ui/switch"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts"
import type { TurCapabilityDescriptor, TurCapabilityProvider } from "@/models/genai/capability.model.ts"
import { TurCapabilityRegistryService } from "@/services/genai/capability.service"
import { IconAlertTriangle, IconCheck, IconLock, IconSparkles } from "@tabler/icons-react"
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

function parseCsv(csv?: string | null): Set<string> {
  if (!csv) return new Set();
  return new Set(csv.split(",").map((s) => s.trim()).filter(Boolean));
}

function serializeCsv(values: Set<string>): string {
  return Array.from(values).sort().join(",");
}

/** A short label for the provider chip. */
function providerLabel(provider: TurCapabilityProvider): string {
  switch (provider) {
    case "OPENAI": return "OpenAI";
    case "ANTHROPIC": return "Anthropic";
    case "TURING": return "Turing";
    default: return "Any";
  }
}

export const AIAgentCapabilitiesForm: React.FC<Props> = ({ value, chrome = "console" }) => {
  const { t } = useTranslation();
  const isBento = chrome === "bento";
  const [descriptors, setDescriptors] = useState<TurCapabilityDescriptor[]>([]);
  const [selectedTools, setSelectedTools] = useState<Set<string>>(new Set());
  const [selectedCaps, setSelectedCaps] = useState<Set<string>>(new Set());
  const [savedTools, setSavedTools] = useState<Set<string>>(new Set());
  const [savedCaps, setSavedCaps] = useState<Set<string>>(new Set());
  const updateMutation = useUpdateAiAgent();

  useEffect(() => {
    capabilityService.query().then((all) => setDescriptors(all.filter((d) => d.kind === "TOOL")));
  }, []);

  useEffect(() => {
    const tools = parseCsv(value.nativeTools);
    const caps = parseCsv(value.nativeCapabilities);
    setSelectedTools(tools);
    setSelectedCaps(caps);
    setSavedTools(tools);
    setSavedCaps(caps);
  }, [value]);

  /** The providers the agent's bound LLM instances can actually run (vendor id, upper-cased). */
  const agentProviders = useMemo(() => {
    const set = new Set<string>();
    for (const instance of value.llmInstances ?? []) {
      const vendorId = instance.turLLMVendor?.id;
      if (vendorId) set.add(vendorId.toUpperCase());
    }
    return set;
  }, [value.llmInstances]);

  /** Capabilities grouped by abstract function, in a stable category-then-function order. */
  const functionGroups = useMemo(() => {
    const byFunction = new Map<string, TurCapabilityDescriptor[]>();
    for (const d of descriptors) {
      const list = byFunction.get(d.function) ?? [];
      list.push(d);
      byFunction.set(d.function, list);
    }
    // TURING first within a function so the provider-agnostic option leads.
    const providerOrder: Record<TurCapabilityProvider, number> =
      { TURING: 0, ANY: 1, OPENAI: 2, ANTHROPIC: 3 };
    for (const list of byFunction.values()) {
      list.sort((a, b) => providerOrder[a.provider] - providerOrder[b.provider]);
    }
    return Array.from(byFunction.entries())
      .map(([fn, list]) => ({ fn, list, category: list[0].category }))
      .sort((a, b) => a.category.localeCompare(b.category) || a.fn.localeCompare(b.fn));
  }, [descriptors]);

  function descriptorAvailable(d: TurCapabilityDescriptor): boolean {
    if (d.provider === "TURING" || d.provider === "ANY") return true;
    return agentProviders.has(d.provider);
  }

  function descriptorOn(d: TurCapabilityDescriptor): boolean {
    if (d.provider === "TURING") {
      return d.toolNames.length > 0 && d.toolNames.every((n) => selectedTools.has(n));
    }
    return selectedCaps.has(d.key);
  }

  /** Remove every selection contributed by a descriptor (its tools or its cap key). */
  function clearDescriptor(d: TurCapabilityDescriptor, tools: Set<string>, caps: Set<string>) {
    if (d.provider === "TURING") {
      for (const n of d.toolNames) tools.delete(n);
    } else {
      caps.delete(d.key);
    }
  }

  /** Enable a descriptor's contribution. */
  function addDescriptor(d: TurCapabilityDescriptor, tools: Set<string>, caps: Set<string>) {
    if (d.provider === "TURING") {
      for (const n of d.toolNames) tools.add(n);
    } else {
      caps.add(d.key);
    }
  }

  /** Mutex select within a function group: clear siblings, then enable `chosen` (or none). */
  function selectInGroup(list: TurCapabilityDescriptor[], chosen: TurCapabilityDescriptor | null) {
    const tools = new Set(selectedTools);
    const caps = new Set(selectedCaps);
    for (const d of list) clearDescriptor(d, tools, caps);
    if (chosen) addDescriptor(chosen, tools, caps);
    setSelectedTools(tools);
    setSelectedCaps(caps);
  }

  function toggleSingle(d: TurCapabilityDescriptor, on: boolean) {
    const tools = new Set(selectedTools);
    const caps = new Set(selectedCaps);
    if (on) addDescriptor(d, tools, caps);
    else clearDescriptor(d, tools, caps);
    setSelectedTools(tools);
    setSelectedCaps(caps);
  }

  const isDirty = useMemo(() => {
    const eq = (a: Set<string>, b: Set<string>) =>
      a.size === b.size && [...a].every((x) => b.has(x));
    return !eq(selectedTools, savedTools) || !eq(selectedCaps, savedCaps);
  }, [selectedTools, savedTools, selectedCaps, savedCaps]);

  async function onSave() {
    // Saving from this picker opts the agent into per-capability selection mode:
    // nativeCapabilities becomes a (possibly empty) CSV, never null.
    const payload: TurAIAgent = {
      ...value,
      nativeTools: serializeCsv(selectedTools),
      nativeCapabilities: serializeCsv(selectedCaps),
    };
    try {
      const result = await updateMutation.mutateAsync(payload);
      if (result) {
        toast.success(t("forms.agentCapabilities.updated"));
        setSavedTools(new Set(selectedTools));
        setSavedCaps(new Set(selectedCaps));
      } else {
        toast.error(t("forms.agentCapabilities.updateFailed"));
      }
    } catch (error) {
      console.error("Save error", error);
      toast.error(t("forms.agentCapabilities.updateFailed"));
    }
  }

  function onReset() {
    setSelectedTools(new Set(savedTools));
    setSelectedCaps(new Set(savedCaps));
  }

  const Section = ({ children }: { readonly children: React.ReactNode }) =>
    isBento ? (
      <BentoFormSection icon={IconSparkles} tone="blue"
        title={t("forms.agentCapabilities.title")} description={t("forms.agentCapabilities.description")}>
        {children}
      </BentoFormSection>
    ) : (
      <SectionCard variant="blue">
        <SectionCard.Header
          icon={IconSparkles}
          title={t("forms.agentCapabilities.title")}
          description={t("forms.agentCapabilities.description")}
        />
        <SectionCard.Content>{children}</SectionCard.Content>
      </SectionCard>
    );

  return (
    <TooltipProvider>
      <div className={isBento ? "space-y-4 md:space-y-5" : "px-6"}>
        <div className={isBento ? "space-y-4 md:space-y-5" : "space-y-4 py-8"}>
          <Section>
              {functionGroups.length === 0 ? (
                <div className="text-center py-8">
                  <IconSparkles className="mx-auto size-10 text-muted-foreground/30 mb-3" />
                  <p className="text-sm text-muted-foreground">{t("forms.agentCapabilities.empty")}</p>
                </div>
              ) : (
                <div className="space-y-5">
                  {functionGroups.map(({ fn, list }) => {
                    const isMutex = list.length >= 2;
                    return (
                      <div key={fn} className="space-y-2">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                            {fn}
                          </span>
                          {isMutex && (
                            <span className="text-[10px] rounded-full bg-amber-500/10 text-amber-600 border border-amber-500/20 px-1.5 py-0.5 dark:text-amber-400">
                              {t("forms.agentCapabilities.mutex")}
                            </span>
                          )}
                        </div>

                        {isMutex ? (
                          <div className="space-y-1.5">
                            {list.map((d) => {
                              const available = descriptorAvailable(d);
                              const on = descriptorOn(d);
                              return (
                                <CapabilityOption
                                  key={d.key}
                                  descriptor={d}
                                  selected={on}
                                  disabled={!available}
                                  selectable
                                  onToggle={() => available && selectInGroup(list, on ? null : d)}
                                  unavailableLabel={t("forms.agentCapabilities.vendorMismatch",
                                    { provider: providerLabel(d.provider) })}
                                  ownsTurnLabel={t("forms.agentCapabilities.ownsTurn")}
                                />
                              );
                            })}
                          </div>
                        ) : (
                          list.map((d) => {
                            const available = descriptorAvailable(d);
                            const on = descriptorOn(d);
                            return (
                              <CapabilityOption
                                key={d.key}
                                descriptor={d}
                                selected={on}
                                disabled={!available}
                                onToggle={() => available && toggleSingle(d, !on)}
                                unavailableLabel={t("forms.agentCapabilities.vendorMismatch",
                                  { provider: providerLabel(d.provider) })}
                                ownsTurnLabel={t("forms.agentCapabilities.ownsTurn")}
                              />
                            );
                          })
                        )}
                      </div>
                    );
                  })}
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
    </TooltipProvider>
  );
};

interface OptionProps {
  descriptor: TurCapabilityDescriptor;
  selected: boolean;
  disabled: boolean;
  /** When true the row is one of ≥2 mutex options (radio look); else a single toggle (switch). */
  selectable?: boolean;
  onToggle: () => void;
  unavailableLabel: string;
  ownsTurnLabel: string;
}

const CapabilityOption: React.FC<OptionProps> = ({
  descriptor, selected, disabled, selectable, onToggle, unavailableLabel, ownsTurnLabel,
}) => {
  const providerText = providerLabel(descriptor.provider);
  const row = (
    <div
      className={`flex items-center gap-3 rounded-lg border px-3 py-2.5 transition-all ${disabled
        ? "opacity-55 cursor-not-allowed border-border"
        : selected
          ? "cursor-pointer border-blue-500/40 bg-blue-500/5 dark:border-blue-400/30 dark:bg-blue-500/10"
          : "cursor-pointer border-border hover:border-blue-500/20 hover:bg-accent/30"
        }`}
      onClick={() => { if (!disabled) onToggle(); }}
    >
      {selectable ? (
        <span className={`flex h-4 w-4 shrink-0 items-center justify-center rounded-full border ${selected
          ? "border-blue-600 dark:border-blue-400"
          : "border-muted-foreground/40"
          }`}>
          {selected && <span className="h-2 w-2 rounded-full bg-blue-600 dark:bg-blue-400" />}
        </span>
      ) : (
        <Switch checked={selected} disabled={disabled} onCheckedChange={() => onToggle()} />
      )}
      <div className="flex flex-col min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium truncate">{descriptor.label}</span>
          <span className="text-[10px] rounded-full bg-slate-100 text-slate-500 border border-slate-200 px-1.5 py-0.5 dark:bg-slate-800 dark:border-slate-700 dark:text-slate-400">
            {providerText}
          </span>
          {descriptor.ownsTurn && (
            <span className="inline-flex items-center gap-1 text-[10px] rounded-full bg-rose-500/10 text-rose-600 border border-rose-500/20 px-1.5 py-0.5 dark:text-rose-400">
              <IconAlertTriangle className="size-3" /> {ownsTurnLabel}
            </span>
          )}
        </div>
        {descriptor.toolNames.length > 0 && (
          <span className="text-xs text-muted-foreground truncate font-mono">
            {descriptor.toolNames.join(", ")}
          </span>
        )}
      </div>
      {disabled && <IconLock className="size-4 text-muted-foreground/50 shrink-0" />}
      {!disabled && selected && !selectable && (
        <IconCheck className="size-4 text-blue-600 dark:text-blue-400 shrink-0" />
      )}
    </div>
  );

  if (!disabled) return row;
  return (
    <Tooltip>
      <TooltipTrigger asChild>{row}</TooltipTrigger>
      <TooltipContent>{unavailableLabel}</TooltipContent>
    </Tooltip>
  );
};
