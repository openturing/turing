import { SubPageHeader } from "@/components/sub.page.header";
import { SectionCard } from "@/components/ui/section-card";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { TurSEInstanceService } from "@/services/se/se.service";
import { IconInfoCircle, IconLoader2, IconServer } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { toast } from "sonner";

const turSEInstanceService = new TurSEInstanceService();

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

export default function SEInstanceSystemInfoPage() {
    const { id } = useParams() as { id: string };
    useSubPageBreadcrumb("System Information");

    const [info, setInfo] = useState<Record<string, string>>({});
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        if (id && id !== "new") {
            turSEInstanceService
                .getSystemInfo(id)
                .then(setInfo)
                .catch(() => toast.error("Failed to load search engine system information."))
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
                    description="Search engine version and runtime details."
                />
                <div className="flex items-center justify-center py-20">
                    <IconLoader2 className="size-6 animate-spin text-muted-foreground" />
                </div>
            </>
        );
    }

    const displayLabels: Record<string, string> = {
        engine: "Engine",
        version: "Version",
        luceneVersion: "Lucene Version",
        os: "Operating System",
        javaVersion: "Java Version",
        jvmMemory: "JVM Memory Used",
        clusterName: "Cluster Name",
        buildType: "Build Type",
        indexPath: "Index Path",
        error: "Error",
    };

    const entries = Object.entries(info).filter(([key]) => key !== "status");

    return (
        <>
            <SubPageHeader
                icon={IconInfoCircle}
                feature="System Information"
                name="System Information"
                description="Search engine version and runtime details."
            />
            <div className="py-6 px-6 space-y-4">
                <SectionCard variant="blue">
                    <SectionCard.StaticHeader
                        icon={IconServer}
                        title="Search Engine Details"
                        description="Version and connection information retrieved from the search engine."
                    />
                    <SectionCard.Content>
                        {info.status && (
                            <div className="flex items-center justify-between py-2 border-b">
                                <span className="text-sm text-muted-foreground">Status</span>
                                <StatusBadge status={info.status} />
                            </div>
                        )}
                        {entries.length === 0 ? (
                            <p className="text-sm text-muted-foreground">No information available.</p>
                        ) : (
                            entries.map(([key, value]) => (
                                <InfoRow
                                    key={key}
                                    label={displayLabels[key] ?? key}
                                    value={value || "N/A"}
                                />
                            ))
                        )}
                    </SectionCard.Content>
                </SectionCard>
            </div>
        </>
    );
}
