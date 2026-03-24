import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurIntegrationIndexingStats } from "@/models/integration/integration-indexing-stats.model";
import { TurIntegrationIndexingStatsService } from "@/services/integration/integration-indexing-stats.service";
import { IconChartBar } from "@tabler/icons-react";
import { format, formatDistanceToNow } from "date-fns";
import { useDateLocale } from "@/hooks/use-date-locale";
import { Clock, Database, Globe, MapPin, RefreshCcw, Search, TrendingUp, Zap } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

function getProgressBarColor(pct: number): string {
    if (pct >= 90) return "*:data-[slot=progress-indicator]:bg-emerald-500";
    if (pct >= 50) return "*:data-[slot=progress-indicator]:bg-amber-500";
    return "*:data-[slot=progress-indicator]:bg-red-500";
}

function getProgressTextColor(pct: number): string {
    if (pct >= 90) return "text-emerald-600";
    if (pct >= 50) return "text-amber-600";
    return "text-red-500";
}

const REFRESH_OPTIONS = [
    { label: "Off", value: 0 },
    { label: "1s", value: 1000 },
    { label: "5s", value: 5000 },
    { label: "10s", value: 10000 },
    { label: "30s", value: 30000 },
    { label: "1m", value: 60000 },
    { label: "5m", value: 300000 },
];

interface SourceSummary {
    source: string;
    latestReindex: TurIntegrationIndexingStats;
    currentCount: number;
    progressPercent: number;
}

export default function IntegrationInstanceIndexingStatsPage() {
    const { t } = useTranslation();
    const dateLocale = useDateLocale();
    const { id } = useParams<{ id: string }>();
    const [stats, setStats] = useState<TurIntegrationIndexingStats[]>();
    const [countBySource, setCountBySource] = useState<Record<string, number>>();
    const [error, setError] = useState<string | null>(null);

    const statsService = useMemo(() => {
        return id ? new TurIntegrationIndexingStatsService(id) : null;
    }, [id]);

    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
    useSubPageBreadcrumb(breadcrumb);
    const [refreshInterval, setRefreshInterval] = useState(5000);
    const [lastUpdated, setLastUpdated] = useState(Date.now());

    const fetchData = useCallback(async (isSilent = false) => {
        if (!statsService || !id) return;
        try {
            const [data, counts] = await Promise.all([
                statsService.getAll(),
                statsService.getCountBySource(),
            ]);
            setStats(data);
            setCountBySource(counts);
            setLastUpdated(Date.now());
            if (!isSilent) {
                setBreadcrumb([{ label: t("integration.indexingStats.title") }]);
            }
        } catch {
            if (!isSilent) setError(t("integration.indexingStats.loadFailed"));
        }
    }, [statsService, id, t]);

    useEffect(() => {
        let isMounted = true;

        const doFetch = (silent: boolean) => {
            if (isMounted) fetchData(silent);
        };

        doFetch(false);

        let interval: ReturnType<typeof setInterval>;
        if (refreshInterval > 0) {
            interval = setInterval(() => doFetch(true), refreshInterval);
        }

        return () => {
            isMounted = false;
            if (interval) clearInterval(interval);
        };
    }, [fetchData, refreshInterval]);

    const sourceSummaries = useMemo<SourceSummary[]>(() => {
        if (!stats || !countBySource) return [];
        const filteredStats = stats.filter((s) => s.operationType === "REINDEX_ALL");
        const latestBySource = new Map<string, TurIntegrationIndexingStats>();
        for (const stat of filteredStats) {
            if (!latestBySource.has(stat.source)) {
                latestBySource.set(stat.source, stat);
            }
        }
        return Array.from(latestBySource.entries()).map(([source, latest]) => {
            const current = countBySource[source] ?? 0;
            const pct = latest.documentCount > 0
                ? Math.min((current / latest.documentCount) * 100, 100)
                : 0;
            return { source, latestReindex: latest, currentCount: current, progressPercent: pct };
        });
    }, [stats, countBySource]);

    const filteredStats = useMemo(() => {
        return stats ?? [];
    }, [stats]);

    if (!id) {
        return <div>{t("integration.indexingStats.invalidId")}</div>;
    }

    function formatDuration(start: string, end: string): string {
        const durationMs = new Date(end).getTime() - new Date(start).getTime();
        if (durationMs < 1000) return `${durationMs}ms`;
        if (durationMs < 60000) return `${(durationMs / 1000).toFixed(1)}s`;
        const minutes = Math.floor(durationMs / 60000);
        const seconds = Math.floor((durationMs % 60000) / 1000);
        return `${minutes}m ${seconds}s`;
    }

    function formatDateTime(dateStr: string): { formatted: string; timeAgo: string } {
        const date = new Date(dateStr);
        const formatted = format(date, "dd/MM/yy HH:mm:ss", { locale: dateLocale });
        const timeAgo = formatDistanceToNow(date, { addSuffix: true, locale: dateLocale });
        return { formatted, timeAgo };
    }

    return (
        <LoadProvider
            checkIsNotUndefined={stats}
            error={error}
            tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/indexing-stats`}
        >
            <SubPageHeader
                icon={IconChartBar}
                name={t("integration.indexingStats.title")}
                feature={t("integration.indexingStats.title")}
                description={t("integration.indexingStats.description")}
            />
            <div className="flex items-center justify-end px-6 py-2 gap-2">
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    <RefreshCcw className="h-3 w-3 animate-spin-slow" />
                    {t("forms.loggingGrid.lastSync", { time: new Date(lastUpdated).toLocaleTimeString() })}
                </div>
                <span className="text-xs text-muted-foreground italic">{t("integration.indexingManager.autoRefreshing")}</span>
                <Select
                    value={String(refreshInterval)}
                    onValueChange={(v) => setRefreshInterval(Number(v))}
                >
                    <SelectTrigger className="w-20 h-8 text-xs font-mono">
                        <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                        {REFRESH_OPTIONS.map((opt) => (
                            <SelectItem key={opt.value} value={String(opt.value)} className="text-xs">
                                {opt.label}
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </div>
            {stats && (
                <div className="px-6 space-y-4">
                    {sourceSummaries.length > 0 ? (
                        <div className="grid grid-cols-1 gap-4">
                            {sourceSummaries.map((summary) => {
                                const lastDate = formatDateTime(summary.latestReindex.startTime);
                                return (
                                    <Card key={summary.source} className="p-6">
                                        <div className="flex items-center gap-2 mb-4">
                                            <RefreshCcw className="h-5 w-5 text-amber-600" />
                                            <h3 className="text-sm font-semibold uppercase tracking-wide">
                                                {t("integration.indexingStats.reindexSummary")}
                                            </h3>
                                            <Badge variant="outline" className="font-mono text-xs px-2 py-0.5">
                                                {summary.source}
                                            </Badge>
                                        </div>
                                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                            <div className="space-y-1">
                                                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                                    <Database className="h-3.5 w-3.5" />
                                                    {t("integration.indexingStats.currentDocuments")}
                                                </div>
                                                <div className="text-2xl font-bold font-mono">
                                                    {summary.currentCount.toLocaleString()}
                                                    <span className={`ml-2 text-sm font-semibold ${getProgressTextColor(summary.progressPercent)}`}>
                                                        ({summary.progressPercent.toFixed(1)}% {t("integration.indexingStats.estimatedProgress")})
                                                    </span>
                                                </div>
                                            </div>
                                            <div className="space-y-1">
                                                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                                    <RefreshCcw className="h-3.5 w-3.5" />
                                                    {t("integration.indexingStats.lastReindex")}
                                                </div>
                                                <div className="text-2xl font-bold font-mono">
                                                    {summary.latestReindex.documentCount.toLocaleString()}
                                                    <span className="ml-2 text-sm font-normal text-muted-foreground">
                                                        {t("integration.indexingStats.processedInLastReindex")}
                                                    </span>
                                                </div>
                                                <div className="text-xs text-muted-foreground italic">
                                                    {lastDate.formatted} ({lastDate.timeAgo})
                                                </div>
                                            </div>
                                            <div className="space-y-1">
                                                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                                    <TrendingUp className="h-3.5 w-3.5" />
                                                    {t("integration.indexingStats.throughput")}
                                                </div>
                                                <div className="text-2xl font-bold font-mono">
                                                    {summary.latestReindex.documentsPerMinute.toFixed(1)}
                                                    <span className="ml-1 text-sm font-normal text-muted-foreground">
                                                        docs/min
                                                    </span>
                                                </div>
                                            </div>
                                        </div>
                                        <div className="mt-4">
                                            <Progress
                                                value={summary.progressPercent}
                                                className={`h-2 ${getProgressBarColor(summary.progressPercent)}`}
                                            />
                                        </div>
                                    </Card>
                                );
                            })}
                        </div>
                    ) : (
                        <Card className="p-6 text-center text-muted-foreground">
                            {t("integration.indexingStats.noReindexStats")}
                        </Card>
                    )}

                    {filteredStats.length > 0 && (
                        <>
                            <div className="flex justify-between p-2 items-center text-xs text-muted-foreground">
                                <span>{filteredStats.length} records</span>
                            </div>
                            <Card>
                                <div className="rounded-md">
                                    <Table>
                                        <TableHeader>
                                            <TableRow>
                                                <TableHead className="px-5">{t("integration.indexingStats.startTime")}</TableHead>
                                                <TableHead className="px-5">{t("integration.indexingStats.source")}</TableHead>
                                                <TableHead className="px-5">{t("integration.indexingStats.operation")}</TableHead>
                                                <TableHead className="px-5">{t("integration.indexingStats.environment")}</TableHead>
                                                <TableHead className="px-5">{t("integration.indexingStats.locale")}</TableHead>
                                                <TableHead className="px-5">{t("integration.indexingStats.site")}</TableHead>
                                                <TableHead className="px-5">{t("integration.indexingStats.documents")}</TableHead>
                                                <TableHead className="px-5">{t("integration.indexingStats.duration")}</TableHead>
                                                <TableHead className="px-5">{t("integration.indexingStats.docsPerMin")}</TableHead>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {filteredStats.map((stat) => {
                                                const start = formatDateTime(stat.startTime);
                                                const isDryScan = stat.operationType === "DRY_SCAN";
                                                const isIndexAll = stat.operationType === "INDEX_ALL";
                                                return (
                                                    <TableRow key={stat.id}>
                                                        <TableCell className="px-5 py-3">
                                                            <div className="flex flex-col">
                                                                <span className="font-mono text-sm">{start.formatted}</span>
                                                                <span className="text-xs text-muted-foreground italic">
                                                                    ({start.timeAgo})
                                                                </span>
                                                            </div>
                                                        </TableCell>
                                                        <TableCell className="px-5 py-3">
                                                            <Badge
                                                                variant="outline"
                                                                className="font-mono text-xs px-2 py-0.5"
                                                            >
                                                                {stat.source}
                                                            </Badge>
                                                        </TableCell>
                                                        <TableCell className="px-5 py-3">
                                                            <Badge
                                                                variant="outline"
                                                                className={`flex w-fit items-center gap-1.5 px-2 py-0.5 font-mono text-[10px] font-bold tracking-tight uppercase ${isDryScan
                                                                    ? "bg-violet-500/10 text-violet-600 border-violet-500/20"
                                                                    : isIndexAll
                                                                        ? "bg-blue-500/10 text-blue-600 border-blue-500/20"
                                                                        : "bg-amber-500/10 text-amber-600 border-amber-500/20"
                                                                }`}
                                                            >
                                                                {isDryScan ? (
                                                                    <><Search className="h-3 w-3" /> DRY SCAN</>
                                                                ) : isIndexAll ? (
                                                                    <><Zap className="h-3 w-3" /> INDEX ALL</>
                                                                ) : (
                                                                    <><RefreshCcw className="h-3 w-3" /> REINDEX ALL</>
                                                                )}
                                                            </Badge>
                                                        </TableCell>
                                                        <TableCell className="px-5 py-3">
                                                            {stat.environment ? (
                                                                <div className="flex items-center gap-1.5">
                                                                    <Globe className="h-3.5 w-3.5 text-muted-foreground" />
                                                                    <span className="text-sm">{stat.environment}</span>
                                                                </div>
                                                            ) : (
                                                                <span className="text-xs text-muted-foreground">—</span>
                                                            )}
                                                        </TableCell>
                                                        <TableCell className="px-5 py-3">
                                                            {stat.locale ? (
                                                                <Badge variant="outline" className="font-mono text-xs px-2 py-0.5">
                                                                    {stat.locale}
                                                                </Badge>
                                                            ) : (
                                                                <span className="text-xs text-muted-foreground">—</span>
                                                            )}
                                                        </TableCell>
                                                        <TableCell className="px-5 py-3">
                                                            {stat.sites?.length > 0 ? (
                                                                <div className="flex flex-wrap gap-1">
                                                                    {stat.sites.map((site) => (
                                                                        <Badge key={site} variant="outline" className="flex items-center gap-1 font-mono text-xs px-2 py-0.5">
                                                                            <MapPin className="h-3 w-3" />
                                                                            {site}
                                                                        </Badge>
                                                                    ))}
                                                                </div>
                                                            ) : (
                                                                <span className="text-xs text-muted-foreground">—</span>
                                                            )}
                                                        </TableCell>
                                                        <TableCell className="px-5 py-3">
                                                            <span className="font-mono text-sm font-semibold">
                                                                {stat.documentCount.toLocaleString()}
                                                            </span>
                                                        </TableCell>
                                                        <TableCell className="px-5 py-3">
                                                            <div className="flex items-center gap-1.5 text-sm text-muted-foreground">
                                                                <Clock className="h-3.5 w-3.5" />
                                                                <span className="font-mono">
                                                                    {formatDuration(stat.startTime, stat.endTime)}
                                                                </span>
                                                            </div>
                                                        </TableCell>
                                                        <TableCell className="px-5 py-3">
                                                            <Badge
                                                                variant="outline"
                                                                className="bg-emerald-500/10 text-emerald-600 border-emerald-500/20 font-mono text-xs px-2 py-0.5"
                                                            >
                                                                {stat.documentsPerMinute.toFixed(1)} docs/min
                                                            </Badge>
                                                        </TableCell>
                                                    </TableRow>
                                                );
                                            })}
                                        </TableBody>
                                    </Table>
                                </div>
                            </Card>
                        </>
                    )}
                </div>
            )}
        </LoadProvider>
    );
}
