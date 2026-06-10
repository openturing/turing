import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { GradientButton } from "@/components/ui/gradient-button";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import type { TurSkill, TurSkillManifest } from "@/models/skill/skill.model";
import type { SkillGroup } from "./skill.utils";
import {
    IconCircleCheck,
    IconFileText,
    IconLink,
    IconLoader2,
    IconLock,
    IconPackageImport,
    IconPackages,
    IconShieldCheck,
    IconShieldX,
    IconTool,
    IconTrash,
} from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

interface SkillDetailDialogProps {
    group: SkillGroup | null;
    onClose: () => void;
    onInstall: (name: string) => void;
    onUninstall: (skill: TurSkill) => void;
    installing: boolean;
    uninstallingId: string | null;
}

/**
 * T117 / §IX.4.b — full manifest preview + install / version management for a
 * single skill. The version selector is the "version-pin" surface: it lists
 * every known version (catalog + installed) so an operator can inspect and
 * uninstall a specific one.
 *
 * @since 2026.3.1
 */
export function SkillDetailDialog({
    group,
    onClose,
    onInstall,
    onUninstall,
    installing,
    uninstallingId,
}: Readonly<SkillDetailDialogProps>) {
    const { t } = useTranslation();
    const [selectedVersion, setSelectedVersion] = useState<string | null>(null);

    const versions = group ? buildVersionList(group) : [];

    // Default the version selector to the catalog version (the installable
    // one), falling back to the newest installed version.
    useEffect(() => {
        if (!group) {
            setSelectedVersion(null);
            return;
        }
        const preferred = group.catalog?.version ?? group.installed[0]?.version ?? null;
        setSelectedVersion(preferred);
    }, [group]);

    if (!group) return null;

    const manifest: TurSkillManifest | null = group.catalog;
    const persona = manifest?.personaFragment;
    const workspaceFiles = manifest?.workspaceDefaults ? Object.keys(manifest.workspaceDefaults) : [];
    const dependencies = manifest?.dependencies ?? [];
    const tools = group.tools;

    const installedVersions = new Set(group.installed.map((s) => s.version));
    const selectedInstalled = group.installed.find((s) => s.version === selectedVersion) ?? null;
    const selectedIsInstalled = selectedVersion !== null && installedVersions.has(selectedVersion);
    const selectedIsCatalog = manifest?.version === selectedVersion;
    const signature = selectedInstalled?.signature ?? manifest?.signature ?? null;

    return (
        <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
            <DialogContent className="sm:max-w-lg max-h-[85vh] flex flex-col">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <IconPackages className="size-5" />
                        {group.name}
                    </DialogTitle>
                    <DialogDescription>
                        {t("skill.byAuthor", { author: group.author ?? t("skill.unknownAuthor") })}
                    </DialogDescription>
                </DialogHeader>

                <div className="overflow-y-auto flex-1 -mx-1 px-1 space-y-4">
                    {group.description && (
                        <p className="text-sm text-muted-foreground leading-relaxed">{group.description}</p>
                    )}

                    {/* Version-pin selector */}
                    <div className="space-y-1.5">
                        <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                            {t("skill.version")}
                        </span>
                        <div className="flex items-center gap-2">
                            <Select
                                value={selectedVersion ?? undefined}
                                onValueChange={(v) => setSelectedVersion(v)}
                            >
                                <SelectTrigger className="h-8 w-40 text-sm">
                                    <SelectValue placeholder={t("skill.version")} />
                                </SelectTrigger>
                                <SelectContent>
                                    {versions.map((v) => (
                                        <SelectItem key={v} value={v}>
                                            v{v}
                                            {installedVersions.has(v) ? ` · ${t("skill.installedShort")}` : ""}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                            {selectedIsInstalled && (
                                <Badge variant="secondary" className="text-[10px] gap-1">
                                    <IconCircleCheck className="size-3" />
                                    {t("skill.installed")}
                                </Badge>
                            )}
                            {group.builtIn && (
                                <Badge variant="outline" className="text-[10px] gap-1">
                                    <IconLock className="size-3" />
                                    {t("skill.builtIn")}
                                </Badge>
                            )}
                        </div>
                    </div>

                    {/* Signature — present on every installed/catalog skill (self-signed in v1) */}
                    {signature ? (
                        <div className="flex items-center gap-2 rounded-md border border-border/60 bg-muted/30 px-3 py-2">
                            <IconShieldCheck className="size-4 text-emerald-600 dark:text-emerald-400" />
                            <span className="text-xs font-mono text-muted-foreground truncate">{signature}</span>
                        </div>
                    ) : (
                        <div className="flex items-center gap-2 rounded-md border border-border/60 bg-muted/30 px-3 py-2">
                            <IconShieldX className="size-4 text-muted-foreground" />
                            <span className="text-xs text-muted-foreground">{t("skill.unsigned")}</span>
                        </div>
                    )}

                    {/* Persona fragment */}
                    {persona?.instruction && (
                        <Section title={t("skill.personaFragment")}>
                            <p className="text-sm text-muted-foreground leading-relaxed whitespace-pre-wrap">
                                {persona.instruction}
                            </p>
                        </Section>
                    )}

                    {/* Tools */}
                    {tools.length > 0 && (
                        <Section title={t("skill.tools")}>
                            <div className="flex flex-wrap gap-1.5">
                                {tools.map((tool) => (
                                    <Badge key={tool} variant="secondary" className="text-[10px] gap-1 font-mono">
                                        <IconTool className="size-3" />
                                        {tool}
                                    </Badge>
                                ))}
                            </div>
                        </Section>
                    )}

                    {/* Workspace defaults */}
                    {workspaceFiles.length > 0 && (
                        <Section title={t("skill.workspaceDefaults")}>
                            <div className="flex flex-wrap gap-1.5">
                                {workspaceFiles.map((file) => (
                                    <Badge key={file} variant="outline" className="text-[10px] gap-1 font-mono">
                                        <IconFileText className="size-3" />
                                        {file}
                                    </Badge>
                                ))}
                            </div>
                        </Section>
                    )}

                    {/* Dependencies */}
                    {dependencies.length > 0 && (
                        <Section title={t("skill.dependencies")}>
                            <div className="flex flex-wrap gap-1.5">
                                {dependencies.map((dep) => (
                                    <Badge key={dep} variant="outline" className="text-[10px] gap-1 font-mono">
                                        <IconLink className="size-3" />
                                        {dep}
                                    </Badge>
                                ))}
                            </div>
                        </Section>
                    )}
                </div>

                <DialogFooter className="gap-2">
                    {selectedInstalled && !selectedInstalled.builtIn && (
                        <Button
                            variant="outline"
                            className="flex-1"
                            onClick={() => onUninstall(selectedInstalled)}
                            disabled={uninstallingId === selectedInstalled.id}
                        >
                            {uninstallingId === selectedInstalled.id ? (
                                <IconLoader2 className="size-4 animate-spin" />
                            ) : (
                                <IconTrash className="size-4" />
                            )}
                            {t("skill.uninstall")}
                        </Button>
                    )}
                    {manifest && !(selectedIsCatalog && selectedIsInstalled) && (
                        <GradientButton
                            className="flex-1"
                            onClick={() => onInstall(group.name)}
                            disabled={installing}
                        >
                            {installing ? (
                                <IconLoader2 className="size-4 animate-spin" />
                            ) : (
                                <IconPackageImport className="size-4" />
                            )}
                            {selectedIsInstalled ? t("skill.reinstall") : t("skill.install")}
                        </GradientButton>
                    )}
                    <Button variant="ghost" onClick={onClose}>
                        {t("common.close")}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

function Section({ title, children }: Readonly<{ title: string; children: React.ReactNode }>) {
    return (
        <div className="space-y-1.5">
            <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">{title}</span>
            {children}
        </div>
    );
}

/** Distinct versions (catalog + installed), newest-ish first (catalog leads). */
function buildVersionList(group: SkillGroup): string[] {
    const out: string[] = [];
    if (group.catalog?.version) out.push(group.catalog.version);
    for (const s of group.installed) {
        if (!out.includes(s.version)) out.push(s.version);
    }
    return out;
}
