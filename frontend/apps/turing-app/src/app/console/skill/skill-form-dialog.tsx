import { useInstallSkill, useSkill } from "@/api/queries/skill.queries";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { TurSkillManifest } from "@/models/skill/skill.model";
import { IconAlertTriangle, IconDeviceFloppy, IconLoader2 } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";

interface SkillFormDialogProps {
    /** `null` → create a new skill; a string id → edit that installed skill. */
    skillId: string | null;
    open: boolean;
    onClose: () => void;
}

interface FormState {
    name: string;
    version: string;
    author: string;
    description: string;
    tools: string;
    dependencies: string;
    instruction: string;
    workspaceDefaults: string;
}

const EMPTY: FormState = {
    name: "",
    version: "1.0.0",
    author: "",
    description: "",
    tools: "",
    dependencies: "",
    instruction: "",
    workspaceDefaults: "",
};

/**
 * T117 (CRUD) / §IX.4.b — create or edit a user-authored skill. Edits the
 * common manifest fields with friendly inputs (comma-separated lists, a JSON
 * box for workspace file templates); `flowSnippet` is preserved verbatim from
 * the loaded manifest so editing a flow-bearing skill doesn't drop it. Saving
 * upserts through `POST /api/skill/install` (validated server-side).
 *
 * @since 2026.3.1
 */
export function SkillFormDialog({ skillId, open, onClose }: Readonly<SkillFormDialogProps>) {
    const { t } = useTranslation();
    const isEdit = skillId !== null;
    const { data: loaded } = useSkill(isEdit ? skillId : undefined);
    const installMutation = useInstallSkill();

    const [form, setForm] = useState<FormState>(EMPTY);
    const [flowSnippet, setFlowSnippet] = useState<unknown>(undefined);
    const [jsonError, setJsonError] = useState<string | null>(null);

    // Prefill on open: edit → from the loaded manifest; create → blank.
    useEffect(() => {
        if (!open) return;
        if (isEdit && loaded) {
            setForm({
                name: loaded.name ?? "",
                version: loaded.version ?? "1.0.0",
                author: loaded.author ?? "",
                description: loaded.description ?? "",
                tools: (loaded.tools ?? []).join(", "),
                dependencies: (loaded.dependencies ?? []).join(", "),
                instruction: loaded.personaFragment?.instruction ?? "",
                workspaceDefaults: loaded.workspaceDefaults
                    ? JSON.stringify(loaded.workspaceDefaults, null, 2)
                    : "",
            });
            setFlowSnippet(loaded.flowSnippet ?? undefined);
        } else if (!isEdit) {
            setForm(EMPTY);
            setFlowSnippet(undefined);
        }
        setJsonError(null);
    }, [open, isEdit, loaded]);

    const set = (key: keyof FormState) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
        setForm((prev) => ({ ...prev, [key]: e.target.value }));

    const canSave = form.name.trim() !== "" && form.version.trim() !== "" && !installMutation.isPending;

    const save = () => {
        let workspaceDefaults: Record<string, string> | undefined;
        if (form.workspaceDefaults.trim()) {
            try {
                workspaceDefaults = JSON.parse(form.workspaceDefaults) as Record<string, string>;
            } catch {
                setJsonError(t("skill.invalidJson"));
                return;
            }
        }
        setJsonError(null);

        const manifest: TurSkillManifest = {
            name: form.name.trim(),
            version: form.version.trim(),
            author: form.author.trim() || null,
            description: form.description.trim() || null,
            personaFragment: form.instruction.trim()
                ? { instruction: form.instruction.trim() }
                : null,
            flowSnippet: flowSnippet ?? null,
            tools: csv(form.tools),
            workspaceDefaults: workspaceDefaults ?? null,
            dependencies: csv(form.dependencies),
        };

        installMutation.mutate(manifest, {
            onSuccess: (res) => {
                if (res.success) {
                    toast.success(t(isEdit ? "skill.saveSuccess" : "skill.createSuccess", { name: manifest.name }));
                    onClose();
                } else {
                    toast.error(res.error ?? t("skill.saveFailed"));
                }
            },
            onError: () => toast.error(t("skill.saveFailed")),
        });
    };

    return (
        <Dialog open={open} onOpenChange={(o) => { if (!o) onClose(); }}>
            <DialogContent className="sm:max-w-lg max-h-[85vh] flex flex-col">
                <DialogHeader>
                    <DialogTitle>{isEdit ? t("skill.editTitle") : t("skill.newTitle")}</DialogTitle>
                    <DialogDescription>{t("skill.formDescription")}</DialogDescription>
                </DialogHeader>

                <div className="overflow-y-auto flex-1 -mx-1 px-1 space-y-4">
                    <div className="grid grid-cols-2 gap-3">
                        <Field label={t("skill.fieldName")}>
                            <Input value={form.name} onChange={set("name")} placeholder="my-skill" />
                        </Field>
                        <Field label={t("skill.fieldVersion")}>
                            <Input value={form.version} onChange={set("version")} placeholder="1.0.0" />
                        </Field>
                    </div>
                    <Field label={t("skill.fieldAuthor")}>
                        <Input value={form.author} onChange={set("author")} placeholder="acme" />
                    </Field>
                    <Field label={t("skill.fieldDescription")}>
                        <Textarea value={form.description} onChange={set("description")} rows={2} />
                    </Field>
                    <Field label={t("skill.fieldTools")} hint={t("skill.csvHint")}>
                        <Input value={form.tools} onChange={set("tools")} placeholder="scaffold.markdown, scaffold.pickQuote" />
                    </Field>
                    <Field label={t("skill.fieldDependencies")} hint={t("skill.csvHint")}>
                        <Input value={form.dependencies} onChange={set("dependencies")} placeholder="http@^1.0.0" />
                    </Field>
                    <Field label={t("skill.fieldInstruction")} hint={t("skill.vocabHint")}>
                        <Textarea value={form.instruction} onChange={set("instruction")} rows={3} />
                    </Field>
                    <Field label={t("skill.fieldWorkspaceDefaults")} hint={t("skill.jsonHint")}>
                        <Textarea
                            value={form.workspaceDefaults}
                            onChange={set("workspaceDefaults")}
                            rows={4}
                            className="font-mono text-xs"
                            placeholder={'{\n  "draft.md": "# {{title}}"\n}'}
                        />
                    </Field>
                    {jsonError && (
                        <div className="flex items-center gap-2 rounded-md bg-red-500/10 border border-red-500/30 px-3 py-2 text-red-600 dark:text-red-400">
                            <IconAlertTriangle className="size-4" />
                            <span className="text-sm">{jsonError}</span>
                        </div>
                    )}
                </div>

                <DialogFooter>
                    <Button variant="outline" onClick={onClose}>{t("common.cancel")}</Button>
                    <GradientButton onClick={save} disabled={!canSave}>
                        {installMutation.isPending ? (
                            <IconLoader2 className="size-4 animate-spin" />
                        ) : (
                            <IconDeviceFloppy className="size-4" />
                        )}
                        {t("common.save")}
                    </GradientButton>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

function Field({ label, hint, children }: Readonly<{ label: string; hint?: string; children: React.ReactNode }>) {
    return (
        <div className="space-y-1.5">
            <Label className="text-xs">{label}</Label>
            {children}
            {hint && <p className="text-[11px] text-muted-foreground">{hint}</p>}
        </div>
    );
}

/** Split a comma-separated string into a trimmed, non-empty list (undefined when empty). */
function csv(value: string): string[] | undefined {
    const parts = value.split(",").map((s) => s.trim()).filter(Boolean);
    return parts.length > 0 ? parts : undefined;
}
