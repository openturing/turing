import { useCreateLlmInstance } from "@/api/queries/llm-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BENTO_TONE_GRADIENTS, BentoHero } from "@/components/bento";
import { Button } from "@/components/ui/button";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { ModelTierBadge } from "@/components/ui/model-meta";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts";
import type {
  TurLlmAdvisorQuery,
  TurLlmModelRecommendation,
} from "@/models/llm/llm-model-advisor.model.ts";
import type { TurLLMVendor } from "@/models/llm/llm-vendor.model";
import { TurLLMInstanceService } from "@/services/llm/llm.service";
import { TurLLMVendorService } from "@/services/llm/llm-vendor.service";
import { IconLoader2, IconPlus, IconSparkles, IconWand } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

const service = new TurLLMInstanceService();
const vendorService = new TurLLMVendorService();

const CAPABILITIES = ["tools", "vision", "reasoning"] as const;
const TIERS = ["", "Light", "Mid", "High", "Frontier"] as const;

function parseNum(value: string): number | null {
  const n = Number(value.trim());
  return value.trim() && Number.isFinite(n) ? n : null;
}

/**
 * T785 — the "find me a model" advisor: describe constraints (capabilities,
 * context, budget, tier/intelligence) and get a ranked catalog shortlist, each
 * one-click into a new LLM instance. A functional bento tool page.
 */
export default function BentoLLMAdvisorPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const createMutation = useCreateLlmInstance();

  const [caps, setCaps] = useState<Set<string>>(new Set());
  const [minContext, setMinContext] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [minIntelligence, setMinIntelligence] = useState("");
  const [minTier, setMinTier] = useState("");

  const [results, setResults] = useState<TurLlmModelRecommendation[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [vendors, setVendors] = useState<TurLLMVendor[]>([]);
  const [creatingId, setCreatingId] = useState<string | null>(null);

  useEffect(() => {
    vendorService.query().then(setVendors).catch(() => setVendors([]));
  }, []);

  const gradient = BENTO_TONE_GRADIENTS.indigo;

  const toggleCap = (cap: string) =>
    setCaps((prev) => {
      const next = new Set(prev);
      if (next.has(cap)) next.delete(cap);
      else next.add(cap);
      return next;
    });

  const runAdvisor = async () => {
    setLoading(true);
    const query: TurLlmAdvisorQuery = {
      requiredCapabilities: [...caps],
      requiredInputModalities: caps.has("vision") ? ["image"] : [],
      minContextWindow: parseNum(minContext),
      maxInputPricePer1M: parseNum(maxPrice),
      minIntelligenceIndex: parseNum(minIntelligence),
      minTier: minTier || null,
      kind: "CHAT",
    };
    try {
      setResults(await service.recommendModels(query));
    } catch {
      setResults([]);
      toast.error(t("modelAdvisor.failed"));
    } finally {
      setLoading(false);
    }
  };

  const vendorFor = useMemo(
    () => (vendorId: string) =>
      vendors.find((v) => v.id.toLowerCase() === vendorId.toLowerCase()) ?? null,
    [vendors],
  );

  const createInstance = async (rec: TurLlmModelRecommendation) => {
    const vendor = vendorFor(rec.vendorId);
    if (!vendor) {
      // No matching configured vendor — fall back to the manual new-instance form.
      navigate(`${ROUTES.BENTO_LLM_INSTANCE}/new`);
      return;
    }
    setCreatingId(rec.modelId);
    try {
      const created = await createMutation.mutateAsync({
        title: rec.label,
        turLLMVendor: vendor,
        modelName: rec.modelId,
        modelNames: rec.modelId,
        enabled: 1,
      } as TurLLMInstance);
      if (created?.id) {
        toast.success(t("modelAdvisor.created", { model: rec.label }));
        navigate(`${ROUTES.BENTO_LLM_INSTANCE}/${created.id}`);
      } else {
        toast.error(t("modelAdvisor.createFailed"));
      }
    } catch {
      toast.error(t("modelAdvisor.createFailed"));
    } finally {
      setCreatingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <BentoHero
        backTo={ROUTES.BENTO_LLM_INSTANCE}
        backLabel={t("llm.title")}
        title={t("modelAdvisor.title")}
        subtitle={t("modelAdvisor.subtitle")}
        leading={
          <span className={`grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br ${gradient} text-white shadow-md`}>
            <IconWand size={24} />
          </span>
        }
      />

      <div className="mx-auto w-full max-w-5xl space-y-6 px-4 md:px-8">
        {/* Constraints */}
        <div className="bento-tile bento-glass rounded-2xl border p-5">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="md:col-span-2">
              <p className="mb-2 text-sm font-medium">{t("modelAdvisor.capabilities")}</p>
              <div className="flex flex-wrap gap-2">
                {CAPABILITIES.map((cap) => (
                  <button
                    key={cap}
                    type="button"
                    onClick={() => toggleCap(cap)}
                    aria-pressed={caps.has(cap)}
                    className={`rounded-full border px-3 py-1 text-sm transition-colors ${
                      caps.has(cap)
                        ? "border-primary/50 bg-primary/10 text-foreground"
                        : "border-border/60 bg-card/40 text-muted-foreground hover:text-foreground"
                    }`}
                  >
                    {t(`modelAdvisor.cap.${cap}`)}
                  </button>
                ))}
              </div>
            </div>
            <label className="text-sm">
              <span className="text-muted-foreground">{t("modelAdvisor.minContext")}</span>
              <Input type="number" min={0} value={minContext} placeholder="128000"
                className="mt-1" onChange={(e) => setMinContext(e.target.value)} />
            </label>
            <label className="text-sm">
              <span className="text-muted-foreground">{t("modelAdvisor.maxPrice")}</span>
              <Input type="number" min={0} step="0.01" value={maxPrice} placeholder="5"
                className="mt-1" onChange={(e) => setMaxPrice(e.target.value)} />
            </label>
            <label className="text-sm">
              <span className="text-muted-foreground">{t("modelAdvisor.minIntelligence")}</span>
              <Input type="number" min={0} value={minIntelligence} placeholder="60"
                className="mt-1" onChange={(e) => setMinIntelligence(e.target.value)} />
            </label>
            <label className="text-sm">
              <span className="text-muted-foreground">{t("modelAdvisor.minTier")}</span>
              <select value={minTier} onChange={(e) => setMinTier(e.target.value)}
                className="border-input bg-background mt-1 h-9 w-full rounded-md border px-2 text-sm focus-visible:outline-none">
                {TIERS.map((tier) => (
                  <option key={tier || "any"} value={tier}>
                    {tier || t("modelAdvisor.anyTier")}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className="mt-4 flex justify-end">
            <GradientButton onClick={runAdvisor} disabled={loading}>
              {loading ? <IconLoader2 className="mr-1 size-4 animate-spin" /> : <IconSparkles className="mr-1 size-4" />}
              {t("modelAdvisor.find")}
            </GradientButton>
          </div>
        </div>

        {/* Results */}
        {results !== null && (
          <div className="space-y-3">
            {results.length === 0 ? (
              <p className="rounded-lg border border-dashed border-border/60 bg-card/40 px-4 py-6 text-center text-sm text-muted-foreground">
                {t("modelAdvisor.noResults")}
              </p>
            ) : (
              results.map((rec) => (
                <div key={`${rec.vendorId}:${rec.modelId}`}
                  className="bento-tile bento-glass flex flex-wrap items-center gap-x-4 gap-y-2 rounded-2xl border p-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-medium">{rec.label}</span>
                      <ModelTierBadge tier={rec.tier} />
                      <span className="text-muted-foreground text-xs">{rec.vendorId}</span>
                    </div>
                    <div className="text-muted-foreground mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-xs">
                      {rec.inputPricePer1M != null && <span>${rec.inputPricePer1M}/1M in</span>}
                      {rec.contextWindow != null && <span>{Math.round(rec.contextWindow / 1000)}K ctx</span>}
                      {rec.intelligenceIndex != null && <span>II {Math.round(rec.intelligenceIndex)}</span>}
                      {rec.capabilities.length > 0 && <span>{rec.capabilities.join(" · ")}</span>}
                    </div>
                  </div>
                  {vendorFor(rec.vendorId) ? (
                    <GradientButton size="sm" disabled={creatingId === rec.modelId}
                      onClick={() => createInstance(rec)}>
                      {creatingId === rec.modelId
                        ? <IconLoader2 className="mr-1 size-4 animate-spin" />
                        : <IconPlus className="mr-1 size-4" />}
                      {t("modelAdvisor.createInstance")}
                    </GradientButton>
                  ) : (
                    <Button size="sm" variant="outline" onClick={() => createInstance(rec)}>
                      {t("modelAdvisor.configure")}
                    </Button>
                  )}
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
}
