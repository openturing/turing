import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import type {
    TurDailyUsageRow,
    TurMonthlySummaryRow,
    TurUsageReport,
} from "@/models/llm/llm-token-usage.model";
import { TurLLMTokenUsageService } from "@/services/llm/llm-token-usage.service";
import { TurLLMInstanceService } from "@/services/llm/llm.service";
import {
    IconArrowLeft,
    IconArrowRight,
    IconChartBar,
    IconCoin,
    IconFileText,
    IconLogin2,
    IconLogout2,
} from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const service = new TurLLMTokenUsageService();
const llmService = new TurLLMInstanceService();

function formatTokens(n: number): string {
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
    if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
    return n.toLocaleString();
}

function getMonthOptions(): { value: string; label: string }[] {
    const options: { value: string; label: string }[] = [];
    const now = new Date();
    for (let i = 0; i < 12; i++) {
        const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
        const label = d.toLocaleDateString(undefined, {
            year: "numeric",
            month: "long",
        });
        options.push({ value, label });
    }
    return options;
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

export default function TokenUsagePage() {
    const { t } = useTranslation();
    const monthOptions = getMonthOptions();
    const [selectedMonth, setSelectedMonth] = useState(monthOptions[0].value);
    const [report, setReport] = useState<TurUsageReport | null>(null);
    const [loading, setLoading] = useState(true);
    const [hasEnabledLlm, setHasEnabledLlm] = useState<boolean | null>(null);

    useEffect(() => {
        llmService.query().then((instances) => {
            setHasEnabledLlm(instances.some((i) => i.enabled === 1));
        });
    }, []);

    const load = useCallback((month: string) => {
        setLoading(true);
        service
            .getReport(month)
            .then(setReport)
            .catch(() => setReport(null))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        load(selectedMonth);
    }, [selectedMonth, load]);

    const handlePrev = () => {
        const idx = monthOptions.findIndex((o) => o.value === selectedMonth);
        if (idx < monthOptions.length - 1) setSelectedMonth(monthOptions[idx + 1].value);
    };

    const handleNext = () => {
        const idx = monthOptions.findIndex((o) => o.value === selectedMonth);
        if (idx > 0) setSelectedMonth(monthOptions[idx - 1].value);
    };

    const currentIdx = monthOptions.findIndex((o) => o.value === selectedMonth);

    if (hasEnabledLlm === false) {
        return (
            <BlankSlate
                icon={IconChartBar}
                title={t("tokenUsage.noLlm")}
                description={t("tokenUsage.noLlmDescription")}
                buttonText={t("tokenUsage.createLlm")}
                urlNew={ROUTES.LLM_INSTANCE + "/new"}
            />
        );
    }

    return (
        <div className="space-y-6 px-4 lg:px-6">
            {/* Month Navigation */}
            <div className="flex items-center gap-2">
                <button
                    type="button"
                    onClick={handlePrev}
                    disabled={currentIdx >= monthOptions.length - 1}
                    className="rounded-lg p-2 hover:bg-accent transition-colors disabled:opacity-30 cursor-pointer disabled:cursor-not-allowed"
                    title={t("tokenUsage.previousMonth")}
                >
                    <IconArrowLeft className="size-4" />
                </button>
                <Select value={selectedMonth} onValueChange={setSelectedMonth}>
                    <SelectTrigger className="w-[200px]">
                        <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                        {monthOptions.map((o) => (
                            <SelectItem key={o.value} value={o.value}>
                                {o.label}
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
                <button
                    type="button"
                    onClick={handleNext}
                    disabled={currentIdx <= 0}
                    className="rounded-lg p-2 hover:bg-accent transition-colors disabled:opacity-30 cursor-pointer disabled:cursor-not-allowed"
                    title={t("tokenUsage.nextMonth")}
                >
                    <IconArrowRight className="size-4" />
                </button>
            </div>

            {loading ? (
                <div className="text-muted-foreground py-16 text-center text-sm">{t("tokenUsage.loadingUsage")}</div>
            ) : !report ? (
                <div className="text-muted-foreground py-16 text-center text-sm">
                    {t("tokenUsage.loadFailed")}
                </div>
            ) : (
                <>
                    {/* Summary Cards */}
                    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                        <SummaryCard
                            title={t("tokenUsage.totalRequests")}
                            value={report.totalRequests.toLocaleString()}
                            icon={IconFileText}
                            color="text-blue-600 dark:text-blue-400"
                            bgColor="from-blue-500/10 to-indigo-500/10 dark:from-blue-500/20 dark:to-indigo-500/20"
                            ringColor="ring-blue-500/10 dark:ring-blue-400/10"
                        />
                        <SummaryCard
                            title={t("tokenUsage.inputTokens")}
                            value={formatTokens(report.totalInputTokens)}
                            icon={IconLogin2}
                            color="text-emerald-600 dark:text-emerald-400"
                            bgColor="from-emerald-500/10 to-teal-500/10 dark:from-emerald-500/20 dark:to-teal-500/20"
                            ringColor="ring-emerald-500/10 dark:ring-emerald-400/10"
                        />
                        <SummaryCard
                            title={t("tokenUsage.outputTokens")}
                            value={formatTokens(report.totalOutputTokens)}
                            icon={IconLogout2}
                            color="text-amber-600 dark:text-amber-400"
                            bgColor="from-amber-500/10 to-orange-500/10 dark:from-amber-500/20 dark:to-orange-500/20"
                            ringColor="ring-amber-500/10 dark:ring-amber-400/10"
                        />
                        <SummaryCard
                            title={t("tokenUsage.totalTokens")}
                            value={formatTokens(report.totalTokens)}
                            icon={IconCoin}
                            color="text-violet-600 dark:text-violet-400"
                            bgColor="from-violet-500/10 to-purple-500/10 dark:from-violet-500/20 dark:to-purple-500/20"
                            ringColor="ring-violet-500/10 dark:ring-violet-400/10"
                        />
                    </div>

                    {/* Monthly Summary by Model */}
                    <Card>
                        <CardHeader>
                            <CardTitle>{t("tokenUsage.summaryByModel")}</CardTitle>
                            <CardDescription>
                                {t("tokenUsage.summaryDescription")}
                            </CardDescription>
                        </CardHeader>
                        <CardContent>
                            {report.summary.length === 0 ? (
                                <p className="text-muted-foreground py-4 text-center text-sm">
                                    {t("tokenUsage.noUsageData")}
                                </p>
                            ) : (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>{t("tokenUsage.instance")}</TableHead>
                                            <TableHead>{t("tokenUsage.vendor")}</TableHead>
                                            <TableHead>{t("tokenUsage.model")}</TableHead>
                                            <TableHead className="text-right">{t("tokenUsage.requests")}</TableHead>
                                            <TableHead className="text-right">{t("tokenUsage.input")}</TableHead>
                                            <TableHead className="text-right">{t("tokenUsage.output")}</TableHead>
                                            <TableHead className="text-right">{t("tokenUsage.total")}</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {report.summary.map((row: TurMonthlySummaryRow, i: number) => (
                                            <TableRow key={i}>
                                                <TableCell className="font-medium">
                                                    {row.instanceTitle}
                                                </TableCell>
                                                <TableCell>{row.vendorId}</TableCell>
                                                <TableCell className="text-muted-foreground">
                                                    {row.modelName}
                                                </TableCell>
                                                <TableCell className="text-right">
                                                    {row.requestCount.toLocaleString()}
                                                </TableCell>
                                                <TableCell className="text-right">
                                                    {formatTokens(row.inputTokens)}
                                                </TableCell>
                                                <TableCell className="text-right">
                                                    {formatTokens(row.outputTokens)}
                                                </TableCell>
                                                <TableCell className="text-right font-medium">
                                                    {formatTokens(row.totalTokens)}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </CardContent>
                    </Card>

                    {/* Daily Breakdown */}
                    <Card>
                        <CardHeader>
                            <CardTitle>{t("tokenUsage.dailyBreakdown")}</CardTitle>
                            <CardDescription>
                                {t("tokenUsage.dailyDescription")}
                            </CardDescription>
                        </CardHeader>
                        <CardContent>
                            {report.daily.length === 0 ? (
                                <p className="text-muted-foreground py-4 text-center text-sm">
                                    {t("tokenUsage.noUsageData")}
                                </p>
                            ) : (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>{t("tokenUsage.date")}</TableHead>
                                            <TableHead>{t("tokenUsage.instance")}</TableHead>
                                            <TableHead>{t("tokenUsage.model")}</TableHead>
                                            <TableHead className="text-right">{t("tokenUsage.requests")}</TableHead>
                                            <TableHead className="text-right">{t("tokenUsage.input")}</TableHead>
                                            <TableHead className="text-right">{t("tokenUsage.output")}</TableHead>
                                            <TableHead className="text-right">{t("tokenUsage.total")}</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {report.daily.map((row: TurDailyUsageRow, i: number) => (
                                            <TableRow key={i}>
                                                <TableCell>{row.date}</TableCell>
                                                <TableCell className="font-medium">
                                                    {row.instanceTitle}
                                                </TableCell>
                                                <TableCell className="text-muted-foreground">
                                                    {row.modelName}
                                                </TableCell>
                                                <TableCell className="text-right">
                                                    {row.requestCount.toLocaleString()}
                                                </TableCell>
                                                <TableCell className="text-right">
                                                    {formatTokens(row.inputTokens)}
                                                </TableCell>
                                                <TableCell className="text-right">
                                                    {formatTokens(row.outputTokens)}
                                                </TableCell>
                                                <TableCell className="text-right font-medium">
                                                    {formatTokens(row.totalTokens)}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </CardContent>
                    </Card>
                </>
            )}
        </div>
    );
}
