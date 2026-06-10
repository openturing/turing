import { useMarketplaceItems } from "@/api/queries/marketplace.queries";
import { useChatFlowRecipes } from "@/api/queries/chat-flow-recipe.queries";
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
import type { TurChatFlowRecipeSummary } from "@/models/agent/chat-flow-recipe.model";
import type { TurImportResult, TurSiteConflict } from "@/models/marketplace/import-result.model";
import { ImportResultPanel } from "@/components/ui/import-result-panel";
import { ImportOptionsPanel } from "@/components/ui/import-options-panel";
import { ContentExchangeProgressBar } from "@/components/ui/content-exchange-progress";
import type { ContentExchangeProgress } from "@/services/sn/sn.service";
import { TurMarketplaceService } from "@/services/marketplace/marketplace.service";
import { TurFeaturesService } from "@/services/system/features.service";
import { Icon } from "@iconify/react";
import {
    IconArrowsRightLeft,
    IconBolt,
    IconBook,
    IconBox,
    IconBrowserCheck,
    IconDatabase,
    IconLoader2,
    IconPackageImport,
    IconSparkles,
    IconX,
} from "@tabler/icons-react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeHighlight from "rehype-highlight";
import { Skeleton } from "@/components/ui/skeleton";

import { RecipeInstallDialog } from "./recipe-install-dialog";

const marketplaceService = new TurMarketplaceService();
const featuresService = new TurFeaturesService();

type ImportStatus = "idle" | "importing" | "indexing" | "success" | "error";

/**
 * T96 / §VII.11.f — unified storefront discriminator. The marketplace
 * grid mixes Sites (long-standing) and Flow Recipes (T96 — curated
 * chat-flow bundles); the kind filter toggles between them. Future
 * marketplace categories (e.g. Skills) would extend this union without
 * having to restructure the page.
 */
type MarketplaceKind = "site" | "flow-recipe";

const ALL_VERTICALS = "all";
const ALL_KINDS = "all";

interface KindFilterOption {
    id: typeof ALL_KINDS | MarketplaceKind;
    label: string;
    icon: React.ComponentType<{ className?: string }>;
}

/**
 * Resolves a kebab-case icon key (e.g. "chef-hat") to a Tabler icon rendered
 * via Iconify's runtime API. Iconify lazy-fetches the SVG so we don't have to
 * bundle the entire tabler set just to support unknown server-provided keys.
 */
function getItemIcon(key: string): React.ReactNode {
    if (!key) return <IconPackageImport className="size-6" />;
    return <Icon icon={`tabler:${key}`} className="size-6" />;
}

export default function MarketplaceListPage() {
    const { t } = useTranslation();
    const { data: items, isError } = useMarketplaceItems();
    const { data: recipes, isError: recipesError } = useChatFlowRecipes();
    // Don't surface recipe loading errors on the main error panel: the
    // site marketplace is the long-standing feature, recipes are additive.
    const error = isError ? t("common.connectionError", { resource: "marketplace" }) : null;

    /* ─── T96 unified filters ─── */
    const [kindFilter, setKindFilter] = useState<typeof ALL_KINDS | MarketplaceKind>(ALL_KINDS);
    const [verticalFilter, setVerticalFilter] = useState<string>(ALL_VERTICALS);

    /* ─── Recipe install dialog ─── */
    const [selectedRecipe, setSelectedRecipe] = useState<TurChatFlowRecipeSummary | null>(null);

    const [selectedItem, setSelectedItem] = useState<TurMarketplaceItem | null>(null);
    const [includeContent, setIncludeContent] = useState(true);
    const [includeTemplate, setIncludeTemplate] = useState(true);
    const [overwrite, setOverwrite] = useState(false);
    const [conflicts, setConflicts] = useState<TurSiteConflict[]>([]);
    const [conflictsLoading, setConflictsLoading] = useState(false);
    const [storageEnabled, setStorageEnabled] = useState(false);
    const [importStatus, setImportStatus] = useState<ImportStatus>("idle");
    const [importResult, setImportResult] = useState<TurImportResult | null>(null);
    const [contentProgress, setContentProgress] = useState<ContentExchangeProgress | null>(null);

    const [readmeItem, setReadmeItem] = useState<TurMarketplaceItem | null>(null);
    const [readmeContent, setReadmeContent] = useState<string | null>(null);
    const [readmeLoading, setReadmeLoading] = useState(false);
    const [readmeError, setReadmeError] = useState<string | null>(null);

    const openImportDialog = useCallback((item: TurMarketplaceItem) => {
        setSelectedItem(item);
        setIncludeContent(item.hasContent);
        setIncludeTemplate(item.hasTemplate);
        setOverwrite(false);
        setConflicts([]);
        setImportStatus("idle");
        setImportResult(null);
        setContentProgress(null);
        setConflictsLoading(true);
        Promise.all([
            marketplaceService.inspect(item.downloadUrl).catch(() => ({ conflicts: [] as TurSiteConflict[] })),
            featuresService.getFeatures().catch(() => ({ storageEnabled: false, ragEnabled: false })),
        ])
            .then(([inspect, features]) => {
                setConflicts(inspect.conflicts ?? []);
                setStorageEnabled(features.storageEnabled);
            })
            .finally(() => setConflictsLoading(false));
    }, []);

    const isBusy = importStatus === "importing" || importStatus === "indexing";

    const closeDialog = useCallback(() => {
        if (isBusy) return;
        setSelectedItem(null);
        setImportStatus("idle");
        setContentProgress(null);
    }, [isBusy]);

    const openReadmeDialog = useCallback(async (item: TurMarketplaceItem) => {
        if (!item.readmeUrl) return;
        setReadmeItem(item);
        setReadmeContent(null);
        setReadmeError(null);
        setReadmeLoading(true);
        try {
            const md = await marketplaceService.getReadme(item.readmeUrl);
            setReadmeContent(md);
        } catch {
            setReadmeError(t("marketplace.readmeLoadError"));
        } finally {
            setReadmeLoading(false);
        }
    }, [t]);

    const closeReadmeDialog = useCallback(() => {
        setReadmeItem(null);
        setReadmeContent(null);
        setReadmeError(null);
    }, []);

    const doImport = useCallback(async () => {
        if (!selectedItem) return;
        setImportResult(null);
        setContentProgress(null);
        const effectiveIncludeTemplate = includeTemplate && storageEnabled;
        const taskId = `marketplace-${Date.now()}`;
        try {
            if (includeContent) {
                setImportStatus("indexing");
                marketplaceService.subscribeImportProgress(
                    taskId,
                    (data) => setContentProgress(data),
                    () => { },
                    () => { },
                );
            } else {
                setImportStatus("importing");
            }
            const result = await marketplaceService.importPackage(
                selectedItem.downloadUrl,
                includeContent,
                effectiveIncludeTemplate,
                overwrite,
                includeContent ? taskId : undefined,
            );
            setImportResult(result);
            setContentProgress(null);
            if (result.error) {
                setImportStatus("error");
                toast.error(t("marketplace.importFailed", { name: selectedItem.title }));
            } else {
                setImportStatus("success");
                toast.success(t("marketplace.importSuccess", { name: selectedItem.title }));
            }
        } catch {
            setImportStatus("error");
            setContentProgress(null);
            toast.error(t("marketplace.importFailed", { name: selectedItem.title }));
        }
    }, [selectedItem, includeContent, includeTemplate, overwrite, storageEnabled, t]);

    // T96 unified filtering. Kind filter narrows by Sites vs Flow Recipes;
    // vertical filter narrows by tag/segment (Education / Finance / …). The
    // available vertical list is derived from the actual recipes loaded so
    // shipping a new vertical only needs a JSON drop — no UI edit.
    const visibleSites = useMemo(() => {
        if (!items) return [];
        if (kindFilter === "flow-recipe") return [];
        if (verticalFilter !== ALL_VERTICALS) {
            // Sites don't carry a structured `vertical` field — match by
            // tag overlap so "education" tagged sites surface under the
            // Education vertical without back-end changes.
            return items.filter((item) =>
                (item.tags ?? []).some((tag) => tag.toLowerCase() === verticalFilter),
            );
        }
        return items;
    }, [items, kindFilter, verticalFilter]);

    const visibleRecipes = useMemo(() => {
        if (!recipes) return [];
        if (kindFilter === "site") return [];
        if (verticalFilter !== ALL_VERTICALS) {
            return recipes.filter((recipe) => recipe.vertical === verticalFilter);
        }
        return recipes;
    }, [recipes, kindFilter, verticalFilter]);

    const availableVerticals = useMemo(() => {
        const verts = new Set<string>();
        recipes?.forEach((recipe) => verts.add(recipe.vertical));
        // Mirror common site tags so the vertical filter actually narrows
        // sites too. The platform-level verticals (§VIII.3) take priority.
        const sorted = Array.from(verts).sort();
        return sorted;
    }, [recipes]);

    const kindOptions: KindFilterOption[] = [
        { id: ALL_KINDS, label: t("marketplace.kindAll", { defaultValue: "All" }), icon: IconBox },
        { id: "site", label: t("marketplace.kindSites", { defaultValue: "Sites" }), icon: IconBrowserCheck },
        { id: "flow-recipe", label: t("marketplace.kindFlowRecipes", { defaultValue: "Flow Recipes" }), icon: IconSparkles },
    ];

    const showingNothing =
        visibleSites.length === 0 && visibleRecipes.length === 0;

    return (
        <div className="px-4 md:px-6 lg:px-8 py-2 space-y-4">
            {/* T96 — unified filter strip. Lives above the grid so admins
                can flip between "show me everything" / "just chat-flow
                recipes" / "Education-only" without scrolling. Future
                marketplace kinds (e.g. Skills) extend the kindOptions
                array; the rest of the page needs no further wiring. */}
            <div className="flex flex-wrap items-center gap-3 rounded-lg border border-border/60 bg-muted/30 px-3 py-2">
                <div className="flex items-center gap-1.5">
                    {kindOptions.map((opt) => {
                        const Icon = opt.icon;
                        const active = kindFilter === opt.id;
                        return (
                            <button
                                key={opt.id}
                                type="button"
                                onClick={() => setKindFilter(opt.id)}
                                className={
                                    active
                                        ? "inline-flex items-center gap-1.5 rounded-full bg-primary text-primary-foreground px-3 py-1 text-xs font-medium shadow-sm"
                                        : "inline-flex items-center gap-1.5 rounded-full border border-border/60 bg-background px-3 py-1 text-xs text-muted-foreground hover:border-primary/40 hover:text-foreground transition-colors"
                                }
                            >
                                <Icon className="size-3.5" />
                                {opt.label}
                            </button>
                        );
                    })}
                </div>

                {availableVerticals.length > 0 && (
                    <div className="flex items-center gap-1.5 ml-auto">
                        <span className="text-[11px] uppercase tracking-wide text-muted-foreground">
                            {t("marketplace.verticalLabel", { defaultValue: "Vertical" })}
                        </span>
                        <button
                            type="button"
                            onClick={() => setVerticalFilter(ALL_VERTICALS)}
                            className={
                                verticalFilter === ALL_VERTICALS
                                    ? "rounded-full bg-blue-600 text-white px-2.5 py-1 text-[11px] font-medium"
                                    : "rounded-full border border-border/60 bg-background px-2.5 py-1 text-[11px] text-muted-foreground hover:border-blue-500/40 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                            }
                        >
                            {t("marketplace.verticalAll", { defaultValue: "All" })}
                        </button>
                        {availableVerticals.map((vert) => (
                            <button
                                key={vert}
                                type="button"
                                onClick={() => setVerticalFilter(vert)}
                                className={
                                    verticalFilter === vert
                                        ? "rounded-full bg-blue-600 text-white px-2.5 py-1 text-[11px] font-medium capitalize"
                                        : "rounded-full border border-border/60 bg-background px-2.5 py-1 text-[11px] text-muted-foreground capitalize hover:border-blue-500/40 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                                }
                            >
                                {vert}
                            </button>
                        ))}
                    </div>
                )}
            </div>

            <LoadProvider checkIsNotUndefined={items} error={error}>
                {!showingNothing ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {/* Flow Recipes first — T96 is the newer surface and
                            the more "showcase" item; sites flow below. */}
                        {visibleRecipes.map((recipe) => (
                            <FlowRecipeCard
                                key={recipe.id}
                                recipe={recipe}
                                onInstall={() => setSelectedRecipe(recipe)}
                                t={t}
                            />
                        ))}
                        {visibleSites.map((item) => (
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
                                            <div className="flex items-center gap-1.5 mb-0.5">
                                                <Badge variant="outline" className="text-[9px] uppercase tracking-wide">
                                                    {t("marketplace.kindSiteBadge", { defaultValue: "Site" })}
                                                </Badge>
                                            </div>
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

                                <CardFooter className="pt-0 flex-col gap-2 items-stretch">
                                    <GradientButton
                                        className="w-full"
                                        size="sm"
                                        onClick={() => openImportDialog(item)}
                                    >
                                        <IconPackageImport className="size-4" />
                                        {t("marketplace.install")}
                                    </GradientButton>
                                    {item.readmeUrl && (
                                        <button
                                            type="button"
                                            onClick={() => openReadmeDialog(item)}
                                            className="inline-flex items-center justify-center gap-1.5 text-xs text-muted-foreground hover:text-primary transition-colors cursor-pointer"
                                        >
                                            <IconBook className="size-3.5" />
                                            {t("marketplace.readDocumentation")}
                                        </button>
                                    )}
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
                        {(kindFilter !== ALL_KINDS || verticalFilter !== ALL_VERTICALS) && (
                            <Button
                                variant="outline"
                                size="sm"
                                className="mt-4"
                                onClick={() => { setKindFilter(ALL_KINDS); setVerticalFilter(ALL_VERTICALS); }}
                            >
                                {t("marketplace.clearFilters", { defaultValue: "Clear filters" })}
                            </Button>
                        )}
                    </div>
                )}
                {recipesError && (
                    <div className="mt-3 rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
                        {t("marketplace.recipesUnavailable", {
                            defaultValue: "Flow Recipes could not be loaded — showing sites only.",
                        })}
                    </div>
                )}
            </LoadProvider>

            {/* T96 — Flow Recipe install dialog. Separate component so this
                page stays focused on the storefront layout. */}
            <RecipeInstallDialog
                recipe={selectedRecipe}
                onClose={() => setSelectedRecipe(null)}
            />

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

                            <ImportOptionsPanel
                                conflicts={conflicts}
                                conflictsLoading={conflictsLoading}
                                hasContent={selectedItem.hasContent}
                                hasTemplate={selectedItem.hasTemplate}
                                storageEnabled={storageEnabled}
                                values={{ includeContent, includeTemplate, overwrite }}
                                onChange={{
                                    setIncludeContent,
                                    setIncludeTemplate,
                                    setOverwrite,
                                }}
                                disabled={isBusy}
                                labels={{
                                    configAlwaysIncluded: t("marketplace.configAlwaysIncluded"),
                                    checkingConflicts: t("marketplace.checkingConflicts"),
                                    conflictWarningTitle: t("marketplace.conflictWarningTitle", { count: conflicts.length }),
                                    overwriteConfig: t("marketplace.overwriteConfig"),
                                    overwriteConfigOn: t("marketplace.overwriteConfigOn"),
                                    overwriteConfigOff: t("marketplace.overwriteConfigOff"),
                                    includeContent: t("marketplace.includeContent"),
                                    includeContentDescription: t("marketplace.includeContentDescription"),
                                    includeTemplate: t("marketplace.includeTemplate"),
                                    includeTemplateDescription: t("marketplace.includeTemplateDescription"),
                                    templateRequiresStorage: t("exchange.importOptions.templateRequiresStorage"),
                                }}
                            />

                            {importStatus === "indexing" && (
                                <ContentExchangeProgressBar
                                    progress={contentProgress}
                                    label={t("exchange.indexingContent")}
                                    preparingLabel={t("exchange.indexingContent")}
                                />
                            )}

                            {importResult && (importStatus === "success" || importStatus === "error") && (
                                <ImportResultPanel result={importResult} />
                            )}

                            {importStatus === "error" && !importResult && (
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
                                    disabled={isBusy}
                                >
                                    {importStatus === "success" ? t("common.close") : t("common.cancel")}
                                </Button>
                                {importStatus !== "success" && (
                                    <GradientButton
                                        className="flex-1"
                                        onClick={doImport}
                                        disabled={isBusy}
                                    >
                                        {isBusy ? (
                                            <IconLoader2 className="size-4 animate-spin" />
                                        ) : (
                                            <IconPackageImport className="size-4" />
                                        )}
                                        {isBusy
                                            ? t("marketplace.installing")
                                            : t("marketplace.install")}
                                    </GradientButton>
                                )}
                            </DialogFooter>
                        </div>
                    )}
                </DialogContent>
            </Dialog>

            {/* README dialog */}
            <Dialog open={readmeItem !== null} onOpenChange={(open) => { if (!open) closeReadmeDialog(); }}>
                <DialogContent className="sm:max-w-3xl max-h-[85vh] flex flex-col">
                    {readmeItem && (
                        <>
                            <DialogHeader>
                                <DialogTitle className="flex items-center gap-2">
                                    <IconBook className="size-5" />
                                    {readmeItem.title}
                                </DialogTitle>
                                <DialogDescription>
                                    {t("marketplace.readmeDialogDescription")}
                                </DialogDescription>
                            </DialogHeader>

                            <div className="overflow-y-auto flex-1 -mx-1 px-1 py-2">
                                {readmeLoading && (
                                    <div className="space-y-3">
                                        <Skeleton className="h-6 w-2/3" />
                                        <Skeleton className="h-4 w-full" />
                                        <Skeleton className="h-4 w-5/6" />
                                        <Skeleton className="h-4 w-3/4" />
                                        <Skeleton className="h-4 w-full" />
                                        <Skeleton className="h-4 w-4/5" />
                                    </div>
                                )}
                                {readmeError && !readmeLoading && (
                                    <div className="flex items-center gap-2 rounded-lg bg-red-500/10 border border-red-500/30 px-3 py-2.5 text-red-600 dark:text-red-400">
                                        <IconX className="size-4" />
                                        <span className="text-sm font-medium">{readmeError}</span>
                                    </div>
                                )}
                                {readmeContent && !readmeLoading && (
                                    <div className="prose prose-sm dark:prose-invert max-w-none">
                                        <ReactMarkdown
                                            remarkPlugins={[remarkGfm]}
                                            rehypePlugins={[rehypeHighlight]}
                                        >
                                            {readmeContent}
                                        </ReactMarkdown>
                                    </div>
                                )}
                            </div>

                            <DialogFooter>
                                {readmeItem.readmeUrl && (
                                    <a
                                        href={readmeItem.readmeUrl}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="text-xs text-muted-foreground hover:text-primary transition-colors mr-auto self-center"
                                    >
                                        {t("marketplace.readmeOpenSource")}
                                    </a>
                                )}
                                <Button variant="outline" size="default" className="h-10 px-6" onClick={closeReadmeDialog}>
                                    {t("common.close")}
                                </Button>
                                <GradientButton
                                    size="default"
                                    className="h-10 px-6"
                                    onClick={() => {
                                        const item = readmeItem;
                                        closeReadmeDialog();
                                        openImportDialog(item);
                                    }}
                                >
                                    <IconPackageImport className="size-4" />
                                    {t("marketplace.install")}
                                </GradientButton>
                            </DialogFooter>
                        </>
                    )}
                </DialogContent>
            </Dialog>
        </div>
    );
}

/**
 * T96 — visual card for a Flow Recipe inside the unified marketplace
 * storefront. Same outer shape as the site cards so the grid feels
 * cohesive; differentiated by the violet sparkle accent + the "Flow
 * Recipe" badge.
 *
 * Cards lead with the vertical chip so a customer scanning the page
 * can spot education / finance / healthcare bundles at a glance.
 */
function FlowRecipeCard({
    recipe,
    onInstall,
    t,
}: Readonly<{
    recipe: TurChatFlowRecipeSummary;
    onInstall: () => void;
    t: (key: string, options?: Record<string, unknown>) => string;
}>) {
    return (
        <Card className="group flex flex-col transition-all hover:shadow-md hover:border-blue-500/30">
            <CardHeader className="pb-3">
                <div className="flex items-start gap-3">
                    <div className="shrink-0 flex items-center justify-center size-10 rounded-lg bg-linear-to-br from-blue-500/10 to-indigo-500/10 text-blue-600 dark:text-blue-400">
                        <IconSparkles className="size-6" />
                    </div>
                    <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-1.5 mb-0.5">
                            <Badge
                                variant="outline"
                                className="text-[9px] uppercase tracking-wide border-blue-500/40 text-blue-600 dark:text-blue-400"
                            >
                                {t("marketplace.kindFlowRecipeBadge", { defaultValue: "Flow Recipe" })}
                            </Badge>
                            <Badge variant="secondary" className="text-[9px] capitalize">
                                {recipe.vertical}
                            </Badge>
                        </div>
                        <CardTitle className="text-base leading-tight">{recipe.name}</CardTitle>
                        <CardDescription className="text-xs mt-0.5">
                            v{recipe.version}
                        </CardDescription>
                    </div>
                </div>
            </CardHeader>

            <CardContent className="flex-1 pb-3">
                <p className="text-sm text-muted-foreground leading-relaxed line-clamp-4">
                    {recipe.description}
                </p>
                <div className="flex flex-wrap gap-1.5 mt-3">
                    <Badge variant="secondary" className="text-[10px] gap-1">
                        <IconBox className="size-3" />
                        {t("marketplace.recipeFlowsBadge", {
                            count: recipe.flowCount,
                            defaultValue: "{{count}} flow",
                        })}
                    </Badge>
                    {recipe.slotCount > 0 && (
                        <Badge variant="secondary" className="text-[10px] gap-1">
                            <IconArrowsRightLeft className="size-3" />
                            {t("marketplace.recipeSlotsBadge", {
                                count: recipe.slotCount,
                                defaultValue: "{{count}} slots",
                            })}
                        </Badge>
                    )}
                    {recipe.personaCount > 0 && (
                        <Badge variant="secondary" className="text-[10px] gap-1">
                            <IconBolt className="size-3" />
                            {t("marketplace.recipePersonasBadge", {
                                count: recipe.personaCount,
                                defaultValue: "{{count}} personas",
                            })}
                        </Badge>
                    )}
                    {recipe.tags.map((tag) => (
                        <Badge key={tag} variant="outline" className="text-[10px]">
                            {tag}
                        </Badge>
                    ))}
                </div>
            </CardContent>

            <CardFooter className="pt-0">
                <GradientButton className="w-full" size="sm" onClick={onInstall}>
                    <IconPackageImport className="size-4" />
                    {t("marketplace.installRecipe", { defaultValue: "Install" })}
                </GradientButton>
            </CardFooter>
        </Card>
    );
}
