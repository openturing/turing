import { SubPageHeader } from "@/components/sub.page.header";
import { SectionCard } from "@/components/ui/section-card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import { GradientButton } from "@/components/ui/gradient-button";
import {
    ResizablePanelGroup,
    ResizablePanel,
    ResizableHandle,
} from "@/components/ui/resizable";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurSystemInfo } from "@/models/system/system-info.model";
import { TurSystemInfoService } from "@/services/system/system-info.service";
import type {
    TurAgentBudgetStatus,
    TurCostReport,
} from "@/models/cost-governance/cost-governance.model";
import { TurCostGovernanceService } from "@/services/cost-governance/cost-governance.service";
import { ROUTES } from "@/app/routes.const";
import {
    IconAlertTriangle,
    IconArrowRight,
    IconBucket,
    IconCoin,
    IconCpu,
    IconDatabase,
    IconDeviceDesktop,
    IconDisc,
    IconInfoCircle,
    IconLeaf,
    IconLoader2,
    IconRefresh,
    IconSearch,
    IconServer,
    IconSparkles,
    IconVariable,
    IconX,
} from "@tabler/icons-react";
import axios from "axios";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

const turSystemInfoService = new TurSystemInfoService();
const turCostGovernanceService = new TurCostGovernanceService();

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

interface InsightsResponse {
    success: boolean;
    error: string | null;
    content: string | null;
    canRegenerate: boolean;
}

function formatBytes(bytes: number): string {
    if (bytes < 0) return "N/A";
    if (bytes === 0) return "0 B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${units[i]}`;
}

function usagePercent(used: number, total: number): number {
    if (total <= 0) return 0;
    return Math.round((used / total) * 100);
}

function ProgressBar({ value, color }: Readonly<{ value: number; color: string }>) {
    return (
        <div className="h-2 w-full rounded-full bg-muted">
            <div
                className={`h-2 rounded-full transition-all ${color}`}
                style={{ width: `${Math.min(value, 100)}%` }}
            />
        </div>
    );
}

function InfoRow({ label, value, propertyKey }: Readonly<{ label: string; value: string; propertyKey?: string }>) {
    return (
        <div className="flex items-center justify-between py-2 border-b last:border-b-0">
            <div className="flex flex-col gap-0.5">
                <span className="text-sm text-muted-foreground">{label}</span>
                {propertyKey && (
                    <span className="text-[11px] font-mono text-muted-foreground/60">{propertyKey}</span>
                )}
            </div>
            <span className="text-sm font-medium font-mono">{value}</span>
        </div>
    );
}

function StatusBadge({ status }: Readonly<{ status: string | null | undefined }>) {
    const { t } = useTranslation();
    const isUp = status === "UP";
    return (
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
            isUp
                ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                : "bg-red-500/10 text-red-600 dark:text-red-400"
        }`}>
            <span className={`size-1.5 rounded-full ${isUp ? "bg-emerald-500" : "bg-red-500"}`} />
            {isUp ? t("systemInfo.up") : t("systemInfo.down")}
        </span>
    );
}

/**
 * @param header Optional header rendered above the content. Defaults to the
 * console {@link SubPageHeader}; the Bento shell (T566) passes its own frosted
 * header so the reused surface never pulls the console {@code useSidebar} into a
 * shell that has no SidebarProvider.
 */
export default function SystemInfoPage({ header }: Readonly<{ header?: ReactNode }> = {}) {
    const { t } = useTranslation();
    useSubPageBreadcrumb(t("systemInfo.title"));

    const headerNode: ReactNode = header ?? (
        <SubPageHeader
            icon={IconInfoCircle}
            feature={t("systemInfo.title")}
            name={t("systemInfo.title")}
            description={t("systemInfo.description")}
        />
    );

    const [info, setInfo] = useState<TurSystemInfo | null>(null);
    const [variables, setVariables] = useState<Record<string, string>>({});
    const [isLoading, setIsLoading] = useState(true);
    const [varSearch, setVarSearch] = useState("");

    const [insightsAvailable, setInsightsAvailable] = useState(false);
    const [insightsOpen, setInsightsOpen] = useState(false);
    const [insightsContent, setInsightsContent] = useState<string | null>(null);
    const [insightsError, setInsightsError] = useState<string | null>(null);
    const [insightsLoading, setInsightsLoading] = useState(false);
    const [canRegenerate, setCanRegenerate] = useState(false);

    // T184 / §X.14.d — AI Spend card data (30-day cost report + budget alerts).
    const [costReport, setCostReport] = useState<TurCostReport | null>(null);
    const [budgetStatus, setBudgetStatus] = useState<TurAgentBudgetStatus[]>([]);

    useEffect(() => {
        Promise.all([
            turSystemInfoService.getInfo(),
            turSystemInfoService.getVariables(),
            axios.get<boolean>("/system/info/insights/available").then(r => r.data),
        ])
            .then(([sysInfo, sysVars, available]) => {
                setInfo(sysInfo);
                setVariables(sysVars);
                setInsightsAvailable(available);
            })
            .catch(() => {
                toast.error(t("systemInfo.loadFailed"));
            })
            .finally(() => setIsLoading(false));
    }, []);

    // Fail-soft: a cost API error just hides the AI Spend card, it never blocks
    // the System Info page (the full dashboard lives at Cost Governance).
    useEffect(() => {
        turCostGovernanceService.getSummary()
            .then(setCostReport)
            .catch(() => setCostReport(null));
        turCostGovernanceService.getBudgetStatus()
            .then(setBudgetStatus)
            .catch(() => setBudgetStatus([]));
    }, []);

    const filteredVariables = useMemo(() => {
        const search = varSearch.toLowerCase();
        return Object.entries(variables).filter(
            ([key, value]) =>
                key.toLowerCase().includes(search) ||
                value.toLowerCase().includes(search),
        );
    }, [variables, varSearch]);

    const fetchInsights = useCallback(async (regenerate = false) => {
        setInsightsOpen(true);
        setInsightsLoading(true);
        setInsightsError(null);
        setInsightsContent(null);
        try {
            const response = await axios.get<InsightsResponse>(
                "/system/info/insights",
                { params: regenerate ? { regenerate: true } : {} },
            );
            if (response.data.success && response.data.content) {
                setInsightsContent(response.data.content);
            } else {
                setInsightsError(response.data.error ?? t("systemInfo.insightsFailed"));
            }
            setCanRegenerate(response.data.canRegenerate ?? false);
        } catch {
            setInsightsError(t("systemInfo.insightsFailed"));
        } finally {
            setInsightsLoading(false);
        }
    }, [t]);

    if (isLoading) {
        return (
            <>
                {headerNode}
                <div className="flex items-center justify-center py-20">
                    <IconLoader2 className="size-6 animate-spin text-muted-foreground" />
                </div>
            </>
        );
    }

    const mem = info?.memory;
    const disk = info?.disk;
    const memPercent = mem ? usagePercent(mem.usedMemory, mem.maxMemory) : 0;
    const diskPercent = disk ? usagePercent(disk.usedSpace, disk.totalSpace) : 0;
    const physicalUsed = mem && mem.totalPhysicalMemory > 0
        ? mem.totalPhysicalMemory - mem.freePhysicalMemory
        : 0;
    const physicalPercent = mem && mem.totalPhysicalMemory > 0
        ? usagePercent(physicalUsed, mem.totalPhysicalMemory)
        : -1;
    const swapPercent = mem && mem.totalSwap > 0
        ? usagePercent(mem.totalSwap - mem.freeSwap, mem.totalSwap)
        : -1;

    const mainContent = (
        <>
            {headerNode}
            <div className="py-6 px-6">
                <Tabs defaultValue="overview">
                    <div className="flex items-center justify-between gap-4">
                    <TabsList>
                        <TabsTrigger value="overview">
                            <IconServer className="size-4 mr-1" />
                            {t("systemInfo.overview")}
                        </TabsTrigger>
                        <TabsTrigger value="variables">
                            <IconVariable className="size-4 mr-1" />
                            {t("systemInfo.systemVariables")}
                        </TabsTrigger>
                    </TabsList>
                    {insightsAvailable && (
                        <GradientButton
                            onClick={() => fetchInsights()}
                            disabled={insightsLoading}
                            size="sm"
                        >
                            {insightsLoading
                                ? <IconLoader2 className="size-4 mr-1.5 animate-spin" />
                                : <IconSparkles className="size-4 mr-1.5" />}
                            {t("systemInfo.aiInsights")}
                        </GradientButton>
                    )}
                    </div>

                    <TabsContent value="overview" className="space-y-4 mt-4">
                        {/* T184 — AI Spend (30 days) */}
                        {costReport && (
                            <SectionCard variant="emerald">
                                <SectionCard.StaticHeader
                                    icon={IconCoin}
                                    title={t("systemInfo.aiSpend")}
                                    description={t("systemInfo.aiSpendDesc")}
                                />
                                <SectionCard.Content>
                                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                                        <div className="rounded-lg border p-3">
                                            <div className="text-xs text-muted-foreground">{t("systemInfo.aiSpendTotal")}</div>
                                            <div className="text-lg font-semibold font-mono">{formatUsd(costReport.totalCostUsd)}</div>
                                        </div>
                                        <div className="rounded-lg border p-3">
                                            <div className="text-xs text-muted-foreground">{t("systemInfo.aiSpendTokens")}</div>
                                            <div className="text-lg font-semibold font-mono">{formatTokens(costReport.totalTokens)}</div>
                                        </div>
                                        <div className="rounded-lg border p-3">
                                            <div className="text-xs text-muted-foreground">{t("systemInfo.aiSpendRequests")}</div>
                                            <div className="text-lg font-semibold font-mono">{costReport.totalRequests.toLocaleString()}</div>
                                        </div>
                                        <div className="rounded-lg border p-3">
                                            <div className="text-xs text-muted-foreground">{t("systemInfo.aiSpendPeriod")}</div>
                                            <div className="text-sm font-medium">{costReport.periodStart} → {costReport.periodEnd}</div>
                                        </div>
                                    </div>

                                    {costReport.byStage.length > 0 && (
                                        <div className="pt-3">
                                            <div className="text-xs font-medium text-muted-foreground mb-1">{t("systemInfo.aiSpendByStage")}</div>
                                            {costReport.byStage.slice(0, 4).map((s) => (
                                                <InfoRow key={s.stage ?? "other"} label={s.stage ?? t("systemInfo.aiSpendOther")} value={formatUsd(s.costUsd)} />
                                            ))}
                                        </div>
                                    )}

                                    {budgetStatus.length > 0 && (
                                        <div className="pt-3 space-y-1.5">
                                            <div className="text-xs font-medium text-muted-foreground">{t("systemInfo.aiSpendBudgets")}</div>
                                            {budgetStatus.slice(0, 5).map((b) => {
                                                const alert = b.overBudget || b.projectedOverBudget;
                                                return (
                                                    <div key={b.agentId} className="flex items-center justify-between rounded-md border px-3 py-1.5 text-sm">
                                                        <span className="flex items-center gap-1.5 min-w-0 truncate">
                                                            {alert && <IconAlertTriangle className="size-4 text-amber-500 shrink-0" />}
                                                            <span className="truncate">{b.agentTitle}</span>
                                                        </span>
                                                        <span className={`font-mono shrink-0 ${b.overBudget ? "text-red-600 dark:text-red-400" : alert ? "text-amber-600 dark:text-amber-400" : "text-muted-foreground"}`}>
                                                            {formatUsd(b.monthToDateSpendUsd)} / {formatUsd(b.monthlyBudgetUsd)}
                                                        </span>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    )}

                                    <div className="pt-3">
                                        <Link to={ROUTES.COST_GOVERNANCE} className="inline-flex items-center gap-1 text-sm text-blue-600 dark:text-blue-400 hover:underline">
                                            {t("systemInfo.aiSpendViewFull")}
                                            <IconArrowRight className="size-4" />
                                        </Link>
                                    </div>
                                </SectionCard.Content>
                            </SectionCard>
                        )}

                        {/* Application */}
                        <SectionCard variant="blue">
                            <SectionCard.StaticHeader
                                icon={IconDeviceDesktop}
                                title={t("systemInfo.application")}
                                description={t("systemInfo.applicationDesc")}
                            />
                            <SectionCard.Content>
                                <InfoRow label={t("systemInfo.version")} value={info?.appVersion ?? "Unknown"} />
                                <InfoRow label={t("systemInfo.javaVersion")} value={variables["java.version"] ?? "Unknown"} />
                                <InfoRow label={t("systemInfo.javaVendor")} value={variables["java.vendor"] ?? "Unknown"} />
                                <InfoRow label={t("systemInfo.os")} value={`${variables["os.name"] ?? ""} ${variables["os.version"] ?? ""} (${variables["os.arch"] ?? ""})`} />
                            </SectionCard.Content>
                        </SectionCard>

                        {/* Database */}
                        <SectionCard variant="violet">
                            <SectionCard.StaticHeader
                                icon={IconDatabase}
                                title={t("systemInfo.database")}
                                description={t("systemInfo.databaseDesc")}
                            />
                            <SectionCard.Content>
                                <div className="flex items-center justify-between py-2 border-b">
                                    <span className="text-sm text-muted-foreground">{t("systemInfo.status")}</span>
                                    <StatusBadge status={info?.database.status} />
                                </div>
                                <InfoRow label={t("systemInfo.product")} value={info?.database.productName ?? "Unknown"} />
                                <InfoRow label={t("systemInfo.version")} value={info?.database.productVersion ?? "Unknown"} />
                                <InfoRow label={t("systemInfo.driver")} value={info?.database.driverName ?? "Unknown"} />
                                <InfoRow label={t("systemInfo.driverVersion")} value={info?.database.driverVersion ?? "Unknown"} />
                                <InfoRow label={t("systemInfo.url")} value={info?.database.url ?? "Unknown"} propertyKey="spring.datasource.url" />
                            </SectionCard.Content>
                        </SectionCard>

                        {/* Physical RAM */}
                        {physicalPercent >= 0 && (
                            <SectionCard variant="cyan">
                                <SectionCard.StaticHeader
                                    icon={IconServer}
                                    title={t("systemInfo.physicalMemory")}
                                    description={t("systemInfo.physicalMemoryDesc")}
                                />
                                <SectionCard.Content>
                                    <div className="space-y-1">
                                        <div className="flex justify-between text-sm">
                                            <span className="text-muted-foreground">{t("systemInfo.ramUsage")}</span>
                                            <span className="font-medium">{physicalPercent}%</span>
                                        </div>
                                        <ProgressBar value={physicalPercent} color="bg-cyan-500" />
                                        <div className="flex justify-between text-xs text-muted-foreground pt-1">
                                            <span>{t("systemInfo.used")} {formatBytes(physicalUsed)}</span>
                                            <span>{t("systemInfo.totalLabel")} {formatBytes(mem?.totalPhysicalMemory ?? 0)}</span>
                                        </div>
                                    </div>
                                    <InfoRow label={t("systemInfo.totalRam")} value={formatBytes(mem?.totalPhysicalMemory ?? 0)} />
                                    <InfoRow label={t("systemInfo.freeRam")} value={formatBytes(mem?.freePhysicalMemory ?? 0)} />
                                    <InfoRow label={t("systemInfo.usedRam")} value={formatBytes(physicalUsed)} />
                                    {swapPercent >= 0 && (
                                        <>
                                            <div className="space-y-1 pt-4">
                                                <div className="flex justify-between text-sm">
                                                    <span className="text-muted-foreground">{t("systemInfo.swapUsage")}</span>
                                                    <span className="font-medium">{swapPercent}%</span>
                                                </div>
                                                <ProgressBar value={swapPercent} color="bg-amber-500" />
                                                <div className="flex justify-between text-xs text-muted-foreground pt-1">
                                                    <span>{t("systemInfo.used")} {formatBytes((mem?.totalSwap ?? 0) - (mem?.freeSwap ?? 0))}</span>
                                                    <span>{t("systemInfo.totalLabel")} {formatBytes(mem?.totalSwap ?? 0)}</span>
                                                </div>
                                            </div>
                                            <InfoRow label={t("systemInfo.totalSwap")} value={formatBytes(mem?.totalSwap ?? 0)} />
                                            <InfoRow label={t("systemInfo.freeSwap")} value={formatBytes(mem?.freeSwap ?? 0)} />
                                        </>
                                    )}
                                </SectionCard.Content>
                            </SectionCard>
                        )}

                        {/* JVM Heap Memory */}
                        <SectionCard variant="emerald">
                            <SectionCard.StaticHeader
                                icon={IconCpu}
                                title={t("systemInfo.jvmHeap")}
                                description={t("systemInfo.jvmHeapDesc")}
                            />
                            <SectionCard.Content>
                                <div className="space-y-1">
                                    <div className="flex justify-between text-sm">
                                        <span className="text-muted-foreground">{t("systemInfo.heapUsage")}</span>
                                        <span className="font-medium">{memPercent}%</span>
                                    </div>
                                    <ProgressBar value={memPercent} color="bg-emerald-500" />
                                    <div className="flex justify-between text-xs text-muted-foreground pt-1">
                                        <span>{t("systemInfo.used")} {formatBytes(mem?.usedMemory ?? 0)}</span>
                                        <span>{t("systemInfo.max")} {formatBytes(mem?.maxMemory ?? 0)}</span>
                                    </div>
                                </div>
                                <InfoRow label={t("systemInfo.totalAllocated")} value={formatBytes(mem?.totalMemory ?? 0)} />
                                <InfoRow label={t("systemInfo.freeAllocated")} value={formatBytes(mem?.freeMemory ?? 0)} />
                                <InfoRow label={t("systemInfo.usedMemory")} value={formatBytes(mem?.usedMemory ?? 0)} />
                                <InfoRow label={t("systemInfo.maxHeap")} value={formatBytes(mem?.maxMemory ?? 0)} />
                            </SectionCard.Content>
                        </SectionCard>

                        {/* Disk */}
                        <SectionCard variant="amber">
                            <SectionCard.StaticHeader
                                icon={IconDisc}
                                title={t("systemInfo.diskSpace")}
                                description={t("systemInfo.diskSpaceDesc")}
                            />
                            <SectionCard.Content>
                                <div className="space-y-1">
                                    <div className="flex justify-between text-sm">
                                        <span className="text-muted-foreground">{t("systemInfo.diskUsage")}</span>
                                        <span className="font-medium">{diskPercent}%</span>
                                    </div>
                                    <ProgressBar value={diskPercent} color="bg-amber-500" />
                                    <div className="flex justify-between text-xs text-muted-foreground pt-1">
                                        <span>{t("systemInfo.used")} {formatBytes(disk?.usedSpace ?? 0)}</span>
                                        <span>{t("systemInfo.totalLabel")} {formatBytes(disk?.totalSpace ?? 0)}</span>
                                    </div>
                                </div>
                                <InfoRow label={t("tokenUsage.total")} value={formatBytes(disk?.totalSpace ?? 0)} />
                                <InfoRow label={t("systemInfo.available")} value={formatBytes(disk?.usableSpace ?? 0)} />
                                <InfoRow label={t("systemInfo.usedMemory")} value={formatBytes(disk?.usedSpace ?? 0)} />
                            </SectionCard.Content>
                        </SectionCard>

                        {/* MongoDB */}
                        {info?.mongodb?.enabled && (
                            <SectionCard variant="emerald">
                                <SectionCard.StaticHeader
                                    icon={IconLeaf}
                                    title={t("systemInfo.mongodb")}
                                    description={t("systemInfo.mongodbDesc")}
                                />
                                <SectionCard.Content>
                                    <div className="flex items-center justify-between py-2 border-b">
                                        <span className="text-sm text-muted-foreground">{t("systemInfo.status")}</span>
                                        <StatusBadge status={info.mongodb.status} />
                                    </div>
                                    <InfoRow label={t("systemInfo.version")} value={info.mongodb.version ?? "Unknown"} />
                                    <InfoRow label={t("systemInfo.endpoint")} value={info.mongodb.endpoint ?? "N/A"} propertyKey="turing.mongodb.uri" />
                                </SectionCard.Content>
                            </SectionCard>
                        )}

                        {/* Storage */}
                        {info?.storage?.enabled && (
                            <SectionCard variant="rose">
                                <SectionCard.StaticHeader
                                    icon={IconBucket}
                                    title={`${t("systemInfo.storage")} (${info.storageType ?? "unknown"})`}
                                    description={t("systemInfo.storageDesc")}
                                />
                                <SectionCard.Content>
                                    <div className="flex items-center justify-between py-2 border-b">
                                        <span className="text-sm text-muted-foreground">{t("systemInfo.status")}</span>
                                        <StatusBadge status={info.storage.status} />
                                    </div>
                                    <InfoRow label={t("systemInfo.version")} value={info.storage.version ?? "Unknown"} />
                                    <InfoRow
                                        label={t("systemInfo.endpoint")}
                                        value={info.storage.endpoint ?? "N/A"}
                                        propertyKey={info.storageType === "minio" ? "turing.storage.minio.endpoint" : "turing.storage.filesystem.path"}
                                    />
                                </SectionCard.Content>
                            </SectionCard>
                        )}
                    </TabsContent>

                    <TabsContent value="variables" className="mt-4">
                        <SectionCard variant="slate">
                            <SectionCard.StaticHeader
                                icon={IconVariable}
                                title={t("systemInfo.systemVariables")}
                                description={t("systemInfo.sysVarsDesc")}
                            />
                            <SectionCard.Content>
                                <div className="relative">
                                    <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                                    <Input
                                        className="pl-9 max-w-sm"
                                        placeholder={t("systemInfo.searchVariables")}
                                        value={varSearch}
                                        onChange={(e) => setVarSearch(e.target.value)}
                                    />
                                </div>
                                <div className="rounded-lg border overflow-hidden">
                                    <div className="max-h-[600px] overflow-y-auto">
                                        <table className="w-full text-sm">
                                            <thead className="bg-muted/60 sticky top-0">
                                                <tr>
                                                    <th className="text-left px-4 py-2 font-medium text-muted-foreground w-1/3">{t("systemInfo.property")}</th>
                                                    <th className="text-left px-4 py-2 font-medium text-muted-foreground">{t("systemInfo.value")}</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {filteredVariables.map(([key, value]) => (
                                                    <tr key={key} className="border-t hover:bg-muted/30 transition-colors">
                                                        <td className="px-4 py-2 font-mono text-xs break-all">{key}</td>
                                                        <td className="px-4 py-2 font-mono text-xs break-all text-muted-foreground">{value}</td>
                                                    </tr>
                                                ))}
                                                {filteredVariables.length === 0 && (
                                                    <tr>
                                                        <td colSpan={2} className="px-4 py-8 text-center text-muted-foreground">
                                                            {t("systemInfo.noVariablesMatch")}
                                                        </td>
                                                    </tr>
                                                )}
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                                <p className="text-xs text-muted-foreground">
                                    {t("systemInfo.propertiesShown", { count: filteredVariables.length, total: Object.keys(variables).length })}
                                </p>
                            </SectionCard.Content>
                        </SectionCard>
                    </TabsContent>
                </Tabs>
            </div>
        </>
    );

    if (!insightsOpen) return mainContent;

    return (
        <ResizablePanelGroup orientation="horizontal">
            <ResizablePanel defaultSize="60" minSize="30">
                {mainContent}
            </ResizablePanel>
            <ResizableHandle withHandle />
            <ResizablePanel defaultSize="40" minSize="20" overflowHidden>
                <Card className="h-full flex flex-col m-2 overflow-hidden">
                    <div className="flex items-center justify-between px-4 py-3 border-b bg-muted/30">
                        <div className="flex items-center gap-2 min-w-0">
                            <IconSparkles className="size-4 text-blue-500 shrink-0" />
                            <h3 className="text-sm font-semibold">{t("systemInfo.aiInsights")}</h3>
                            {insightsLoading && (
                                <IconLoader2 className="size-3.5 animate-spin text-muted-foreground shrink-0" />
                            )}
                        </div>
                        <div className="flex items-center gap-1 shrink-0">
                            {!insightsLoading && canRegenerate && (
                                <button
                                    type="button"
                                    onClick={() => fetchInsights(true)}
                                    className="p-1 rounded hover:bg-muted cursor-pointer"
                                    title={t("systemInfo.regenerate")}
                                >
                                    <IconRefresh className="h-4 w-4" />
                                </button>
                            )}
                            <button
                                type="button"
                                onClick={() => { setInsightsOpen(false); setInsightsContent(null); setInsightsError(null); }}
                                className="p-1 rounded hover:bg-muted cursor-pointer"
                                title={t("systemInfo.close")}
                            >
                                <IconX className="h-4 w-4" />
                            </button>
                        </div>
                    </div>
                    <div className="flex-1 overflow-auto p-4">
                        {insightsLoading && (
                            <div className="flex items-center gap-2 text-muted-foreground py-4">
                                <IconLoader2 className="size-4 animate-spin" />
                                <span className="text-sm">{t("systemInfo.insightsGenerating")}</span>
                            </div>
                        )}
                        {insightsError && !insightsLoading && (
                            <p className="text-sm text-destructive">{insightsError}</p>
                        )}
                        {insightsContent && !insightsLoading && (
                            <div className="prose prose-sm dark:prose-invert max-w-none">
                                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                    {insightsContent}
                                </ReactMarkdown>
                            </div>
                        )}
                    </div>
                </Card>
            </ResizablePanel>
        </ResizablePanelGroup>
    );
}
