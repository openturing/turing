import { SubPageHeader } from "@/components/sub.page.header";
import { SectionCard } from "@/components/ui/section-card";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import {
    IconCpu,
    IconDeviceDesktop,
    IconDisc,
    IconInfoCircle,
    IconLoader2,
    IconServer,
} from "@tabler/icons-react";
import axios from "axios";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { toast } from "sonner";

interface ConnectorMemory {
    maxMemory: number;
    totalMemory: number;
    usedMemory: number;
    freeMemory: number;
    totalPhysicalMemory: number;
    freePhysicalMemory: number;
}

interface ConnectorDisk {
    totalSpace: number;
    usableSpace: number;
    usedSpace: number;
}

interface ConnectorSystemInfo {
    appVersion: string;
    appName: string;
    javaVersion: string;
    javaVendor: string;
    javaVmName: string;
    osName: string;
    osVersion: string;
    osArch: string;
    memory: ConnectorMemory;
    disk: ConnectorDisk;
    status: string;
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

function InfoRow({ label, value }: Readonly<{ label: string; value: string }>) {
    return (
        <div className="flex items-center justify-between py-2 border-b last:border-b-0">
            <span className="text-sm text-muted-foreground">{label}</span>
            <span className="text-sm font-medium font-mono">{value}</span>
        </div>
    );
}

function StatusBadge({ status, t }: Readonly<{ status: string; t: (key: string) => string }>) {
    const isUp = status === "UP";
    return (
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
            isUp
                ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                : "bg-red-500/10 text-red-600 dark:text-red-400"
        }`}>
            <span className={`size-1.5 rounded-full ${isUp ? "bg-emerald-500" : "bg-red-500"}`} />
            {isUp ? t("integration.connectorSystemInfo.up") : t("integration.connectorSystemInfo.down")}
        </span>
    );
}

export default function IntegrationInstanceSystemInfoPage() {
    const { t } = useTranslation();
    const { id } = useParams() as { id: string };
    useSubPageBreadcrumb(t("integration.connectorSystemInfo.title"));

    const [info, setInfo] = useState<ConnectorSystemInfo | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [status, setStatus] = useState<string>("DOWN");

    useEffect(() => {
        if (id && id !== "new") {
            axios
                .get<ConnectorSystemInfo>(`/v2/integration/${id}/connector/system-info`)
                .then((res) => {
                    setInfo(res.data);
                    setStatus(res.data.status ?? "UP");
                })
                .catch(() => {
                    setStatus("DOWN");
                    toast.error("Failed to load connector system information.");
                })
                .finally(() => setIsLoading(false));
        } else {
            setIsLoading(false);
        }
    }, [id]);

    if (isLoading) {
        return (
            <>
                <SubPageHeader
                    icon={IconInfoCircle}
                    feature={t("integration.connectorSystemInfo.title")}
                    name={t("integration.connectorSystemInfo.title")}
                    description={t("integration.connectorSystemInfo.description")}
                />
                <div className="flex items-center justify-center py-20">
                    <IconLoader2 className="size-6 animate-spin text-muted-foreground" />
                </div>
            </>
        );
    }

    const mem = info?.memory;
    const disk = info?.disk;
    const heapPercent = mem ? usagePercent(mem.usedMemory, mem.maxMemory) : 0;
    const diskPercent = disk ? usagePercent(disk.usedSpace, disk.totalSpace) : 0;
    const physicalUsed = mem && mem.totalPhysicalMemory > 0
        ? mem.totalPhysicalMemory - mem.freePhysicalMemory
        : 0;
    const physicalPercent = mem && mem.totalPhysicalMemory > 0
        ? usagePercent(physicalUsed, mem.totalPhysicalMemory)
        : -1;

    return (
        <>
            <SubPageHeader
                icon={IconInfoCircle}
                feature="System Information"
                name="System Information"
                description="Connector version and runtime details."
            />
            <div className="py-6 px-6 space-y-4">
                {/* Application */}
                <SectionCard variant="blue">
                    <SectionCard.StaticHeader
                        icon={IconDeviceDesktop}
                        title={t("integration.connectorSystemInfo.application")}
                        description={t("integration.connectorSystemInfo.applicationDesc")}
                    />
                    <SectionCard.Content>
                        <div className="flex items-center justify-between py-2 border-b">
                            <span className="text-sm text-muted-foreground">{t("integration.connectorSystemInfo.status")}</span>
                            <StatusBadge status={status} t={t} />
                        </div>
                        <InfoRow label={t("integration.connectorSystemInfo.name")} value={info?.appName ?? "Unknown"} />
                        <InfoRow label={t("integration.connectorSystemInfo.version")} value={info?.appVersion ?? "Unknown"} />
                        <InfoRow label={t("integration.connectorSystemInfo.javaVersion")} value={info?.javaVersion ?? "Unknown"} />
                        <InfoRow label={t("integration.connectorSystemInfo.javaVendor")} value={info?.javaVendor ?? "Unknown"} />
                        <InfoRow label={t("integration.connectorSystemInfo.jvm")} value={info?.javaVmName ?? "Unknown"} />
                        <InfoRow label={t("integration.connectorSystemInfo.os")} value={`${info?.osName ?? ""} ${info?.osVersion ?? ""} (${info?.osArch ?? ""})`} />
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
                                <span className="font-medium">{heapPercent}%</span>
                            </div>
                            <ProgressBar value={heapPercent} color="bg-emerald-500" />
                            <div className="flex justify-between text-xs text-muted-foreground pt-1">
                                <span>{t("systemInfo.used")} {formatBytes(mem?.usedMemory ?? 0)}</span>
                                <span>{t("systemInfo.max")} {formatBytes(mem?.maxMemory ?? 0)}</span>
                            </div>
                        </div>
                        <InfoRow label={t("systemInfo.totalAllocated")} value={formatBytes(mem?.totalMemory ?? 0)} />
                        <InfoRow label={t("systemInfo.freeAllocated")} value={formatBytes(mem?.freeMemory ?? 0)} />
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
                        <InfoRow label={t("systemInfo.totalLabel")} value={formatBytes(disk?.totalSpace ?? 0)} />
                        <InfoRow label={t("systemInfo.available")} value={formatBytes(disk?.usableSpace ?? 0)} />
                        <InfoRow label={t("systemInfo.usedMemory")} value={formatBytes(disk?.usedSpace ?? 0)} />
                    </SectionCard.Content>
                </SectionCard>
            </div>
        </>
    );
}
