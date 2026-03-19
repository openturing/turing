import { SubPageHeader } from "@/components/sub.page.header";
import { SectionCard } from "@/components/ui/section-card";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { TurStoreInstanceService } from "@/services/store/store.service";
import { IconDatabase, IconInfoCircle, IconLoader2 } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { toast } from "sonner";

const turStoreInstanceService = new TurStoreInstanceService();

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

export default function StoreInstanceSystemInfoPage() {
    const { id } = useParams() as { id: string };
    useSubPageBreadcrumb("System Information");

    const [info, setInfo] = useState<Record<string, string>>({});
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        if (id && id !== "new") {
            turStoreInstanceService
                .getSystemInfo(id)
                .then(setInfo)
                .catch(() => toast.error("Failed to load embedding store system information."))
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
                    description="Embedding store version and connection details."
                />
                <div className="flex items-center justify-center py-20">
                    <IconLoader2 className="size-6 animate-spin text-muted-foreground" />
                </div>
            </>
        );
    }

    const displayLabels: Record<string, string> = {
        store: "Store",
        version: "Version",
        error: "Error",
    };

    const entries = Object.entries(info).filter(([key]) => key !== "status");

    return (
        <>
            <SubPageHeader
                icon={IconInfoCircle}
                feature="System Information"
                name="System Information"
                description="Embedding store version and connection details."
            />
            <div className="py-6 px-6 space-y-4">
                <SectionCard variant="violet">
                    <SectionCard.StaticHeader
                        icon={IconDatabase}
                        title="Embedding Store Details"
                        description="Version and connection information retrieved from the embedding store."
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
