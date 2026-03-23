import { SubPageHeader } from "@/components/sub.page.header";
import { SectionCard } from "@/components/ui/section-card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Input } from "@/components/ui/input";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurSystemInfo } from "@/models/system/system-info.model";
import { TurSystemInfoService } from "@/services/system/system-info.service";
import {
    IconBucket,
    IconCpu,
    IconDatabase,
    IconDeviceDesktop,
    IconDisc,
    IconInfoCircle,
    IconLeaf,
    IconLoader2,
    IconSearch,
    IconServer,
    IconVariable,
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

const turSystemInfoService = new TurSystemInfoService();

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

function InfoRow({ label, value }: Readonly<{ label: string; value: string }>) {
    return (
        <div className="flex items-center justify-between py-2 border-b last:border-b-0">
            <span className="text-sm text-muted-foreground">{label}</span>
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

export default function SystemInfoPage() {
    const { t } = useTranslation();
    useSubPageBreadcrumb(t("systemInfo.title"));

    const [info, setInfo] = useState<TurSystemInfo | null>(null);
    const [variables, setVariables] = useState<Record<string, string>>({});
    const [isLoading, setIsLoading] = useState(true);
    const [varSearch, setVarSearch] = useState("");

    useEffect(() => {
        Promise.all([
            turSystemInfoService.getInfo(),
            turSystemInfoService.getVariables(),
        ])
            .then(([sysInfo, sysVars]) => {
                setInfo(sysInfo);
                setVariables(sysVars);
            })
            .catch(() => {
                toast.error(t("systemInfo.loadFailed"));
            })
            .finally(() => setIsLoading(false));
    }, []);

    const filteredVariables = useMemo(() => {
        const search = varSearch.toLowerCase();
        return Object.entries(variables).filter(
            ([key, value]) =>
                key.toLowerCase().includes(search) ||
                value.toLowerCase().includes(search),
        );
    }, [variables, varSearch]);

    if (isLoading) {
        return (
            <>
                <SubPageHeader
                    icon={IconInfoCircle}
                    feature={t("systemInfo.title")}
                    name={t("systemInfo.title")}
                    description={t("systemInfo.description")}
                />
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

    return (
        <>
            <SubPageHeader
                icon={IconInfoCircle}
                feature="System Information"
                name="System Information"
                description="Runtime environment and system details."
            />
            <div className="py-6 px-6">
                <Tabs defaultValue="overview">
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

                    <TabsContent value="overview" className="space-y-4 mt-4">
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
                                <InfoRow label={t("systemInfo.url")} value={info?.database.url ?? "Unknown"} />
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
                                    <InfoRow label={t("systemInfo.endpoint")} value={info.mongodb.endpoint ?? "N/A"} />
                                </SectionCard.Content>
                            </SectionCard>
                        )}

                        {/* MinIO */}
                        {info?.minio?.enabled && (
                            <SectionCard variant="rose">
                                <SectionCard.StaticHeader
                                    icon={IconBucket}
                                    title={t("systemInfo.minio")}
                                    description={t("systemInfo.minioDesc")}
                                />
                                <SectionCard.Content>
                                    <div className="flex items-center justify-between py-2 border-b">
                                        <span className="text-sm text-muted-foreground">{t("systemInfo.status")}</span>
                                        <StatusBadge status={info.minio.status} />
                                    </div>
                                    <InfoRow label={t("systemInfo.version")} value={info.minio.version ?? "Unknown"} />
                                    <InfoRow label={t("systemInfo.endpoint")} value={info.minio.endpoint ?? "N/A"} />
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
}
