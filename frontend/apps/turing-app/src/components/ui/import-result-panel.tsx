import type { TurImportResult } from "@/models/marketplace/import-result.model";
import {
    IconAlertTriangle,
    IconBrowserCheck,
    IconCheck,
    IconDatabase,
    IconRobot,
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
            {result.searchEngineAvailable === false && (
                <div className="flex items-start gap-2 rounded-lg bg-amber-500/10 border border-amber-500/30 px-3 py-2.5 text-amber-700 dark:text-amber-400">
                    <IconAlertTriangle className="size-4 mt-0.5 shrink-0" />
                    <span className="text-sm font-medium">
                        {t("marketplace.searchEngineUnavailable")}
                    </span>
                </div>
            )}
            <div className="rounded-lg border border-border/60 divide-y divide-border/60 text-sm">
                {result.siteImported && (
                    <>
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
                    </>
                )}
                {result.agentSummary && (
                    <div className="flex items-center justify-between px-3 py-2">
                        <span className="text-muted-foreground flex items-center gap-1.5">
                            <IconRobot className="size-3.5" />
                            {t("marketplace.resultAgents", { defaultValue: "AI Agents" })}
                        </span>
                        <span className="font-medium">
                            {t("marketplace.resultAgentsCount", {
                                defaultValue: "{{imported}} imported, {{skipped}} skipped",
                                imported: result.agentSummary.imported.length,
                                skipped: result.agentSummary.skipped.length,
                            })}
                        </span>
                    </div>
                )}
            </div>
        </div>
    );
}
