import { useSeInstanceSystemInfo } from "@/api/queries/se-instance.queries";
import { BentoFormSection } from "@/components/bento";
import { SubPageHeader } from "@/components/sub.page.header";
import { SectionCard } from "@/components/ui/section-card";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconInfoCircle, IconLoader2, IconServer } from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

function InfoRow({ label, value }: Readonly<{ label: string; value: string }>) {
    return (
        <div className="flex items-center justify-between py-2 border-b last:border-b-0">
            <span className="text-sm text-muted-foreground">{label}</span>
            <span className="text-sm font-medium font-mono">{value}</span>
        </div>
    );
}

function StatusBadge({ status }: Readonly<{ status: string }>) {
    const { t } = useTranslation();
    const isUp = status === "UP";
    return (
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
            isUp
                ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                : "bg-red-500/10 text-red-600 dark:text-red-400"
        }`}>
            <span className={`size-1.5 rounded-full ${isUp ? "bg-emerald-500" : "bg-red-500"}`} />
            {isUp ? t("se.systemInfo.up") : t("se.systemInfo.down")}
        </span>
    );
}

interface SEInstanceSystemInfoPageProps {
    /** Override the console `SubPageHeader` (e.g. a `BentoHero` in the bento shell). */
    header?: ReactNode;
}

export default function SEInstanceSystemInfoPage({ header }: Readonly<SEInstanceSystemInfoPageProps>) {
    const { t } = useTranslation();
    const { id } = useParams() as { id: string };
    useSubPageBreadcrumb(t("se.systemInfo.title"));

    // T1018 — read through the hook. The page kept its own info/loading pair and
    // filled it from the service in an effect; the hook is disabled for the
    // `new` route, which is what the `id !== "new"` guard did, and reports the
    // failure the same way through `isError`.
    const { data: info = {}, isLoading, isError } = useSeInstanceSystemInfo(id);
    const headerNode = header !== undefined ? header : (
        <SubPageHeader
            icon={IconInfoCircle}
            feature={t("se.systemInfo.title")}
            name={t("se.systemInfo.title")}
            description={t("se.systemInfo.description")}
        />
    );

    // The failure is rendered rather than toasted. A toast fired from the render
    // body would repeat on every pass while the query stays in error, and the
    // page has room to say what went wrong where the reading would have been.
    if (isError) {
        return (
            <>
                {headerNode}
                <div className="py-20 text-center text-sm text-muted-foreground">
                    {t("se.systemInfo.loadFailed")}
                </div>
            </>
        );
    }

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

    const displayLabels: Record<string, string> = {
        engine: t("se.systemInfo.engine"),
        version: t("se.systemInfo.version"),
        luceneVersion: t("se.systemInfo.luceneVersion"),
        os: t("se.systemInfo.os"),
        javaVersion: t("se.systemInfo.javaVersion"),
        jvmMemory: t("se.systemInfo.jvmMemory"),
        clusterName: t("se.systemInfo.clusterName"),
        buildType: t("se.systemInfo.buildType"),
        indexPath: t("se.systemInfo.indexPath"),
        error: t("se.systemInfo.error"),
    };

    const entries = Object.entries(info).filter(([key]) => key !== "status");
    const isBento = header !== undefined;

    const details = (
        <>
            {info.status && (
                <div className="flex items-center justify-between py-2 border-b">
                    <span className="text-sm text-muted-foreground">{t("se.systemInfo.status")}</span>
                    <StatusBadge status={info.status} />
                </div>
            )}
            {entries.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t("se.systemInfo.noInfo")}</p>
            ) : (
                entries.map(([key, value]) => (
                    <InfoRow
                        key={key}
                        label={displayLabels[key] ?? key}
                        value={value || t("se.systemInfo.na")}
                    />
                ))
            )}
        </>
    );

    return (
        <>
            {headerNode}
            {isBento ? (
                <div className="space-y-4">
                    <BentoFormSection
                        icon={IconServer}
                        tone="emerald"
                        title={t("se.systemInfo.details")}
                        description={t("se.systemInfo.detailsDesc")}
                    >
                        {details}
                    </BentoFormSection>
                </div>
            ) : (
                <div className="py-6 px-6 space-y-4">
                    <SectionCard variant="blue">
                        <SectionCard.StaticHeader
                            icon={IconServer}
                            title={t("se.systemInfo.details")}
                            description={t("se.systemInfo.detailsDesc")}
                        />
                        <SectionCard.Content>{details}</SectionCard.Content>
                    </SectionCard>
                </div>
            )}
        </>
    );
}
