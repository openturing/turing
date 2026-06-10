import { useSkills, useUninstallSkill } from "@/api/queries/skill.queries";
import { LoadProvider } from "@/components/loading-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import type { TurSkill } from "@/models/skill/skill.model";
import {
    IconLoader2,
    IconLock,
    IconPackages,
    IconPencil,
    IconPlus,
    IconSearch,
    IconTool,
    IconTrash,
} from "@tabler/icons-react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";

import { SkillFormDialog } from "./skill-form-dialog";

/**
 * T117 (CRUD) / §IX.4.b — manage installed skills. The browse/install
 * storefront moved to the unified marketplace (T118); this page is the
 * editable CRUD: create, edit and delete user-authored skills. Built-in
 * platform skills are listed read-only (the boot-time sync owns them).
 *
 * @since 2026.3.1
 */
export default function SkillListPage() {
    const { t } = useTranslation();
    const { data: skills, isError } = useSkills();
    const uninstallMutation = useUninstallSkill();

    const [search, setSearch] = useState("");
    // null = closed; { id: null } = create; { id } = edit.
    const [editing, setEditing] = useState<{ id: string | null } | null>(null);
    const [deleting, setDeleting] = useState<TurSkill | null>(null);

    const error = isError ? t("common.connectionError", { resource: t("skill.title") }) : null;

    const visible = useMemo(() => {
        const list = skills ?? [];
        const q = search.trim().toLowerCase();
        const filtered = q
            ? list.filter((s) =>
                [s.name, s.description ?? "", ...(s.tools ?? [])].join(" ").toLowerCase().includes(q))
            : list;
        return [...filtered].sort((a, b) => a.name.localeCompare(b.name));
    }, [skills, search]);

    const confirmDelete = useCallback(() => {
        if (!deleting) return;
        const skill = deleting;
        uninstallMutation.mutate(skill.id, {
            onSuccess: (ok) => {
                if (ok) {
                    toast.success(t("skill.uninstallSuccess", { name: skill.name }));
                } else {
                    toast.error(t("skill.cannotUninstallBuiltIn"));
                }
                setDeleting(null);
            },
            onError: () => {
                toast.error(t("skill.cannotUninstallBuiltIn"));
                setDeleting(null);
            },
        });
    }, [deleting, uninstallMutation, t]);

    return (
        <div className="px-4 md:px-6 lg:px-8 py-2 space-y-4">
            {/* Toolbar */}
            <div className="flex flex-wrap items-center gap-3">
                <div className="relative">
                    <IconSearch className="absolute left-2.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                    <Input
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder={t("skill.searchPlaceholder")}
                        className="h-9 w-64 pl-8 text-sm"
                    />
                </div>
                <GradientButton className="ml-auto" size="sm" onClick={() => setEditing({ id: null })}>
                    <IconPlus className="size-4" />
                    {t("skill.new")}
                </GradientButton>
            </div>

            <LoadProvider checkIsNotUndefined={skills} error={error}>
                {visible.length > 0 ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {visible.map((skill) => (
                            <SkillCard
                                key={skill.id}
                                skill={skill}
                                onEdit={() => setEditing({ id: skill.id })}
                                onDelete={() => setDeleting(skill)}
                                t={t}
                            />
                        ))}
                    </div>
                ) : (
                    <div className="flex flex-col items-center justify-center py-20 text-center">
                        <div className="rounded-2xl p-4 bg-muted text-muted-foreground mb-4">
                            <IconPackages className="size-10" />
                        </div>
                        <p className="text-lg font-semibold">{t("skill.crudEmptyTitle")}</p>
                        <p className="text-sm text-muted-foreground mt-1">{t("skill.crudEmptyDescription")}</p>
                        <GradientButton className="mt-4" size="sm" onClick={() => setEditing({ id: null })}>
                            <IconPlus className="size-4" />
                            {t("skill.new")}
                        </GradientButton>
                    </div>
                )}
            </LoadProvider>

            <SkillFormDialog
                open={editing !== null}
                skillId={editing?.id ?? null}
                onClose={() => setEditing(null)}
            />

            {/* Delete confirmation */}
            <Dialog open={deleting !== null} onOpenChange={(o) => { if (!o) setDeleting(null); }}>
                <DialogContent className="sm:max-w-md">
                    <DialogHeader>
                        <DialogTitle>{t("skill.deleteTitle")}</DialogTitle>
                        <DialogDescription>
                            {t("skill.deleteConfirm", { name: deleting?.name ?? "" })}
                        </DialogDescription>
                    </DialogHeader>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setDeleting(null)}>
                            {t("common.cancel")}
                        </Button>
                        <Button
                            variant="destructive"
                            onClick={confirmDelete}
                            disabled={uninstallMutation.isPending}
                        >
                            {uninstallMutation.isPending ? (
                                <IconLoader2 className="size-4 animate-spin" />
                            ) : (
                                <IconTrash className="size-4" />
                            )}
                            {t("skill.uninstall")}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}

function SkillCard({
    skill,
    onEdit,
    onDelete,
    t,
}: Readonly<{
    skill: TurSkill;
    onEdit: () => void;
    onDelete: () => void;
    t: (key: string, options?: Record<string, unknown>) => string;
}>) {
    const tools = skill.tools ?? [];
    return (
        <Card className="group flex flex-col transition-all hover:shadow-md hover:border-blue-500/30">
            <CardHeader className="pb-3">
                <div className="flex items-start gap-3">
                    <div className="shrink-0 flex items-center justify-center size-10 rounded-lg bg-linear-to-br from-blue-500/10 to-indigo-500/10 text-blue-600 dark:text-blue-400">
                        <IconPackages className="size-6" />
                    </div>
                    <div className="flex-1 min-w-0">
                        {skill.builtIn && (
                            <Badge variant="outline" className="text-[9px] uppercase tracking-wide gap-1 mb-0.5">
                                <IconLock className="size-3" />
                                {t("skill.builtIn")}
                            </Badge>
                        )}
                        <CardTitle className="text-base leading-tight">{skill.name}</CardTitle>
                        <CardDescription className="text-xs mt-0.5">
                            {t("skill.byAuthor", { author: skill.author ?? t("skill.unknownAuthor") })}
                            {" "}&middot;{" "}v{skill.version}
                        </CardDescription>
                    </div>
                </div>
            </CardHeader>

            <CardContent className="flex-1 pb-3">
                <p className="text-sm text-muted-foreground leading-relaxed line-clamp-3">
                    {skill.description}
                </p>
                {tools.length > 0 && (
                    <div className="flex flex-wrap gap-1.5 mt-3">
                        {tools.slice(0, 4).map((tool) => (
                            <Badge key={tool} variant="secondary" className="text-[10px] gap-1 font-mono">
                                <IconTool className="size-3" />
                                {tool}
                            </Badge>
                        ))}
                        {tools.length > 4 && (
                            <Badge variant="outline" className="text-[10px]">+{tools.length - 4}</Badge>
                        )}
                    </div>
                )}
            </CardContent>

            <CardFooter className="pt-0 flex gap-2">
                {skill.builtIn ? (
                    <span className="text-xs text-muted-foreground inline-flex items-center gap-1.5">
                        <IconLock className="size-3.5" />
                        {t("skill.platformManaged")}
                    </span>
                ) : (
                    <>
                        <Button variant="outline" size="sm" className="flex-1" onClick={onEdit}>
                            <IconPencil className="size-4" />
                            {t("skill.edit")}
                        </Button>
                        <Button variant="outline" size="sm" aria-label={t("skill.uninstall")} onClick={onDelete}>
                            <IconTrash className="size-4" />
                        </Button>
                    </>
                )}
            </CardFooter>
        </Card>
    );
}
