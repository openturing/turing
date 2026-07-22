import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import type {
    TurCostReport,
    TurDailyCostPoint,
    TurLLMConsumerPlan,
    TurLLMPrice,
} from "@/models/cost-governance/cost-governance.model";
import { TurCostGovernanceService } from "@/services/cost-governance/cost-governance.service";
import {
    IconAlertTriangle,
    IconCoin,
    IconCpu2,
    IconExternalLink,
    IconFileText,
    IconLogin2,
    IconLogout2,
    IconPlus,
    IconTrash,
} from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import {
    CartesianGrid,
    Line,
    LineChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";

const service = new TurCostGovernanceService();

function isoDaysAgo(days: number): string {
    const d = new Date();
    d.setDate(d.getDate() - days);
    return d.toISOString().slice(0, 10);
}

function todayIso(): string {
    return new Date().toISOString().slice(0, 10);
}

function formatUsd(n: number): string {
    if (n === 0) return "$0.00";
    if (Math.abs(n) < 1) return `$${n.toFixed(4)}`;
    return `$${n.toFixed(2)}`;
}

function formatTokens(n: number): string {
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
    if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
    return n.toLocaleString();
}

/** T778 — a catalog price not re-verified in this many days is flagged stale. */
const PRICE_STALE_DAYS = 180;

/** True when an ISO date string is older than {@link PRICE_STALE_DAYS} days. */
function isPriceStale(lastVerified?: string): boolean {
    if (!lastVerified) return false;
    const when = Date.parse(lastVerified);
    if (Number.isNaN(when)) return false;
    return (Date.now() - when) / 86_400_000 > PRICE_STALE_DAYS;
}

interface SummaryCardProps {
    title: string;
    value: string;
    icon: React.ElementType;
    color: string;
    bgColor: string;
    ringColor: string;
}

function SummaryCard({ title, value, icon: Icon, color, bgColor, ringColor }: Readonly<SummaryCardProps>) {
    return (
        <div className="rounded-xl border p-4">
            <div className="flex items-center gap-3">
                <div className={`flex h-9 w-9 items-center justify-center rounded-lg bg-linear-to-br ${bgColor} ring-1 ${ringColor}`}>
                    <Icon className={`size-4.5 ${color}`} />
                </div>
                <div className="min-w-0">
                    <p className="text-xs text-muted-foreground">{title}</p>
                    <p className="text-xl font-bold tracking-tight mt-0.5">{value}</p>
                </div>
            </div>
        </div>
    );
}

function CostChart({ points }: Readonly<{ points: TurDailyCostPoint[] }>) {
    const { t } = useTranslation();
    if (points.length === 0) {
        return <p className="text-muted-foreground py-8 text-center text-sm">{t("costGovernance.noSpend")}</p>;
    }
    const data = points.map((p) => ({ date: p.date.slice(5), cost: Number(p.costUsd.toFixed(6)) }));
    return (
        <div className="h-[240px] w-full">
            <ResponsiveContainer width="100%" height="100%">
                <LineChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border)" />
                    <XAxis dataKey="date" stroke="var(--muted-foreground)" fontSize={11} />
                    <YAxis stroke="var(--muted-foreground)" fontSize={11} width={60}
                        tickFormatter={(v: number) => formatUsd(v)} />
                    <Tooltip
                        contentStyle={{ background: "var(--card)", border: "1px solid var(--border)" }}
                        formatter={(v) => [formatUsd(Number(v) || 0), t("costGovernance.costTooltip")]} />
                    <Line type="monotone" dataKey="cost" stroke="#6366f1" strokeWidth={2}
                        isAnimationActive={false} dot={false} />
                </LineChart>
            </ResponsiveContainer>
        </div>
    );
}

/** T778 — source badge (catalog/manual + indicative) and a verify-at-vendor link. */
function PriceSourceCell({ price, link }: Readonly<{ price: TurLLMPrice; link?: string }>) {
    const { t } = useTranslation();
    const isCatalog = price.source === "CATALOG";
    const catalogTip = price.priceProvenance
        ? t("costGovernance.priceTable.sourceCatalogTooltip", { provenance: price.priceProvenance })
        : t("costGovernance.priceTable.sourceCatalogTooltipNoProvenance");
    return (
        <div className="flex flex-wrap items-center gap-1.5">
            {isCatalog ? (
                <Badge variant="secondary" title={catalogTip}>
                    {t("costGovernance.priceTable.sourceCatalog")}
                </Badge>
            ) : (
                <Badge variant="outline" title={t("costGovernance.priceTable.sourceManualTooltip")}>
                    {t("costGovernance.priceTable.sourceManual")}
                </Badge>
            )}
            {price.indicative && (
                <Badge variant="outline" title={t("costGovernance.priceTable.indicativeTooltip")}>
                    {t("costGovernance.priceTable.indicative")}
                </Badge>
            )}
            {link && (
                <a href={link} target="_blank" rel="noreferrer"
                    className="text-muted-foreground hover:text-foreground inline-flex"
                    title={t("costGovernance.priceTable.verify")}>
                    <IconExternalLink className="size-4" />
                </a>
            )}
        </div>
    );
}

/** T778 — last-verified date with a staleness warning for old catalog prices. */
function PriceVerifiedCell({ price }: Readonly<{ price: TurLLMPrice }>) {
    const { t } = useTranslation();
    if (!price.lastVerified) {
        return <span className="text-muted-foreground">{t("costGovernance.priceTable.neverVerified")}</span>;
    }
    const stale = isPriceStale(price.lastVerified);
    return (
        <div className="flex items-center gap-1.5">
            <span className={stale ? "text-muted-foreground" : ""}>{price.lastVerified}</span>
            {stale && (
                <Badge variant="destructive"
                    title={t("costGovernance.priceTable.staleTooltip", { days: PRICE_STALE_DAYS })}>
                    {t("costGovernance.priceTable.stale")}
                </Badge>
            )}
        </div>
    );
}

/** Inline editor for one or more price rows + an add-model form. */
function PriceEditor({ prices, providerLinks, onChanged }: Readonly<{
    prices: TurLLMPrice[];
    providerLinks: Record<string, string>;
    onChanged: () => void;
}>) {
    const { t } = useTranslation();
    const [draft, setDraft] = useState<Record<string, { input: string; output: string }>>({});
    const [newRow, setNewRow] = useState({ vendorId: "", modelName: "", input: "", output: "" });

    // Seed the editable draft from the loaded prices whenever they change.
    useEffect(() => {
        const seeded: Record<string, { input: string; output: string }> = {};
        for (const p of prices) {
            if (p.id) {
                seeded[p.id] = {
                    input: String(p.inputPricePerMillion),
                    output: String(p.outputPricePerMillion),
                };
            }
        }
        setDraft(seeded);
    }, [prices]);

    const draftFor = (p: TurLLMPrice) =>
        draft[p.id ?? ""] ?? { input: String(p.inputPricePerMillion), output: String(p.outputPricePerMillion) };

    const setDraftFor = (id: string, field: "input" | "output", value: string) =>
        setDraft((d) => {
            const cur = d[id] ?? { input: "", output: "" };
            return { ...d, [id]: { ...cur, [field]: value } };
        });

    const saveRow = async (p: TurLLMPrice) => {
        const d = draftFor(p);
        try {
            await service.savePrice({
                ...p,
                inputPricePerMillion: Number(d.input) || 0,
                outputPricePerMillion: Number(d.output) || 0,
            });
            toast.success(t("costGovernance.priceTable.saved", { model: p.modelName }));
            onChanged();
        } catch {
            toast.error(t("costGovernance.priceTable.saveFailed"));
        }
    };

    const addRow = async () => {
        if (!newRow.vendorId.trim() || !newRow.modelName.trim()) {
            toast.error(t("costGovernance.priceTable.required"));
            return;
        }
        try {
            await service.savePrice({
                vendorId: newRow.vendorId.trim(),
                modelName: newRow.modelName.trim(),
                inputPricePerMillion: Number(newRow.input) || 0,
                outputPricePerMillion: Number(newRow.output) || 0,
                currency: "USD",
            });
            toast.success(t("costGovernance.priceTable.added", { model: newRow.modelName }));
            setNewRow({ vendorId: "", modelName: "", input: "", output: "" });
            onChanged();
        } catch {
            toast.error(t("costGovernance.priceTable.addFailed"));
        }
    };

    const removeRow = async (p: TurLLMPrice) => {
        if (!p.id) return;
        try {
            await service.deletePrice(p.id);
            toast.success(t("costGovernance.priceTable.removed", { model: p.modelName }));
            onChanged();
        } catch {
            toast.error(t("costGovernance.priceTable.removeFailed"));
        }
    };

    return (
        <Table>
            <TableHeader>
                <TableRow>
                    <TableHead>{t("costGovernance.priceTable.vendor")}</TableHead>
                    <TableHead>{t("costGovernance.priceTable.model")}</TableHead>
                    <TableHead className="text-right">{t("costGovernance.priceTable.inputPrice")}</TableHead>
                    <TableHead className="text-right">{t("costGovernance.priceTable.outputPrice")}</TableHead>
                    <TableHead>{t("costGovernance.priceTable.source")}</TableHead>
                    <TableHead>{t("costGovernance.priceTable.verified")}</TableHead>
                    <TableHead className="text-right">{t("costGovernance.priceTable.actions")}</TableHead>
                </TableRow>
            </TableHeader>
            <TableBody>
                {prices.map((p) => {
                    const d = draftFor(p);
                    return (
                        <TableRow key={p.id ?? `${p.vendorId}:${p.modelName}`}>
                            <TableCell>{p.vendorId}</TableCell>
                            <TableCell className="font-medium">{p.modelName}</TableCell>
                            <TableCell className="text-right">
                                <Input type="number" step="0.01" min={0} value={d.input}
                                    className="w-28 text-right ml-auto"
                                    onChange={(e) => setDraftFor(p.id ?? "", "input", e.target.value)} />
                            </TableCell>
                            <TableCell className="text-right">
                                <Input type="number" step="0.01" min={0} value={d.output}
                                    className="w-28 text-right ml-auto"
                                    onChange={(e) => setDraftFor(p.id ?? "", "output", e.target.value)} />
                            </TableCell>
                            <TableCell>
                                <PriceSourceCell price={p} link={providerLinks[p.vendorId?.toLowerCase()]} />
                            </TableCell>
                            <TableCell>
                                <PriceVerifiedCell price={p} />
                            </TableCell>
                            <TableCell className="text-right">
                                <div className="flex justify-end gap-2">
                                    <Button size="sm" variant="outline" onClick={() => saveRow(p)}>{t("costGovernance.priceTable.save")}</Button>
                                    <Button size="sm" variant="ghost" onClick={() => removeRow(p)}
                                        title={t("costGovernance.priceTable.delete")}>
                                        <IconTrash className="size-4" />
                                    </Button>
                                </div>
                            </TableCell>
                        </TableRow>
                    );
                })}
                {/* Add-model row */}
                <TableRow>
                    <TableCell>
                        <Input placeholder={t("costGovernance.priceTable.vendorPlaceholder")} value={newRow.vendorId} className="w-28"
                            onChange={(e) => setNewRow((r) => ({ ...r, vendorId: e.target.value }))} />
                    </TableCell>
                    <TableCell>
                        <Input placeholder={t("costGovernance.priceTable.modelPlaceholder")} value={newRow.modelName} className="w-40"
                            onChange={(e) => setNewRow((r) => ({ ...r, modelName: e.target.value }))} />
                    </TableCell>
                    <TableCell className="text-right">
                        <Input type="number" step="0.01" min={0} placeholder="0" value={newRow.input}
                            className="w-28 text-right ml-auto"
                            onChange={(e) => setNewRow((r) => ({ ...r, input: e.target.value }))} />
                    </TableCell>
                    <TableCell className="text-right">
                        <Input type="number" step="0.01" min={0} placeholder="0" value={newRow.output}
                            className="w-28 text-right ml-auto"
                            onChange={(e) => setNewRow((r) => ({ ...r, output: e.target.value }))} />
                    </TableCell>
                    <TableCell />
                    <TableCell />
                    <TableCell className="text-right">
                        <Button size="sm" onClick={addRow}>
                            <IconPlus className="size-4 mr-1" /> {t("costGovernance.priceTable.add")}
                        </Button>
                    </TableCell>
                </TableRow>
            </TableBody>
        </Table>
    );
}

export default function CostGovernancePage() {
    const { t } = useTranslation();
    const [from, setFrom] = useState(isoDaysAgo(30));
    const [to, setTo] = useState(todayIso());
    const [report, setReport] = useState<TurCostReport | null>(null);
    const [points, setPoints] = useState<TurDailyCostPoint[]>([]);
    const [prices, setPrices] = useState<TurLLMPrice[]>([]);
    const [providerLinks, setProviderLinks] = useState<Record<string, string>>({});
    const [consumerPlans, setConsumerPlans] = useState<Record<string, TurLLMConsumerPlan[]>>({});
    const [loading, setLoading] = useState(true);

    const load = useCallback((f: string, t: string) => {
        setLoading(true);
        Promise.all([service.getSummary(f, t), service.getTimeseries(f, t)])
            .then(([rep, ts]) => {
                setReport(rep);
                setPoints(ts);
            })
            .catch(() => {
                setReport(null);
                setPoints([]);
            })
            .finally(() => setLoading(false));
    }, []);

    const loadPrices = useCallback(() => {
        service.listPrices().then(setPrices).catch(() => setPrices([]));
    }, []);

    useEffect(() => {
        load(from, to);
    }, [from, to, load]);

    useEffect(() => {
        loadPrices();
        service.getProviderLinks().then(setProviderLinks).catch(() => setProviderLinks({}));
        service.getConsumerPlans().then(setConsumerPlans).catch(() => setConsumerPlans({}));
    }, [loadPrices]);

    const avgPerRequest = useMemo(() => {
        if (!report || report.totalRequests === 0) return 0;
        return report.totalCostUsd / report.totalRequests;
    }, [report]);

    // T778 — cross-check the billed models against the price table so operators
    // see which used models were billed at $0 (no price row) or on an indicative
    // (published, unverified) catalog rate.
    const priceByKey = useMemo(() => {
        const map = new Map<string, TurLLMPrice>();
        for (const p of prices) {
            map.set(`${p.vendorId?.toLowerCase()}:${p.modelName}`, p);
        }
        return map;
    }, [prices]);

    const usedUnpriced = useMemo(
        () => (report?.byModel ?? []).filter(
            (row) => !priceByKey.has(`${row.vendorId?.toLowerCase()}:${row.modelName}`)),
        [report, priceByKey],
    );

    const usedIndicative = useMemo(
        () => (report?.byModel ?? []).filter(
            (row) => priceByKey.get(`${row.vendorId?.toLowerCase()}:${row.modelName}`)?.indicative),
        [report, priceByKey],
    );

    return (
        <div className="space-y-6 px-4 lg:px-6">
            {/* Date range */}
            <div className="flex flex-wrap items-end gap-3">
                <div className="flex flex-col gap-1">
                    <label htmlFor="cost-from" className="text-xs text-muted-foreground">{t("costGovernance.dateFrom")}</label>
                    <Input id="cost-from" type="date" value={from} className="w-40"
                        max={to} onChange={(e) => setFrom(e.target.value)} />
                </div>
                <div className="flex flex-col gap-1">
                    <label htmlFor="cost-to" className="text-xs text-muted-foreground">{t("costGovernance.dateTo")}</label>
                    <Input id="cost-to" type="date" value={to} className="w-40"
                        min={from} onChange={(e) => setTo(e.target.value)} />
                </div>
            </div>

            {loading ? (
                <div className="text-muted-foreground py-16 text-center text-sm">{t("costGovernance.loading")}</div>
            ) : !report ? (
                <div className="text-muted-foreground py-16 text-center text-sm">{t("costGovernance.loadFailed")}</div>
            ) : (
                <>
                    {/* Summary cards */}
                    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                        <SummaryCard title={t("costGovernance.cards.totalCost")} value={formatUsd(report.totalCostUsd)}
                            icon={IconCoin} color="text-violet-600 dark:text-violet-400"
                            bgColor="from-violet-500/10 to-purple-500/10 dark:from-violet-500/20 dark:to-purple-500/20"
                            ringColor="ring-violet-500/10 dark:ring-violet-400/10" />
                        <SummaryCard title={t("costGovernance.cards.avgPerRequest")} value={formatUsd(avgPerRequest)}
                            icon={IconCoin} color="text-amber-600 dark:text-amber-400"
                            bgColor="from-amber-500/10 to-orange-500/10 dark:from-amber-500/20 dark:to-orange-500/20"
                            ringColor="ring-amber-500/10 dark:ring-amber-400/10" />
                        <SummaryCard title={t("costGovernance.cards.requests")} value={report.totalRequests.toLocaleString()}
                            icon={IconFileText} color="text-blue-600 dark:text-blue-400"
                            bgColor="from-blue-500/10 to-indigo-500/10 dark:from-blue-500/20 dark:to-indigo-500/20"
                            ringColor="ring-blue-500/10 dark:ring-blue-400/10" />
                        <SummaryCard title={t("costGovernance.cards.inputTokens")} value={formatTokens(report.totalInputTokens)}
                            icon={IconLogin2} color="text-emerald-600 dark:text-emerald-400"
                            bgColor="from-emerald-500/10 to-teal-500/10 dark:from-emerald-500/20 dark:to-emerald-500/20"
                            ringColor="ring-emerald-500/10 dark:ring-emerald-400/10" />
                        <SummaryCard title={t("costGovernance.cards.outputTokens")} value={formatTokens(report.totalOutputTokens)}
                            icon={IconLogout2} color="text-amber-600 dark:text-amber-400"
                            bgColor="from-amber-500/10 to-orange-500/10 dark:from-amber-500/20 dark:to-orange-500/20"
                            ringColor="ring-amber-500/10 dark:ring-amber-400/10" />
                        <SummaryCard title={t("costGovernance.cards.totalTokens")} value={formatTokens(report.totalTokens)}
                            icon={IconCpu2} color="text-emerald-600 dark:text-emerald-400"
                            bgColor="from-emerald-500/10 to-teal-500/10 dark:from-emerald-500/20 dark:to-emerald-500/20"
                            ringColor="ring-emerald-500/10 dark:ring-emerald-400/10" />
                    </div>

                    {/* Daily cost chart */}
                    <Card>
                        <CardHeader>
                            <CardTitle>{t("costGovernance.dailySpend")}</CardTitle>
                            <CardDescription>{t("costGovernance.dailySpendDesc")}</CardDescription>
                        </CardHeader>
                        <CardContent>
                            <CostChart points={points} />
                        </CardContent>
                    </Card>

                    {/* By agent */}
                    <Card>
                        <CardHeader>
                            <CardTitle>{t("costGovernance.byAgent")}</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <BreakdownTable
                                head={[t("costGovernance.columns.agent"), t("costGovernance.columns.cost"), t("costGovernance.columns.tokens"), t("costGovernance.columns.requests")]}
                                rows={report.byAgent.map((r) => [
                                    r.agentTitle, formatUsd(r.costUsd), formatTokens(r.totalTokens),
                                    r.requestCount.toLocaleString(),
                                ])} />
                        </CardContent>
                    </Card>

                    {/* By model + by stage side by side on wide screens */}
                    <div className="grid gap-6 lg:grid-cols-2">
                        <Card>
                            <CardHeader>
                                <CardTitle>{t("costGovernance.byModel")}</CardTitle>
                            </CardHeader>
                            <CardContent>
                                <BreakdownTable
                                    head={[t("costGovernance.columns.model"), t("costGovernance.columns.cost"), t("costGovernance.columns.input"), t("costGovernance.columns.output"), t("costGovernance.columns.tokens")]}
                                    rows={report.byModel.map((r) => [
                                        `${r.vendorId} / ${r.modelName}`, formatUsd(r.costUsd),
                                        formatTokens(r.inputTokens), formatTokens(r.outputTokens),
                                        formatTokens(r.totalTokens),
                                    ])} />
                            </CardContent>
                        </Card>
                        <Card>
                            <CardHeader>
                                <CardTitle>{t("costGovernance.byStage")}</CardTitle>
                                <CardDescription>{t("costGovernance.byStageDesc")}</CardDescription>
                            </CardHeader>
                            <CardContent>
                                <BreakdownTable
                                    head={[t("costGovernance.columns.stage"), t("costGovernance.columns.cost"), t("costGovernance.columns.tokens")]}
                                    rows={report.byStage.map((r) => [
                                        r.stage ?? t("costGovernance.untagged"), formatUsd(r.costUsd),
                                        formatTokens(r.totalTokens),
                                    ])} />
                            </CardContent>
                        </Card>
                    </div>
                </>
            )}

            {/* T778 — provenance / freshness warnings for billed models */}
            {usedUnpriced.length > 0 && (
                <PriceWarningCallout
                    tone="destructive"
                    title={t("costGovernance.priceTable.unpricedTitle")}
                    description={t("costGovernance.priceTable.unpricedDescription")}
                    models={usedUnpriced.map((r) => `${r.vendorId}/${r.modelName}`)} />
            )}
            {usedIndicative.length > 0 && (
                <PriceWarningCallout
                    tone="warning"
                    title={t("costGovernance.priceTable.indicativeUsedTitle")}
                    description={t("costGovernance.priceTable.indicativeUsedDescription")}
                    models={usedIndicative.map((r) => `${r.vendorId}/${r.modelName}`)} />
            )}

            {/* Price table editor */}
            <Card>
                <CardHeader>
                    <CardTitle>{t("costGovernance.priceTable.title")}</CardTitle>
                    <CardDescription>
                        {t("costGovernance.priceTable.description")}
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <PriceEditor prices={prices} providerLinks={providerLinks}
                        onChanged={() => { loadPrices(); load(from, to); }} />
                </CardContent>
            </Card>

            {/* T789 — consumer subscription plan reference (admin help) */}
            <ConsumerPlansCard plans={consumerPlans} />
        </div>
    );
}

/** T789 — indicative consumer-plan reference grouped by vendor (reference only). */
function ConsumerPlansCard({ plans }: Readonly<{ plans: Record<string, TurLLMConsumerPlan[]> }>) {
    const { t } = useTranslation();
    const vendors = Object.keys(plans).filter((v) => (plans[v]?.length ?? 0) > 0).sort((a, b) => a.localeCompare(b));
    if (vendors.length === 0) return null;
    return (
        <Card>
            <CardHeader>
                <CardTitle>{t("costGovernance.consumerPlans.title")}</CardTitle>
                <CardDescription>{t("costGovernance.consumerPlans.description")}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
                {vendors.map((vendor) => (
                    <div key={vendor}>
                        <p className="mb-1.5 text-sm font-medium capitalize">{vendor}</p>
                        <div className="flex flex-wrap gap-2">
                            {plans[vendor].map((plan) => (
                                <a key={plan.id} href={plan.url ?? undefined} target="_blank" rel="noreferrer"
                                    className="group flex items-center gap-2 rounded-lg border border-border/60 bg-card/40 px-3 py-1.5 text-sm hover:border-border">
                                    <span className="font-medium">{plan.name ?? plan.id}</span>
                                    {plan.priceMonthlyUsd != null && (
                                        <span className="text-muted-foreground">
                                            {plan.priceMonthlyUsd === 0
                                                ? t("costGovernance.consumerPlans.free")
                                                : `$${plan.priceMonthlyUsd}/mo`}
                                        </span>
                                    )}
                                    {plan.url && <IconExternalLink className="size-3.5 text-muted-foreground" />}
                                </a>
                            ))}
                        </div>
                    </div>
                ))}
            </CardContent>
        </Card>
    );
}

/** T778 — a small inline callout listing billed models that need attention. */
function PriceWarningCallout({ tone, title, description, models }: Readonly<{
    tone: "destructive" | "warning";
    title: string;
    description: string;
    models: string[];
}>) {
    const toneClass = tone === "destructive"
        ? "border-destructive/40 bg-destructive/5 text-destructive"
        : "border-amber-500/40 bg-amber-500/5 text-amber-700 dark:text-amber-400";
    return (
        <div className={`flex gap-3 rounded-lg border p-4 ${toneClass}`}>
            <IconAlertTriangle className="mt-0.5 size-5 shrink-0" />
            <div className="space-y-1">
                <p className="font-medium">{title}</p>
                <p className="text-muted-foreground text-sm">{description}</p>
                <div className="flex flex-wrap gap-1.5 pt-1">
                    {models.map((m) => (
                        <Badge key={m} variant="outline" className="font-mono text-xs">{m}</Badge>
                    ))}
                </div>
            </div>
        </div>
    );
}

function BreakdownTable({ head, rows }: Readonly<{ head: string[]; rows: string[][] }>) {
    const { t } = useTranslation();
    if (rows.length === 0) {
        return <p className="text-muted-foreground py-4 text-center text-sm">{t("costGovernance.noData")}</p>;
    }
    return (
        <Table>
            <TableHeader>
                <TableRow>
                    {head.map((h, i) => (
                        <TableHead key={h} className={i === 0 ? "" : "text-right"}>{h}</TableHead>
                    ))}
                </TableRow>
            </TableHeader>
            <TableBody>
                {rows.map((row, ri) => (
                    <TableRow key={ri}>
                        {row.map((cell, ci) => (
                            <TableCell key={ci}
                                className={ci === 0 ? "font-medium" : "text-right"}>{cell}</TableCell>
                        ))}
                    </TableRow>
                ))}
            </TableBody>
        </Table>
    );
}
