import {
    useInstallSkillFromCatalog,
    useSkillCatalog,
    useSkills,
    useUninstallSkill,
} from "@/api/queries/skill.queries";
import { LoadProvider } from "@/components/loading-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import type { TurSkill, TurSkillManifest } from "@/models/skill/skill.model";
import {
    IconCircleCheck,
    IconEye,
    IconLoader2,
    IconLock,
    IconPackageImport,
    IconPackages,
    IconSearch,
    IconTool,
} from "@tabler/icons-react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";

import { SkillDetailDialog, type SkillGroup } from "./skill-detail-dialog";

const ALL_VENDORS = "all";
type KindFilter = "all" | "installed" | "available";

/**
 * T117 / §IX.4.b — Skills marketplace storefront. Browses the bundled platform
 * catalog merged with installed skills, with search, a kind filter
 * (all / installed / available) and a vendor (author) filter. Cards install
 * from the catalog or uninstall; the detail dialog handles per-version pinning.
 *
 * @since 2026.3.1
 */
export default function SkillListPage() {
    const { t } = useTranslation();
    const { data: installed, isError: installedError } = useSkills();
    const { data: catalog, isError: catalogError } = useSkillCatalog();

    const installMutation = useInstallSkillFromCatalog();
    const uninstallMutation = useUninstallSkill();

    const [search, setSearch] = useState("");
    const [kind, setKind] = useState<KindFilter>("all");
    const [vendor, setVendor] = useState<string>(ALL_VENDORS);
    const [selected, setSelected] = useState<SkillGroup | null>(null);

    const error = installedError ? t("common.connectionError", { resource: t("skill.title") }) : null;

    // Merge catalog + installed into one row per skill name.
    const groups = useMemo(
        () => buildGroups(catalog ?? [], installed ?? []),
        [catalog, installed],
    );

    const vendors = useMemo(() => {
        const set = new Set<string>();
        groups.forEach((g) => { if (g.author) set.add(g.author); });
        return Array.from(set).sort();
    }, [groups]);

    const visible = useMemo(() => {
        const q = search.trim().toLowerCase();
        return groups.filter((g) => {
            if (kind === "installed" && g.installed.length === 0) return false;
            if (kind === "available" && g.installed.length > 0) return false;
            if (vendor !== ALL_VENDORS && g.author !== vendor) return false;
            if (q) {
                const haystack = [g.name, g.description ?? "", ...g.tools].join(" ").toLowerCase();
                if (!haystack.includes(q)) return false;
            }
            return true;
        });
    }, [groups, search, kind, vendor]);

    const doInstall = useCallback((name: string) => {
        installMutation.mutate(name, {
            onSuccess: (res) => {
                if (res.success) {
                    toast.success(t("skill.installSuccess", { name }));
                    setSelected(null);
                } else {
                    toast.error(res.error ?? t("skill.installFailed", { name }));
                }
            },
            onError: () => toast.error(t("skill.installFailed", { name })),
        });
    }, [installMutation, t]);

    const doUninstall = useCallback((skill: TurSkill) => {
        uninstallMutation.mutate(skill.id, {
            onSuccess: (ok) => {
                if (ok) {
                    toast.success(t("skill.uninstallSuccess", { name: skill.name }));
                    setSelected(null);
                } else {
                    toast.error(t("skill.cannotUninstallBuiltIn"));
                }
            },
            onError: () => toast.error(t("skill.cannotUninstallBuiltIn")),
        });
    }, [uninstallMutation, t]);

    const kindOptions: Array<{ id: KindFilter; label: string }> = [
        { id: "all", label: t("skill.kindAll") },
        { id: "installed", label: t("skill.kindInstalled") },
        { id: "available", label: t("skill.kindAvailable") },
    ];

    return (
        <div className="px-4 md:px-6 lg:px-8 py-2 space-y-4">
            {/* Filter strip */}
            <div className="flex flex-wrap items-center gap-3 rounded-lg border border-border/60 bg-muted/30 px-3 py-2">
                <div className="flex items-center gap-1.5">
                    {kindOptions.map((opt) => {
                        const active = kind === opt.id;
                        return (
                            <button
                                key={opt.id}
                                type="button"
                                onClick={() => setKind(opt.id)}
                                className={
                                    active
                                        ? "inline-flex items-center gap-1.5 rounded-full bg-primary text-primary-foreground px-3 py-1 text-xs font-medium shadow-sm"
                                        : "inline-flex items-center gap-1.5 rounded-full border border-border/60 bg-background px-3 py-1 text-xs text-muted-foreground hover:border-primary/40 hover:text-foreground transition-colors"
                                }
                            >
                                {opt.label}
                            </button>
                        );
                    })}
                </div>

                {vendors.length > 1 && (
                    <div className="flex items-center gap-1.5">
                        <span className="text-[11px] uppercase tracking-wide text-muted-foreground">
                            {t("skill.vendor")}
                        </span>
                        <button
                            type="button"
                            onClick={() => setVendor(ALL_VENDORS)}
                            className={
                                vendor === ALL_VENDORS
                                    ? "rounded-full bg-blue-600 text-white px-2.5 py-1 text-[11px] font-medium"
                                    : "rounded-full border border-border/60 bg-background px-2.5 py-1 text-[11px] text-muted-foreground hover:border-blue-500/40 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                            }
                        >
                            {t("skill.vendorAll")}
                        </button>
                        {vendors.map((v) => (
                            <button
                                key={v}
                                type="button"
                                onClick={() => setVendor(v)}
                                className={
                                    vendor === v
                                        ? "rounded-full bg-blue-600 text-white px-2.5 py-1 text-[11px] font-medium"
                                        : "rounded-full border border-border/60 bg-background px-2.5 py-1 text-[11px] text-muted-foreground hover:border-blue-500/40 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                                }
                            >
                                {v}
                            </button>
                        ))}
                    </div>
                )}

                <div className="relative ml-auto">
                    <IconSearch className="absolute left-2.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                    <Input
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder={t("skill.searchPlaceholder")}
                        className="h-8 w-56 pl-8 text-sm"
                    />
                </div>
            </div>

            <LoadProvider checkIsNotUndefined={catalog ?? installed} error={error}>
                {visible.length > 0 ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {visible.map((group) => (
                            <SkillCard
                                key={group.name}
                                group={group}
                                onView={() => setSelected(group)}
                                onInstall={() => doInstall(group.name)}
                                installing={installMutation.isPending && installMutation.variables === group.name}
                                t={t}
                            />
                        ))}
                    </div>
                ) : (
                    <div className="flex flex-col items-center justify-center py-20 text-center">
                        <div className="rounded-2xl p-4 bg-muted text-muted-foreground mb-4">
                            <IconPackages className="size-10" />
                        </div>
                        <p className="text-lg font-semibold">{t("skill.emptyTitle")}</p>
                        <p className="text-sm text-muted-foreground mt-1">{t("skill.emptyDescription")}</p>
                        {(kind !== "all" || vendor !== ALL_VENDORS || search) && (
                            <Button
                                variant="outline"
                                size="sm"
                                className="mt-4"
                                onClick={() => { setKind("all"); setVendor(ALL_VENDORS); setSearch(""); }}
                            >
                                {t("skill.clearFilters")}
                            </Button>
                        )}
                    </div>
                )}
                {catalogError && (
                    <div className="mt-3 rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
                        {t("skill.catalogUnavailable")}
                    </div>
                )}
            </LoadProvider>

            <SkillDetailDialog
                group={selected}
                onClose={() => setSelected(null)}
                onInstall={doInstall}
                onUninstall={doUninstall}
                installing={installMutation.isPending}
                uninstallingId={uninstallMutation.isPending ? uninstallMutation.variables ?? null : null}
            />
        </div>
    );
}

/**
 * Visual card for a skill. Leads with the package icon + name; shows the
 * installed/built-in state and tool count; the footer installs or opens the
 * detail dialog.
 */
function SkillCard({
    group,
    onView,
    onInstall,
    installing,
    t,
}: Readonly<{
    group: SkillGroup;
    onView: () => void;
    onInstall: () => void;
    installing: boolean;
    t: (key: string, options?: Record<string, unknown>) => string;
}>) {
    const isInstalled = group.installed.length > 0;
    const displayVersion = group.catalog?.version ?? group.installed[0]?.version;
    return (
        <Card className="group flex flex-col transition-all hover:shadow-md hover:border-blue-500/30">
            <CardHeader className="pb-3">
                <div className="flex items-start gap-3">
                    <div className="shrink-0 flex items-center justify-center size-10 rounded-lg bg-linear-to-br from-blue-500/10 to-indigo-500/10 text-blue-600 dark:text-blue-400">
                        <IconPackages className="size-6" />
                    </div>
                    <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-1.5 mb-0.5">
                            {isInstalled ? (
                                <Badge variant="secondary" className="text-[9px] uppercase tracking-wide gap-1">
                                    <IconCircleCheck className="size-3" />
                                    {t("skill.installed")}
                                </Badge>
                            ) : (
                                <Badge variant="outline" className="text-[9px] uppercase tracking-wide">
                                    {t("skill.available")}
                                </Badge>
                            )}
                            {group.builtIn && (
                                <Badge variant="outline" className="text-[9px] uppercase tracking-wide gap-1">
                                    <IconLock className="size-3" />
                                    {t("skill.builtIn")}
                                </Badge>
                            )}
                        </div>
                        <CardTitle className="text-base leading-tight">{group.name}</CardTitle>
                        <CardDescription className="text-xs mt-0.5">
                            {t("skill.byAuthor", { author: group.author ?? t("skill.unknownAuthor") })}
                            {displayVersion ? <> {" "}&middot;{" "}v{displayVersion}</> : null}
                        </CardDescription>
                    </div>
                </div>
            </CardHeader>

            <CardContent className="flex-1 pb-3">
                <p className="text-sm text-muted-foreground leading-relaxed line-clamp-3">
                    {group.description}
                </p>
                {group.tools.length > 0 && (
                    <div className="flex flex-wrap gap-1.5 mt-3">
                        {group.tools.slice(0, 4).map((tool) => (
                            <Badge key={tool} variant="secondary" className="text-[10px] gap-1 font-mono">
                                <IconTool className="size-3" />
                                {tool}
                            </Badge>
                        ))}
                        {group.tools.length > 4 && (
                            <Badge variant="outline" className="text-[10px]">
                                +{group.tools.length - 4}
                            </Badge>
                        )}
                    </div>
                )}
            </CardContent>

            <CardFooter className="pt-0 flex gap-2">
                {!isInstalled && group.catalog ? (
                    <GradientButton className="flex-1" size="sm" onClick={onInstall} disabled={installing}>
                        {installing ? (
                            <IconLoader2 className="size-4 animate-spin" />
                        ) : (
                            <IconPackageImport className="size-4" />
                        )}
                        {t("skill.install")}
                    </GradientButton>
                ) : null}
                <Button variant="outline" size="sm" className={isInstalled ? "flex-1" : ""} onClick={onView}>
                    <IconEye className="size-4" />
                    {t("skill.details")}
                </Button>
            </CardFooter>
        </Card>
    );
}

/** Merge the catalog manifests and installed rows into one group per name. */
function buildGroups(catalog: TurSkillManifest[], installed: TurSkill[]): SkillGroup[] {
    const byName = new Map<string, SkillGroup>();

    const ensure = (name: string): SkillGroup => {
        let g = byName.get(name);
        if (!g) {
            g = { name, author: null, description: null, catalog: null, installed: [], tools: [], builtIn: false };
            byName.set(name, g);
        }
        return g;
    };

    for (const manifest of catalog) {
        const g = ensure(manifest.name);
        g.catalog = manifest;
        g.author = manifest.author ?? g.author;
        g.description = manifest.description ?? g.description;
        mergeTools(g, manifest.tools ?? []);
    }

    for (const skill of installed) {
        const g = ensure(skill.name);
        g.installed.push(skill);
        g.author = g.author ?? skill.author;
        g.description = g.description ?? skill.description;
        mergeTools(g, skill.tools ?? []);
    }

    for (const g of byName.values()) {
        // newest version first (lexically-decent for semver-ish strings)
        g.installed.sort((a, b) => b.version.localeCompare(a.version, undefined, { numeric: true }));
        g.builtIn = g.installed.length > 0 && g.installed.every((s) => s.builtIn);
    }

    return Array.from(byName.values()).sort((a, b) => a.name.localeCompare(b.name));
}

function mergeTools(group: SkillGroup, tools: string[]) {
    for (const tool of tools) {
        if (!group.tools.includes(tool)) group.tools.push(tool);
    }
}
