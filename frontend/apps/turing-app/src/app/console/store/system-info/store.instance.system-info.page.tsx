import { useStoreInstanceSystemInfo } from "@/api/queries/store-instance.queries";
import { BentoFormSection } from "@/components/bento";
import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconDatabase, IconInfoCircle, IconLoader2 } from "@tabler/icons-react";
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
            {isUp ? t("store.systemInfo.up") : t("store.systemInfo.down")}
        </span>
    );
}

interface StoreInstanceSystemInfoPageProps {
    /** Override the console `SubPageHeader` (e.g. a `BentoHero` in the bento shell). */
    header?: ReactNode;
}

export default function StoreInstanceSystemInfoPage({ header }: Readonly<StoreInstanceSystemInfoPageProps>) {
    const { t } = useTranslation();
    const { id } = useParams() as { id: string };
    useSubPageBreadcrumb(t("store.systemInfo.title"));

    // T1018 — read through the hook. The page kept its own info/loading pair and
    // filled it from the service in an effect; the hook is disabled for the
    // `new` route, which is what the `id !== "new"` guard did, and reports the
    // failure the same way through `isError`.
    const { data: info = {}, isLoading, isError } = useStoreInstanceSystemInfo(id);
    const headerNode = header !== undefined ? header : (
        <SubPageHeader
            icon={IconInfoCircle}
            feature={t("store.systemInfo.title")}
            name={t("store.systemInfo.title")}
            description={t("store.systemInfo.description")}
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
                    {t("store.systemInfo.loadFailed")}
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
        store: t("store.systemInfo.storeLabel"),
        version: t("store.systemInfo.version"),
        error: t("store.systemInfo.error"),
    };

    const entries = Object.entries(info).filter(([key]) => key !== "status");

    const details = (
        <>
            {info.status && (
                <div className="flex items-center justify-between py-2 border-b">
                    <span className="text-sm text-muted-foreground">{t("store.systemInfo.status")}</span>
                    <StatusBadge status={info.status} />
                </div>
            )}
            {entries.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t("store.systemInfo.noInfo")}</p>
            ) : (
                entries.map(([key, value]) => (
                    <InfoRow
                        key={key}
                        label={displayLabels[key] ?? key}
                        value={value || t("store.systemInfo.na")}
                    />
                ))
            )}
        </>
    );

    return (
        <>
            {headerNode}
              <div className="space-y-4">
                  <BentoFormSection
                      icon={IconDatabase}
                      tone="violet"
                      title={t("store.systemInfo.details")}
                      description={t("store.systemInfo.detailsDesc")}
                  >
                      {details}
                  </BentoFormSection>
              </div>
        </>
    );
}
