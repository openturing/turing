import { ROUTES } from "@/app/routes.const";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { GradientButton } from "@/components/ui/gradient-button";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { LoadProvider } from "@/components/loading-provider";
import { Badge } from "@/components/ui/badge";
import type { TurMarketplaceItem } from "@/models/marketplace/marketplace-item.model";
import { TurMarketplaceService } from "@/services/marketplace/marketplace.service";
import * as TablerIcons from "@tabler/icons-react";
import {
    IconBrowserCheck,
    IconCheck,
    IconDatabase,
    IconLoader2,
    IconPackageImport,
    IconSettings2,
    IconX,
} from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const marketplaceService = new TurMarketplaceService();

type ImportStatus = "idle" | "importing" | "success" | "error";

/**
 * Resolves a kebab-case icon key (e.g. "chef-hat") to a Tabler Icon component.
 * Falls back to IconPackageImport when the icon doesn't exist.
 */
function getItemIcon(key: string): React.ReactNode {
    const pascalName = `Icon${key
        .split("-")
        .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
        .join("")}`;
    const IconComponent =
        (TablerIcons as unknown as Record<string, TablerIcons.Icon | undefined>)[pascalName] ?? IconPackageImport;
    return <IconComponent className="size-6" />;
}

export default function MarketplaceListPage() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const [items, setItems] = useState<TurMarketplaceItem[]>();
    const [error, setError] = useState<string | null>(null);

    const [selectedItem, setSelectedItem] = useState<TurMarketplaceItem | null>(null);
    const [includeContent, setIncludeContent] = useState(true);
    const [includeTemplate, setIncludeTemplate] = useState(true);
    const [importStatus, setImportStatus] = useState<ImportStatus>("idle");

    useEffect(() => {
        marketplaceService
            .list()
            .then(setItems)
            .catch(() => setError(t("common.connectionError", { resource: "marketplace" })));
    }, [t]);

    const openImportDialog = useCallback((item: TurMarketplaceItem) => {
        setSelectedItem(item);
        setIncludeContent(item.hasContent);
        setIncludeTemplate(item.hasTemplate);
        setImportStatus("idle");
    }, []);

    const closeDialog = useCallback(() => {
        if (importStatus === "importing") return;
        setSelectedItem(null);
        setImportStatus("idle");
    }, [importStatus]);

    const doImport = useCallback(async () => {
        if (!selectedItem) return;
        setImportStatus("importing");
        try {
            await marketplaceService.importPackage(
                selectedItem.downloadUrl,
                includeContent,
                includeTemplate
            );
            setImportStatus("success");
            toast.success(t("marketplace.importSuccess", { name: selectedItem.title }));
            setTimeout(() => {
                setSelectedItem(null);
                navigate(ROUTES.SN_INSTANCE);
            }, 2000);
        } catch {
            setImportStatus("error");
            toast.error(t("marketplace.importFailed", { name: selectedItem.title }));
        }
    }, [selectedItem, includeContent, includeTemplate, navigate, t]);

    return (
        <div className="px-4 md:px-6 lg:px-8 py-2">
            <LoadProvider checkIsNotUndefined={items} error={error}>
                {items && items.length > 0 ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {items.map((item) => (
                            <Card
                                key={item.id}
                                className="group flex flex-col transition-all hover:shadow-md hover:border-primary/30"
                            >
                                <CardHeader className="pb-3">
                                    <div className="flex items-start gap-3">
                                        <div className="shrink-0 flex items-center justify-center size-10 rounded-lg bg-primary/10 text-primary">
                                            {getItemIcon(item.icon)}
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <CardTitle className="text-base leading-tight">
                                                {item.title}
                                            </CardTitle>
                                            <CardDescription className="text-xs mt-0.5">
                                                {t("marketplace.byAuthor", { author: item.author })}
                                                {" "}&middot;{" "}v{item.version}
                                            </CardDescription>
                                        </div>
                                    </div>
                                </CardHeader>

                                <CardContent className="flex-1 pb-3">
                                    <p className="text-sm text-muted-foreground leading-relaxed">
                                        {item.description}
                                    </p>
                                    <div className="flex flex-wrap gap-1.5 mt-3">
                                        {item.hasContent && (
                                            <Badge variant="secondary" className="text-[10px] gap-1">
                                                <IconDatabase className="size-3" />
                                                {t("marketplace.content")}
                                            </Badge>
                                        )}
                                        {item.hasTemplate && (
                                            <Badge variant="secondary" className="text-[10px] gap-1">
                                                <IconBrowserCheck className="size-3" />
                                                {t("marketplace.template")}
                                            </Badge>
                                        )}
                                        {item.tags.map((tag) => (
                                            <Badge key={tag} variant="outline" className="text-[10px]">
                                                {tag}
                                            </Badge>
                                        ))}
                                    </div>
                                </CardContent>

                                <CardFooter className="pt-0">
                                    <GradientButton
                                        className="w-full"
                                        size="sm"
                                        onClick={() => openImportDialog(item)}
                                    >
                                        <IconPackageImport className="size-4" />
                                        {t("marketplace.install")}
                                    </GradientButton>
                                </CardFooter>
                            </Card>
                        ))}
                    </div>
                ) : (
                    <div className="flex flex-col items-center justify-center py-20 text-center">
                        <div className="rounded-2xl p-4 bg-muted text-muted-foreground mb-4">
                            <IconPackageImport className="size-10" />
                        </div>
                        <p className="text-lg font-semibold">{t("marketplace.emptyTitle")}</p>
                        <p className="text-sm text-muted-foreground mt-1">{t("marketplace.emptyDescription")}</p>
                    </div>
                )}
            </LoadProvider>

            {/* Import dialog */}
            <Dialog open={selectedItem !== null} onOpenChange={(open) => { if (!open) closeDialog(); }}>
                <DialogContent className="sm:max-w-md">
                    {selectedItem && (
                        <div className="space-y-5">
                            <DialogHeader>
                                <DialogTitle className="flex items-center gap-2">
                                    <IconPackageImport className="size-5" />
                                    {t("marketplace.importDialogTitle", { name: selectedItem.title })}
                                </DialogTitle>
                                <DialogDescription>
                                    {t("marketplace.importDialogDescription")}
                                </DialogDescription>
                            </DialogHeader>

                            {/* Package info */}
                            <div className="flex items-center gap-3 rounded-lg bg-muted/50 border border-border/60 px-3 py-2.5">
                                <div className="shrink-0 text-primary">
                                    {getItemIcon(selectedItem.icon)}
                                </div>
                                <div className="flex-1 min-w-0">
                                    <span className="text-sm font-medium">{selectedItem.title}</span>
                                    <span className="text-xs text-muted-foreground ml-2">v{selectedItem.version}</span>
                                </div>
                            </div>

                            {/* Always included */}
                            <div className="flex items-center gap-2.5 rounded-lg bg-primary/5 border border-primary/20 px-3 py-2.5">
                                <IconSettings2 className="size-4 text-primary shrink-0" />
                                <span className="text-sm text-foreground/80">{t("marketplace.configAlwaysIncluded")}</span>
                            </div>

                            {/* Options */}
                            <div className="space-y-2.5">
                                {selectedItem.hasContent && (
                                    <button
                                        type="button"
                                        onClick={() => setIncludeContent(!includeContent)}
                                        disabled={importStatus === "importing"}
                                        className={`flex items-start gap-3 w-full rounded-lg border p-3.5 text-left transition-all ${
                                            includeContent
                                                ? "border-primary/50 bg-primary/5 ring-1 ring-primary/20"
                                                : "border-border hover:border-primary/30 hover:bg-accent/50 cursor-pointer"
                                        }`}
                                    >
                                        <div className={`mt-0.5 shrink-0 flex items-center justify-center size-8 rounded-md ${
                                            includeContent ? "bg-primary/15 text-primary" : "bg-muted text-muted-foreground"
                                        }`}>
                                            <IconDatabase className="size-4" />
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2">
                                                <span className="text-sm font-medium">{t("marketplace.includeContent")}</span>
                                                {includeContent && (
                                                    <span className="inline-flex items-center rounded-full bg-primary/15 px-2 py-0.5 text-[10px] font-semibold text-primary">ON</span>
                                                )}
                                            </div>
                                            <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed">
                                                {t("marketplace.includeContentDescription")}
                                            </p>
                                        </div>
                                    </button>
                                )}

                                {selectedItem.hasTemplate && (
                                    <button
                                        type="button"
                                        onClick={() => setIncludeTemplate(!includeTemplate)}
                                        disabled={importStatus === "importing"}
                                        className={`flex items-start gap-3 w-full rounded-lg border p-3.5 text-left transition-all ${
                                            includeTemplate
                                                ? "border-primary/50 bg-primary/5 ring-1 ring-primary/20"
                                                : "border-border hover:border-primary/30 hover:bg-accent/50 cursor-pointer"
                                        }`}
                                    >
                                        <div className={`mt-0.5 shrink-0 flex items-center justify-center size-8 rounded-md ${
                                            includeTemplate ? "bg-primary/15 text-primary" : "bg-muted text-muted-foreground"
                                        }`}>
                                            <IconBrowserCheck className="size-4" />
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2">
                                                <span className="text-sm font-medium">{t("marketplace.includeTemplate")}</span>
                                                {includeTemplate && (
                                                    <span className="inline-flex items-center rounded-full bg-primary/15 px-2 py-0.5 text-[10px] font-semibold text-primary">ON</span>
                                                )}
                                            </div>
                                            <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed">
                                                {t("marketplace.includeTemplateDescription")}
                                            </p>
                                        </div>
                                    </button>
                                )}
                            </div>

                            {/* Status feedback */}
                            {importStatus === "success" && (
                                <div className="flex items-center gap-2 rounded-lg bg-emerald-500/10 border border-emerald-500/30 px-3 py-2.5 text-emerald-600 dark:text-emerald-400">
                                    <IconCheck className="size-4" />
                                    <span className="text-sm font-medium">{t("marketplace.importCompleted")}</span>
                                </div>
                            )}

                            {importStatus === "error" && (
                                <div className="flex items-center gap-2 rounded-lg bg-red-500/10 border border-red-500/30 px-3 py-2.5 text-red-600 dark:text-red-400">
                                    <IconX className="size-4" />
                                    <span className="text-sm font-medium">{t("marketplace.importError")}</span>
                                </div>
                            )}

                            <DialogFooter>
                                <Button
                                    className="flex-1 h-11"
                                    variant="outline"
                                    onClick={closeDialog}
                                    disabled={importStatus === "importing"}
                                >
                                    {importStatus === "success" ? t("common.close") : t("common.cancel")}
                                </Button>
                                {importStatus !== "success" && (
                                    <GradientButton
                                        className="flex-1"
                                        onClick={doImport}
                                        disabled={importStatus === "importing"}
                                    >
                                        {importStatus === "importing" ? (
                                            <IconLoader2 className="size-4 animate-spin" />
                                        ) : (
                                            <IconPackageImport className="size-4" />
                                        )}
                                        {importStatus === "importing"
                                            ? t("marketplace.installing")
                                            : t("marketplace.install")}
                                    </GradientButton>
                                )}
                            </DialogFooter>
                        </div>
                    )}
                </DialogContent>
            </Dialog>
        </div>
    );
}
