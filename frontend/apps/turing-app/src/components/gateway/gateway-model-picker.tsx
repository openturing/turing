import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  IconCheck,
  IconCpu2,
  IconSearch,
  IconSparkles,
  IconWorldBolt,
  IconX,
} from "@tabler/icons-react";

import { useLlmInstances } from "@/api/queries/llm-instance.queries";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts";

/**
 * T748 follow-up / §XLIX — a visual, no-typing multi-select for a gateway key's
 * **allowed models**. Instead of a CSV text box, the operator toggles between
 * "Any model" and "Choose models", then clicks frosted, vendor-grouped model
 * cards (with live search and a selected-pills tray). The value stays a
 * comma-separated string of model names — blank = any model — so the backend
 * scope check is unchanged.
 */
export interface GatewayModelPickerProps {
  /** CSV of allowed model names; blank/empty = any model. */
  value: string;
  onChange: (csv: string) => void;
}

/** Vendor id → a tonal gradient, so each provider reads at a glance. */
const VENDOR_GRADIENT: Record<string, string> = {
  openai: "from-emerald-500 to-teal-600",
  anthropic: "from-amber-500 to-orange-600",
  gemini: "from-blue-500 to-indigo-600",
  google: "from-blue-500 to-indigo-600",
  mistral: "from-orange-500 to-red-600",
  cohere: "from-violet-500 to-purple-600",
  ollama: "from-slate-500 to-slate-700",
  azure: "from-sky-500 to-blue-600",
  bedrock: "from-amber-500 to-yellow-600",
  voyage: "from-cyan-500 to-teal-600",
};

function gradientFor(vendorId: string | undefined): string {
  const key = (vendorId ?? "").toLowerCase();
  return VENDOR_GRADIENT[key] ?? "from-indigo-500 to-blue-600";
}

function parseTokens(csv: string): string[] {
  return csv
    .split(",")
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

export function GatewayModelPicker({ value, onChange }: GatewayModelPickerProps) {
  const { t } = useTranslation();
  const { data: instances, isLoading } = useLlmInstances();
  const [query, setQuery] = useState("");
  // Explicit mode — NOT derived from the token count, so "Choose models" can show
  // the grid before anything is picked (an empty list still means "any" to the
  // backend, but the operator needs to see the grid to pick).
  const [chooseMode, setChooseMode] = useState(() => parseTokens(value).length > 0);

  const tokens = useMemo(() => parseTokens(value), [value]);

  const enabled = useMemo(
    () => (instances ?? []).filter((i) => i.enabled === 1 && i.modelName),
    [instances],
  );

  const isSelected = (instance: TurLLMInstance) =>
    tokens.some(
      (tok) =>
        tok.toLowerCase() === (instance.modelName ?? "").toLowerCase() ||
        tok.toLowerCase() === instance.id.toLowerCase(),
    );

  const emit = (nextTokens: string[]) => {
    // De-dupe case-insensitively, preserve order.
    const seen = new Set<string>();
    const deduped = nextTokens.filter((tok) => {
      const k = tok.toLowerCase();
      if (seen.has(k)) return false;
      seen.add(k);
      return true;
    });
    onChange(deduped.join(", "));
  };

  const toggle = (instance: TurLLMInstance) => {
    const token = instance.modelName ?? instance.id;
    if (isSelected(instance)) {
      emit(
        tokens.filter(
          (tok) =>
            tok.toLowerCase() !== (instance.modelName ?? "").toLowerCase() &&
            tok.toLowerCase() !== instance.id.toLowerCase(),
        ),
      );
    } else {
      emit([...tokens, token]);
    }
  };

  // Group enabled instances by vendor for the mosaic.
  const groups = useMemo(() => {
    const q = query.trim().toLowerCase();
    const map = new Map<string, { vendorTitle: string; vendorId: string; items: TurLLMInstance[] }>();
    for (const instance of enabled) {
      const matches =
        !q ||
        (instance.modelName ?? "").toLowerCase().includes(q) ||
        (instance.title ?? "").toLowerCase().includes(q) ||
        (instance.turLLMVendor?.title ?? "").toLowerCase().includes(q);
      if (!matches) continue;
      const vendorId = instance.turLLMVendor?.id ?? "other";
      const vendorTitle = instance.turLLMVendor?.title ?? vendorId;
      if (!map.has(vendorId)) {
        map.set(vendorId, { vendorTitle, vendorId, items: [] });
      }
      map.get(vendorId)!.items.push(instance);
    }
    return Array.from(map.values()).sort((a, b) => a.vendorTitle.localeCompare(b.vendorTitle));
  }, [enabled, query]);

  const chooseAny = () => {
    setChooseMode(false);
    emit([]); // blank = any
  };

  return (
    <div className="grid gap-3">
      {/* Segmented Any / Choose toggle */}
      <div className="inline-flex w-fit rounded-full border border-border/60 bg-card/60 p-1 backdrop-blur">
        <button
          type="button"
          aria-pressed={!chooseMode}
          onClick={chooseAny}
          className={`inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-sm transition-colors ${
            !chooseMode
              ? "bg-linear-to-br from-blue-600 to-indigo-600 text-white shadow-sm"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          <IconWorldBolt size={16} />
          {t("gateway.modelsAny", { defaultValue: "Any model" })}
        </button>
        <button
          type="button"
          aria-pressed={chooseMode}
          onClick={() => setChooseMode(true)}
          className={`inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-sm transition-colors ${
            chooseMode
              ? "bg-linear-to-br from-blue-600 to-indigo-600 text-white shadow-sm"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          <IconSparkles size={16} />
          {t("gateway.modelsChoose", { defaultValue: "Choose models" })}
        </button>
      </div>

      {!chooseMode ? (
        <p className="rounded-xl border border-dashed border-border/60 bg-card/40 px-4 py-3 text-sm text-muted-foreground">
          {t("gateway.modelsAnyHint", {
            defaultValue:
              "This key may call any model. Switch to “Choose models” to restrict it.",
          })}
        </p>
      ) : (
        <>
          {/* Search */}
          <div className="relative">
            <IconSearch
              size={16}
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground"
              aria-hidden
            />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t("gateway.modelsSearch", { defaultValue: "Search models…" })}
              aria-label={t("gateway.modelsSearch", { defaultValue: "Search models…" })}
              className="w-full rounded-lg border border-border/60 bg-background/70 py-2 pl-9 pr-3 text-sm outline-none focus:border-primary/50 focus:ring-1 focus:ring-primary/30"
            />
          </div>

          {/* Selected pills tray */}
          {tokens.length > 0 && (
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="text-xs font-medium text-muted-foreground">
                {t("gateway.modelsSelectedCount", {
                  defaultValue: "{{count}} selected",
                  count: tokens.length,
                })}
              </span>
              {tokens.map((tok) => (
                <span
                  key={tok}
                  className="inline-flex items-center gap-1 rounded-full border border-primary/30 bg-primary/10 py-0.5 pl-2.5 pr-1 text-xs text-foreground"
                >
                  {tok}
                  <button
                    type="button"
                    aria-label={t("gateway.modelsRemove", { defaultValue: "Remove {{model}}", model: tok })}
                    onClick={() => emit(tokens.filter((x) => x !== tok))}
                    className="grid h-4 w-4 place-items-center rounded-full text-muted-foreground hover:bg-primary/20 hover:text-foreground"
                  >
                    <IconX size={11} />
                  </button>
                </span>
              ))}
            </div>
          )}

          {/* Model mosaic grouped by vendor */}
          {isLoading ? (
            <p className="text-sm text-muted-foreground">
              {t("common.loading", { defaultValue: "Loading…" })}
            </p>
          ) : enabled.length === 0 ? (
            <p className="rounded-xl border border-dashed border-border/60 bg-card/40 px-4 py-3 text-sm text-muted-foreground">
              {t("gateway.modelsEmpty", {
                defaultValue: "No enabled LLM instances to choose from.",
              })}
            </p>
          ) : (
            <div className="grid gap-4">
              {groups.map((group) => (
                <div key={group.vendorId} className="grid gap-2">
                  <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                    {group.vendorTitle}
                  </span>
                  <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
                    {group.items.map((instance) => {
                      const selected = isSelected(instance);
                      return (
                        <button
                          key={instance.id}
                          type="button"
                          aria-pressed={selected}
                          onClick={() => toggle(instance)}
                          className={`bento-tile bento-tile-clickable group relative flex items-center gap-3 rounded-2xl border p-3 text-left transition-all ${
                            selected
                              ? "border-primary/50 bg-primary/10 shadow-sm"
                              : "border-border/60 bg-card/60 hover:border-border"
                          }`}
                        >
                          <span
                            className={`grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-linear-to-br ${gradientFor(
                              instance.turLLMVendor?.id,
                            )} text-white shadow-sm`}
                          >
                            <IconCpu2 size={18} />
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-sm font-medium">
                              {instance.modelName}
                            </span>
                            <span className="block truncate text-xs text-muted-foreground">
                              {instance.title}
                            </span>
                          </span>
                          <span
                            aria-hidden
                            className={`grid h-5 w-5 shrink-0 place-items-center rounded-full border transition-all ${
                              selected
                                ? "border-primary bg-primary text-primary-foreground scale-100"
                                : "border-border/70 bg-transparent text-transparent scale-90 group-hover:border-border"
                            }`}
                          >
                            <IconCheck size={13} />
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
              {groups.length === 0 && (
                <p className="text-sm text-muted-foreground">
                  {t("gateway.modelsNoMatch", { defaultValue: "No models match your search." })}
                </p>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
