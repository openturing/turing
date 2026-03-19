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

function StatusBadge({ status }: Readonly<{ status: string }>) {
    const isUp = status === "UP";
    return (
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
            isUp
                ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                : "bg-red-500/10 text-red-600 dark:text-red-400"
        }`}>
            <span className={`size-1.5 rounded-full ${isUp ? "bg-emerald-500" : "bg-red-500"}`} />
            {isUp ? "UP" : "DOWN"}
        </span>
    );
}

export default function IntegrationInstanceSystemInfoPage() {
    const { id } = useParams() as { id: string };
    useSubPageBreadcrumb("System Information");

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
                    feature="System Information"
                    name="System Information"
                    description="Connector version and runtime details."
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
                        title="Application"
                        description="Connector version and runtime environment."
                    />
                    <SectionCard.Content>
                        <div className="flex items-center justify-between py-2 border-b">
                            <span className="text-sm text-muted-foreground">Status</span>
                            <StatusBadge status={status} />
                        </div>
                        <InfoRow label="Name" value={info?.appName ?? "Unknown"} />
                        <InfoRow label="Version" value={info?.appVersion ?? "Unknown"} />
                        <InfoRow label="Java Version" value={info?.javaVersion ?? "Unknown"} />
                        <InfoRow label="Java Vendor" value={info?.javaVendor ?? "Unknown"} />
                        <InfoRow label="JVM" value={info?.javaVmName ?? "Unknown"} />
                        <InfoRow label="OS" value={`${info?.osName ?? ""} ${info?.osVersion ?? ""} (${info?.osArch ?? ""})`} />
                    </SectionCard.Content>
                </SectionCard>

                {/* Physical RAM */}
                {physicalPercent >= 0 && (
                    <SectionCard variant="cyan">
                        <SectionCard.StaticHeader
                            icon={IconServer}
                            title="Physical Memory (RAM)"
                            description="Operating system physical memory."
                        />
                        <SectionCard.Content>
                            <div className="space-y-1">
                                <div className="flex justify-between text-sm">
                                    <span className="text-muted-foreground">RAM Usage</span>
                                    <span className="font-medium">{physicalPercent}%</span>
                                </div>
                                <ProgressBar value={physicalPercent} color="bg-cyan-500" />
                                <div className="flex justify-between text-xs text-muted-foreground pt-1">
                                    <span>Used: {formatBytes(physicalUsed)}</span>
                                    <span>Total: {formatBytes(mem?.totalPhysicalMemory ?? 0)}</span>
                                </div>
                            </div>
                            <InfoRow label="Total RAM" value={formatBytes(mem?.totalPhysicalMemory ?? 0)} />
                            <InfoRow label="Free RAM" value={formatBytes(mem?.freePhysicalMemory ?? 0)} />
                        </SectionCard.Content>
                    </SectionCard>
                )}

                {/* JVM Heap Memory */}
                <SectionCard variant="emerald">
                    <SectionCard.StaticHeader
                        icon={IconCpu}
                        title="Memory (JVM Heap)"
                        description="Java heap memory allocation and usage."
                    />
                    <SectionCard.Content>
                        <div className="space-y-1">
                            <div className="flex justify-between text-sm">
                                <span className="text-muted-foreground">Heap Usage</span>
                                <span className="font-medium">{heapPercent}%</span>
                            </div>
                            <ProgressBar value={heapPercent} color="bg-emerald-500" />
                            <div className="flex justify-between text-xs text-muted-foreground pt-1">
                                <span>Used: {formatBytes(mem?.usedMemory ?? 0)}</span>
                                <span>Max: {formatBytes(mem?.maxMemory ?? 0)}</span>
                            </div>
                        </div>
                        <InfoRow label="Total Allocated" value={formatBytes(mem?.totalMemory ?? 0)} />
                        <InfoRow label="Free (Allocated)" value={formatBytes(mem?.freeMemory ?? 0)} />
                        <InfoRow label="Max Heap" value={formatBytes(mem?.maxMemory ?? 0)} />
                    </SectionCard.Content>
                </SectionCard>

                {/* Disk */}
                <SectionCard variant="amber">
                    <SectionCard.StaticHeader
                        icon={IconDisc}
                        title="Disk Space"
                        description="Storage on the connector's working volume."
                    />
                    <SectionCard.Content>
                        <div className="space-y-1">
                            <div className="flex justify-between text-sm">
                                <span className="text-muted-foreground">Disk Usage</span>
                                <span className="font-medium">{diskPercent}%</span>
                            </div>
                            <ProgressBar value={diskPercent} color="bg-amber-500" />
                            <div className="flex justify-between text-xs text-muted-foreground pt-1">
                                <span>Used: {formatBytes(disk?.usedSpace ?? 0)}</span>
                                <span>Total: {formatBytes(disk?.totalSpace ?? 0)}</span>
                            </div>
                        </div>
                        <InfoRow label="Total" value={formatBytes(disk?.totalSpace ?? 0)} />
                        <InfoRow label="Available" value={formatBytes(disk?.usableSpace ?? 0)} />
                        <InfoRow label="Used" value={formatBytes(disk?.usedSpace ?? 0)} />
                    </SectionCard.Content>
                </SectionCard>
            </div>
        </>
    );
}
