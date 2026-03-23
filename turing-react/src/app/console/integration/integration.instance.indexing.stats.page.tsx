import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurIntegrationIndexingStats } from "@/models/integration/integration-indexing-stats.model";
import { TurIntegrationIndexingStatsService } from "@/services/integration/integration-indexing-stats.service";
import { IconChartBar } from "@tabler/icons-react";
import { format, formatDistanceToNow } from "date-fns";
import { useDateLocale } from "@/hooks/use-date-locale";
import { Clock, RefreshCcw, Zap } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceIndexingStatsPage() {
    const { t } = useTranslation();
    const dateLocale = useDateLocale();
    const { id } = useParams<{ id: string }>();
    const [stats, setStats] = useState<TurIntegrationIndexingStats[]>();
    const [error, setError] = useState<string | null>(null);

    const statsService = useMemo(() => {
        return id ? new TurIntegrationIndexingStatsService(id) : null;
    }, [id]);

    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
    useSubPageBreadcrumb(breadcrumb);

    useEffect(() => {
        if (!statsService || !id) return;

        statsService.getAll().then((data) => {
            setStats(data);
            setBreadcrumb([{ label: t("integration.indexingStats.title") }]);
        }).catch(() => setError(t("integration.indexingStats.loadFailed")));
    }, [id]);

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
            {stats && (
                <div className="px-6">
                    <div className="flex justify-between p-2 items-center text-xs text-muted-foreground">
                        <span>{stats.length} records</span>
                    </div>
                    {stats.length === 0 ? (
                        <Card className="p-8 text-center text-muted-foreground">
                            {t("integration.indexingStats.noStats")}
                        </Card>
                    ) : (
                        <Card>
                            <div className="rounded-md">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead className="px-5">{t("integration.indexingStats.startTime")}</TableHead>
                                            <TableHead className="px-5">{t("integration.indexingStats.source")}</TableHead>
                                            <TableHead className="px-5">{t("integration.indexingStats.operation")}</TableHead>
                                            <TableHead className="px-5">{t("integration.indexingStats.documents")}</TableHead>
                                            <TableHead className="px-5">{t("integration.indexingStats.duration")}</TableHead>
                                            <TableHead className="px-5">{t("integration.indexingStats.docsPerMin")}</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {stats.map((stat) => {
                                            const start = formatDateTime(stat.startTime);
                                            const isReindex = stat.operationType === "REINDEX_ALL";
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
                                                            className={`flex w-fit items-center gap-1.5 px-2 py-0.5 font-mono text-[10px] font-bold tracking-tight uppercase ${isReindex
                                                                ? "bg-amber-500/10 text-amber-600 border-amber-500/20"
                                                                : "bg-blue-500/10 text-blue-600 border-blue-500/20"
                                                                }`}
                                                        >
                                                            {isReindex ? (
                                                                <><RefreshCcw className="h-3 w-3" /> REINDEX ALL</>
                                                            ) : (
                                                                <><Zap className="h-3 w-3" /> INDEX ALL</>
                                                            )}
                                                        </Badge>
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
                    )}
                </div>
            )}
        </LoadProvider>
    );
}
