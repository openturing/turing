import type { TurImportResult } from "@/models/marketplace/import-result.model";
import {
    IconBrowserCheck,
    IconCheck,
    IconDatabase,
    IconSettings2,
    IconX,
} from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export function ImportResultPanel({ result }: Readonly<{ result: TurImportResult }>) {
    const { t } = useTranslation();

    if (result.error) {
        return (
            <div className="flex items-center gap-2 rounded-lg bg-red-500/10 border border-red-500/30 px-3 py-2.5 text-red-600 dark:text-red-400">
                <IconX className="size-4 shrink-0" />
                <span className="text-sm font-medium">{result.error}</span>
            </div>
        );
    }

    return (
        <div className="space-y-2">
            <div className="flex items-center gap-2 rounded-lg bg-emerald-500/10 border border-emerald-500/30 px-3 py-2.5 text-emerald-600 dark:text-emerald-400">
                <IconCheck className="size-4" />
                <span className="text-sm font-medium">{t("marketplace.importCompleted")}</span>
            </div>
            <div className="rounded-lg border border-border/60 divide-y divide-border/60 text-sm">
                <div className="flex items-center justify-between px-3 py-2">
                    <span className="text-muted-foreground flex items-center gap-1.5">
                        <IconSettings2 className="size-3.5" />
                        {t("marketplace.resultSite")}
                    </span>
                    <span className="font-medium">{result.siteName}</span>
                </div>
                <div className="flex items-center justify-between px-3 py-2">
                    <span className="text-muted-foreground flex items-center gap-1.5">
                        <IconDatabase className="size-3.5" />
                        {t("marketplace.resultContent")}
                    </span>
                    <span className="font-medium">
                        {result.contentFiles > 0
                            ? t("marketplace.resultDocuments", { count: result.contentDocuments })
                            : t("marketplace.resultNotIncluded")}
                    </span>
                </div>
                <div className="flex items-center justify-between px-3 py-2">
                    <span className="text-muted-foreground flex items-center gap-1.5">
                        <IconBrowserCheck className="size-3.5" />
                        {t("marketplace.resultTemplate")}
                    </span>
                    <span className="font-medium">
                        {result.templateImported
                            ? t("marketplace.resultFiles", { count: result.templateFiles })
                            : t("marketplace.resultNotIncluded")}
                    </span>
                </div>
            </div>
        </div>
    );
}
