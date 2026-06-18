import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
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
    TurLLMPrice,
} from "@/models/cost-governance/cost-governance.model";
import { TurCostGovernanceService } from "@/services/cost-governance/cost-governance.service";
import {
    IconCoin,
    IconCpu2,
    IconFileText,
    IconPlus,
    IconTrash,
} from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useState } from "react";
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
    if (points.length === 0) {
        return <p className="text-muted-foreground py-8 text-center text-sm">No spend in this period.</p>;
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
                        formatter={(v) => [formatUsd(Number(v) || 0), "Cost"]} />
                    <Line type="monotone" dataKey="cost" stroke="#6366f1" strokeWidth={2}
                        isAnimationActive={false} dot={false} />
                </LineChart>
            </ResponsiveContainer>
        </div>
    );
}

/** Inline editor for one or more price rows + an add-model form. */
function PriceEditor({ prices, onChanged }: Readonly<{ prices: TurLLMPrice[]; onChanged: () => void }>) {
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
            toast.success(`Saved price for ${p.modelName}`);
            onChanged();
        } catch {
            toast.error("Failed to save price");
        }
    };

    const addRow = async () => {
        if (!newRow.vendorId.trim() || !newRow.modelName.trim()) {
            toast.error("Vendor and model are required");
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
            toast.success(`Added price for ${newRow.modelName}`);
            setNewRow({ vendorId: "", modelName: "", input: "", output: "" });
            onChanged();
        } catch {
            toast.error("Failed to add price");
        }
    };

    const removeRow = async (p: TurLLMPrice) => {
        if (!p.id) return;
        try {
            await service.deletePrice(p.id);
            toast.success(`Removed price for ${p.modelName}`);
            onChanged();
        } catch {
            toast.error("Failed to remove price");
        }
    };

    return (
        <Table>
            <TableHeader>
                <TableRow>
                    <TableHead>Vendor</TableHead>
                    <TableHead>Model</TableHead>
                    <TableHead className="text-right">Input $/1M</TableHead>
                    <TableHead className="text-right">Output $/1M</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
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
                            <TableCell className="text-right">
                                <div className="flex justify-end gap-2">
                                    <Button size="sm" variant="outline" onClick={() => saveRow(p)}>Save</Button>
                                    <Button size="sm" variant="ghost" onClick={() => removeRow(p)}
                                        title="Delete">
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
                        <Input placeholder="openai" value={newRow.vendorId} className="w-28"
                            onChange={(e) => setNewRow((r) => ({ ...r, vendorId: e.target.value }))} />
                    </TableCell>
                    <TableCell>
                        <Input placeholder="gpt-4o" value={newRow.modelName} className="w-40"
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
                    <TableCell className="text-right">
                        <Button size="sm" onClick={addRow}>
                            <IconPlus className="size-4 mr-1" /> Add
                        </Button>
                    </TableCell>
                </TableRow>
            </TableBody>
        </Table>
    );
}

export default function CostGovernancePage() {
    const [from, setFrom] = useState(isoDaysAgo(30));
    const [to, setTo] = useState(todayIso());
    const [report, setReport] = useState<TurCostReport | null>(null);
    const [points, setPoints] = useState<TurDailyCostPoint[]>([]);
    const [prices, setPrices] = useState<TurLLMPrice[]>([]);
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
    }, [loadPrices]);

    const avgPerRequest = useMemo(() => {
        if (!report || report.totalRequests === 0) return 0;
        return report.totalCostUsd / report.totalRequests;
    }, [report]);

    return (
        <div className="space-y-6 px-4 lg:px-6">
            {/* Date range */}
            <div className="flex flex-wrap items-end gap-3">
                <div className="flex flex-col gap-1">
                    <label htmlFor="cost-from" className="text-xs text-muted-foreground">From</label>
                    <Input id="cost-from" type="date" value={from} className="w-40"
                        max={to} onChange={(e) => setFrom(e.target.value)} />
                </div>
                <div className="flex flex-col gap-1">
                    <label htmlFor="cost-to" className="text-xs text-muted-foreground">To</label>
                    <Input id="cost-to" type="date" value={to} className="w-40"
                        min={from} onChange={(e) => setTo(e.target.value)} />
                </div>
            </div>

            {loading ? (
                <div className="text-muted-foreground py-16 text-center text-sm">Loading spend…</div>
            ) : !report ? (
                <div className="text-muted-foreground py-16 text-center text-sm">Failed to load spend.</div>
            ) : (
                <>
                    {/* Summary cards */}
                    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                        <SummaryCard title="Total cost" value={formatUsd(report.totalCostUsd)}
                            icon={IconCoin} color="text-violet-600 dark:text-violet-400"
                            bgColor="from-violet-500/10 to-purple-500/10 dark:from-violet-500/20 dark:to-purple-500/20"
                            ringColor="ring-violet-500/10 dark:ring-violet-400/10" />
                        <SummaryCard title="Total tokens" value={formatTokens(report.totalTokens)}
                            icon={IconCpu2} color="text-emerald-600 dark:text-emerald-400"
                            bgColor="from-emerald-500/10 to-teal-500/10 dark:from-emerald-500/20 dark:to-teal-500/20"
                            ringColor="ring-emerald-500/10 dark:ring-emerald-400/10" />
                        <SummaryCard title="Requests" value={report.totalRequests.toLocaleString()}
                            icon={IconFileText} color="text-blue-600 dark:text-blue-400"
                            bgColor="from-blue-500/10 to-indigo-500/10 dark:from-blue-500/20 dark:to-indigo-500/20"
                            ringColor="ring-blue-500/10 dark:ring-blue-400/10" />
                        <SummaryCard title="Avg / request" value={formatUsd(avgPerRequest)}
                            icon={IconCoin} color="text-amber-600 dark:text-amber-400"
                            bgColor="from-amber-500/10 to-orange-500/10 dark:from-amber-500/20 dark:to-orange-500/20"
                            ringColor="ring-amber-500/10 dark:ring-amber-400/10" />
                    </div>

                    {/* Daily cost chart */}
                    <Card>
                        <CardHeader>
                            <CardTitle>Daily spend</CardTitle>
                            <CardDescription>USD cost per day across all providers (local / unpriced = $0).</CardDescription>
                        </CardHeader>
                        <CardContent>
                            <CostChart points={points} />
                        </CardContent>
                    </Card>

                    {/* By agent */}
                    <Card>
                        <CardHeader>
                            <CardTitle>Spend by agent</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <BreakdownTable
                                head={["Agent", "Cost", "Tokens", "Requests"]}
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
                                <CardTitle>Spend by model</CardTitle>
                            </CardHeader>
                            <CardContent>
                                <BreakdownTable
                                    head={["Model", "Cost", "Tokens"]}
                                    rows={report.byModel.map((r) => [
                                        `${r.vendorId} / ${r.modelName}`, formatUsd(r.costUsd),
                                        formatTokens(r.totalTokens),
                                    ])} />
                            </CardContent>
                        </Card>
                        <Card>
                            <CardHeader>
                                <CardTitle>Spend by stage</CardTitle>
                                <CardDescription>Live vs. background vs. skill.</CardDescription>
                            </CardHeader>
                            <CardContent>
                                <BreakdownTable
                                    head={["Stage", "Cost", "Tokens"]}
                                    rows={report.byStage.map((r) => [
                                        r.stage ?? "(untagged)", formatUsd(r.costUsd),
                                        formatTokens(r.totalTokens),
                                    ])} />
                            </CardContent>
                        </Card>
                    </div>
                </>
            )}

            {/* Price table editor */}
            <Card>
                <CardHeader>
                    <CardTitle>Model price table</CardTitle>
                    <CardDescription>
                        USD per 1M tokens. Edits apply to the next chat turn; local / embedded models cost $0.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <PriceEditor prices={prices} onChanged={() => { loadPrices(); load(from, to); }} />
                </CardContent>
            </Card>
        </div>
    );
}

function BreakdownTable({ head, rows }: Readonly<{ head: string[]; rows: string[][] }>) {
    if (rows.length === 0) {
        return <p className="text-muted-foreground py-4 text-center text-sm">No data.</p>;
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
