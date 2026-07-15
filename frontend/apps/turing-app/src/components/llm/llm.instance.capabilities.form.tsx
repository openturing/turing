"use client"
import {
  useLlmInstanceCapabilities,
  useUpsertLlmInstanceCapability,
} from "@/api/queries/llm-capability.queries"
import { BentoFormSection } from "@/components/bento"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import type { TurCapabilityDescriptor } from "@/models/genai/capability.model.ts"
import type { TurLLMInstanceCapability } from "@/models/llm/llm-capability.model.ts"
import { TurCapabilityRegistryService } from "@/services/genai/capability.service"
import { IconAlertTriangle, IconSparkles } from "@tabler/icons-react"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"

const capabilityService = new TurCapabilityRegistryService();

interface Props {
  instanceId: string;
  /** The instance's vendor id (e.g. "OPENAI", "ANTHROPIC"); decides which capabilities apply. */
  vendorId?: string;
  /** When true, toggles are disabled (e.g. a GLOBAL read-only instance). */
  readOnly?: boolean;
}

/** Humanize a capability key as a label fallback, e.g. "openai-image-generation" → "Openai Image Generation". */
function humanizeKey(key: string): string {
  return key
    .split("-")
    .filter(Boolean)
    .map((token) => token.charAt(0).toUpperCase() + token.slice(1))
    .join(" ");
}

/**
 * T132 / §X.2 — the per-instance native capability matrix. This is the admin
 * gate that actually enables a provider-native capability (e.g. OpenAI
 * `image_generation`) on an LLM instance; the agent capability picker can only
 * select a capability that is enabled here. Self-contained: it loads and saves
 * through the per-instance capability API, independent of the parent form's
 * submit, so it only applies to an already-saved instance.
 *
 * <p>Renders its own frosted {@code BentoFormSection}; returns {@code null}
 * when the vendor exposes no native capabilities (e.g. Ollama), so nothing
 * shows rather than an empty card.
 */
export const LLMInstanceCapabilitiesForm: React.FC<Props> = ({ instanceId, vendorId, readOnly }) => {
  const { t } = useTranslation();
  const { data: capabilities } = useLlmInstanceCapabilities(instanceId);
  const upsertMutation = useUpsertLlmInstanceCapability(instanceId);
  const [descriptors, setDescriptors] = useState<TurCapabilityDescriptor[]>([]);

  useEffect(() => {
    capabilityService.query().then(setDescriptors).catch(() => setDescriptors([]));
  }, []);

  /** Registry descriptor keyed by capability key, for nicer labels + categories. */
  const descriptorByKey = useMemo(() => {
    const map = new Map<string, TurCapabilityDescriptor>();
    for (const d of descriptors) map.set(d.key, d);
    return map;
  }, [descriptors]);

  /** Only the capabilities that belong to this instance's vendor plugin type. */
  const vendorPlugin = vendorId?.toLowerCase();
  const rows = useMemo(
    () => (capabilities ?? []).filter((c) => c.pluginType === vendorPlugin),
    [capabilities, vendorPlugin],
  );

  /** Rows grouped by their registry category (fallback "Tools"). */
  const groups = useMemo(() => {
    const byCategory = new Map<string, TurLLMInstanceCapability[]>();
    for (const row of rows) {
      const category = descriptorByKey.get(row.key)?.category ?? "Tools";
      const list = byCategory.get(category) ?? [];
      list.push(row);
      byCategory.set(category, list);
    }
    return Array.from(byCategory.entries())
      .map(([category, list]) => ({ category, list }))
      .sort((a, b) => a.category.localeCompare(b.category));
  }, [rows, descriptorByKey]);

  // No native capabilities for this vendor (e.g. Gemini/Ollama) → nothing to show.
  if (rows.length === 0) {
    return null;
  }

  async function onToggle(row: TurLLMInstanceCapability, enabled: boolean) {
    try {
      await upsertMutation.mutateAsync({ key: row.key, enabled, configJson: row.configJson });
      toast.success(t("forms.llm.nativeCapabilityUpdated"));
    } catch (error) {
      console.error("Capability toggle error", error);
      toast.error(t("forms.llm.nativeCapabilityUpdateFailed"));
    }
  }

  return (
    <BentoFormSection
      icon={IconSparkles}
      tone="violet"
      title={t("forms.llm.nativeCapabilities")}
      description={t("forms.llm.nativeCapabilitiesDesc")}
    >
      <div className="space-y-5">
        {groups.map(({ category, list }) => (
          <div key={category} className="space-y-2">
            <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              {category}
            </span>
            <div className="space-y-1.5">
              {list.map((row) => {
                const descriptor = descriptorByKey.get(row.key);
                const label = descriptor?.label || humanizeKey(row.key);
                return (
                  <div
                    key={row.key}
                    className={`flex items-center gap-3 rounded-lg border px-3 py-2.5 transition-all ${row.enabled
                      ? "border-violet-500/40 bg-violet-500/5 dark:border-violet-400/30 dark:bg-violet-500/10"
                      : "border-border"
                      }`}
                  >
                    <div className="flex flex-col min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium truncate">{label}</span>
                        <span className="text-[10px] rounded-full bg-slate-100 text-slate-500 border border-slate-200 px-1.5 py-0.5 font-mono dark:bg-slate-800 dark:border-slate-700 dark:text-slate-400">
                          {row.key}
                        </span>
                        {descriptor?.ownsTurn && (
                          <span className="inline-flex items-center gap-1 text-[10px] rounded-full bg-rose-500/10 text-rose-600 border border-rose-500/20 px-1.5 py-0.5 dark:text-rose-400">
                            <IconAlertTriangle className="size-3" /> {t("forms.llm.nativeCapabilityOwnsTurn")}
                          </span>
                        )}
                      </div>
                    </div>
                    <GradientSwitch
                      checked={row.enabled}
                      disabled={readOnly || upsertMutation.isPending}
                      onCheckedChange={(checked) => onToggle(row, checked)}
                    />
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </BentoFormSection>
  );
};
