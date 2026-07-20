import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  IconApi,
  IconCopy,
  IconKey,
  IconPlus,
  IconRefresh,
  IconChartBar,
  IconTerminal2,
  IconTrash,
} from "@tabler/icons-react";

import { ROUTES } from "@/app/routes.const";
import { BentoFormSection, BentoHero } from "@/components/bento";
import { GatewayModelPicker } from "@/components/gateway/gateway-model-picker";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  useCreateGatewayKey,
  useDeleteGatewayKey,
  useGatewayKeys,
  useGatewayUsage,
  useRotateGatewayKey,
} from "@/api/queries/gateway.queries";
import type { TurGatewayKeyView } from "@/models/gateway/gateway.model.ts";

/**
 * T748 / §XLIX — the Governed LLM Gateway admin (`/bento/gateway`, Block AZ):
 * virtual-key CRUD (create / rotate / revoke with scope + budget), a per-key
 * spend dashboard, and an OpenAI-compatible playground with a copy-paste
 * `curl` snippet. A read-mostly functional surface, so it "sits inside the
 * shell": a {@link BentoHero} over frosted {@link BentoFormSection} cards.
 */
export default function BentoGatewayPage() {
  const { t } = useTranslation();
  const { data: keys } = useGatewayKeys();
  const { data: usage } = useGatewayUsage();
  const createKey = useCreateGatewayKey();
  const rotateKey = useRotateGatewayKey();
  const deleteKey = useDeleteGatewayKey();

  const [name, setName] = useState("");
  const [allowedModels, setAllowedModels] = useState("");
  const [monthlyBudget, setMonthlyBudget] = useState("");
  const [hardCap, setHardCap] = useState("");
  const [rateLimit, setRateLimit] = useState("");
  const [revealedKey, setRevealedKey] = useState<string | null>(null);

  const baseUrl = useMemo(
    () => `${typeof window !== "undefined" ? window.location.origin : ""}/v1`,
    [],
  );

  const numberOrNull = (value: string): number | null => {
    const trimmed = value.trim();
    if (!trimmed) return null;
    const parsed = Number(trimmed);
    return Number.isFinite(parsed) ? parsed : null;
  };

  const copy = (text: string) => {
    if (navigator?.clipboard) {
      void navigator.clipboard.writeText(text);
    }
  };

  const onCreate = async () => {
    if (!name.trim()) return;
    const created = await createKey.mutateAsync({
      name: name.trim(),
      allowedModels: allowedModels.trim() || null,
      monthlyBudgetUsd: numberOrNull(monthlyBudget),
      hardMonthlyCapUsd: numberOrNull(hardCap),
      rateLimitPerMinute: numberOrNull(rateLimit) as number | null,
    });
    setRevealedKey(created.rawKey);
    setName("");
    setAllowedModels("");
    setMonthlyBudget("");
    setHardCap("");
    setRateLimit("");
  };

  const onRotate = async (key: TurGatewayKeyView) => {
    const rotated = await rotateKey.mutateAsync(key.id);
    setRevealedKey(rotated.rawKey);
  };

  const curlSnippet = `curl ${baseUrl}/chat/completions \\
  -H "Authorization: Bearer sk-turing-..." \\
  -H "Content-Type: application/json" \\
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Hello"}]
  }'`;

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_AREA_MANAGEMENT}
        backLabel={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-indigo-500 to-blue-600 text-white shadow-md">
            <IconApi size={24} />
          </span>
        }
        title={t("gateway.title", { defaultValue: "LLM Gateway" })}
        subtitle={t("gateway.description", {
          defaultValue:
            "OpenAI-compatible governed egress — issue virtual keys, cap spend, and track usage.",
        })}
      />

      {revealedKey && (
        <div className="bento-fade-in mb-6 rounded-2xl border border-emerald-500/40 bg-emerald-500/10 p-4">
          <p className="mb-2 text-sm font-medium text-emerald-700 dark:text-emerald-300">
            {t("gateway.keyRevealWarning", {
              defaultValue: "Copy this key now — it is shown only once.",
            })}
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 overflow-x-auto rounded-lg bg-background/70 px-3 py-2 font-mono text-sm">
              {revealedKey}
            </code>
            <Button size="sm" variant="outline" onClick={() => copy(revealedKey)}>
              <IconCopy size={16} className="mr-1" />
              {t("common.copy", { defaultValue: "Copy" })}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setRevealedKey(null)}>
              {t("common.dismiss", { defaultValue: "Dismiss" })}
            </Button>
          </div>
        </div>
      )}

      <div className="bento-grid grid gap-6">
        <BentoFormSection
          icon={IconPlus}
          tone="blue"
          title={t("gateway.createTitle", { defaultValue: "New virtual key" })}
          description={t("gateway.createDescription", {
            defaultValue:
              "Scope a key to specific models and give it a monthly budget and rate limit.",
          })}
        >
          <div className="grid gap-4 md:grid-cols-2">
            <div className="grid gap-1.5">
              <Label htmlFor="gw-name">{t("gateway.fieldName", { defaultValue: "Name" })}</Label>
              <Input
                id="gw-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Marketing dev key"
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="gw-budget">
                {t("gateway.fieldMonthlyBudget", { defaultValue: "Monthly budget (USD)" })}
              </Label>
              <Input
                id="gw-budget"
                type="number"
                value={monthlyBudget}
                onChange={(e) => setMonthlyBudget(e.target.value)}
                placeholder="50"
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="gw-hardcap">
                {t("gateway.fieldHardCap", { defaultValue: "Hard cap (USD)" })}
              </Label>
              <Input
                id="gw-hardcap"
                type="number"
                value={hardCap}
                onChange={(e) => setHardCap(e.target.value)}
                placeholder="100"
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="gw-rate">
                {t("gateway.fieldRateLimit", { defaultValue: "Rate limit (req/min)" })}
              </Label>
              <Input
                id="gw-rate"
                type="number"
                value={rateLimit}
                onChange={(e) => setRateLimit(e.target.value)}
                placeholder="60"
              />
            </div>
          </div>
          <div className="mt-4 grid gap-1.5">
            <Label>{t("gateway.fieldAllowedModels", { defaultValue: "Allowed models" })}</Label>
            <GatewayModelPicker value={allowedModels} onChange={setAllowedModels} />
          </div>
          <div className="mt-4">
            <Button onClick={onCreate} disabled={!name.trim() || createKey.isPending}>
              <IconKey size={16} className="mr-1" />
              {t("gateway.createButton", { defaultValue: "Create key" })}
            </Button>
          </div>
        </BentoFormSection>

        <BentoFormSection
          icon={IconKey}
          tone="indigo"
          title={t("gateway.keysTitle", { defaultValue: "Virtual keys" })}
          description={t("gateway.keysDescription", {
            defaultValue: "Rotate to issue a fresh secret; revoke to disable a key immediately.",
          })}
        >
          {!keys || keys.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              {t("gateway.keysEmpty", { defaultValue: "No virtual keys yet." })}
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border/60 text-left text-muted-foreground">
                    <th className="py-2 pr-4">{t("gateway.colName", { defaultValue: "Name" })}</th>
                    <th className="py-2 pr-4">{t("gateway.colKey", { defaultValue: "Key" })}</th>
                    <th className="py-2 pr-4">{t("gateway.colScope", { defaultValue: "Models" })}</th>
                    <th className="py-2 pr-4">{t("gateway.colBudget", { defaultValue: "Budget" })}</th>
                    <th className="py-2 pr-4" />
                  </tr>
                </thead>
                <tbody>
                  {keys.map((key) => (
                    <tr key={key.id} className="border-b border-border/40">
                      <td className="py-2 pr-4 font-medium">
                        {key.name}
                        {key.enabled !== 1 && (
                          <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                            {t("gateway.revoked", { defaultValue: "revoked" })}
                          </span>
                        )}
                      </td>
                      <td className="py-2 pr-4 font-mono text-xs">{key.keyPrefix}</td>
                      <td className="py-2 pr-4 text-muted-foreground">{key.allowedModels || "—"}</td>
                      <td className="py-2 pr-4 text-muted-foreground">
                        {key.monthlyBudgetUsd != null ? `$${key.monthlyBudgetUsd}/mo` : "—"}
                      </td>
                      <td className="py-2 pr-4">
                        <div className="flex justify-end gap-1">
                          <Button
                            size="sm"
                            variant="ghost"
                            aria-label={t("gateway.rotate", { defaultValue: "Rotate" })}
                            onClick={() => onRotate(key)}
                          >
                            <IconRefresh size={16} />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            aria-label={t("gateway.revoke", { defaultValue: "Revoke" })}
                            onClick={() => deleteKey.mutate(key.id)}
                          >
                            <IconTrash size={16} />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </BentoFormSection>

        <BentoFormSection
          icon={IconChartBar}
          tone="emerald"
          title={t("gateway.spendTitle", { defaultValue: "Spend by key (last 30 days)" })}
          description={t("gateway.spendDescription", {
            defaultValue: "Inbound gateway spend, metered per virtual key.",
          })}
        >
          {!usage || usage.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              {t("gateway.spendEmpty", { defaultValue: "No gateway usage recorded yet." })}
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border/60 text-left text-muted-foreground">
                    <th className="py-2 pr-4">{t("gateway.colName", { defaultValue: "Name" })}</th>
                    <th className="py-2 pr-4">{t("gateway.colCost", { defaultValue: "Cost (USD)" })}</th>
                    <th className="py-2 pr-4">{t("gateway.colTokens", { defaultValue: "Tokens" })}</th>
                    <th className="py-2 pr-4">{t("gateway.colRequests", { defaultValue: "Requests" })}</th>
                  </tr>
                </thead>
                <tbody>
                  {usage.map((row) => (
                    <tr key={row.keyId} className="border-b border-border/40">
                      <td className="py-2 pr-4 font-medium">{row.keyName}</td>
                      <td className="py-2 pr-4">${row.costUsd.toFixed(4)}</td>
                      <td className="py-2 pr-4">{row.totalTokens.toLocaleString()}</td>
                      <td className="py-2 pr-4">{row.requestCount.toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </BentoFormSection>

        <BentoFormSection
          icon={IconTerminal2}
          tone="slate"
          title={t("gateway.playgroundTitle", { defaultValue: "OpenAI-compatible playground" })}
          description={t("gateway.playgroundDescription", {
            defaultValue: "Point any OpenAI client at this base URL and use a virtual key.",
          })}
        >
          <div className="grid gap-1.5">
            <Label>{t("gateway.baseUrl", { defaultValue: "Base URL" })}</Label>
            <div className="flex items-center gap-2">
              <code className="flex-1 overflow-x-auto rounded-lg bg-background/70 px-3 py-2 font-mono text-sm">
                {baseUrl}
              </code>
              <Button size="sm" variant="outline" onClick={() => copy(baseUrl)}>
                <IconCopy size={16} />
              </Button>
            </div>
          </div>
          <div className="mt-4 grid gap-1.5">
            <Label>{t("gateway.curlExample", { defaultValue: "curl example" })}</Label>
            <div className="relative">
              <pre className="overflow-x-auto rounded-lg bg-background/70 p-3 font-mono text-xs">
                {curlSnippet}
              </pre>
              <Button
                size="sm"
                variant="outline"
                className="absolute right-2 top-2"
                onClick={() => copy(curlSnippet)}
              >
                <IconCopy size={16} />
              </Button>
            </div>
          </div>
        </BentoFormSection>
      </div>
    </>
  );
}
