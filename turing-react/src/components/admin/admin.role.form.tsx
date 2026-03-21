import { ROUTES } from "@/app/routes.const";
import {
    Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FormActions } from "@/components/ui/form-actions";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { SectionCard } from "@/components/ui/section-card";
import type { TurPrivilege, TurRole } from "@/models/auth/role";
import { TurRoleService } from "@/services/auth/role.service";
import { TurPrivilegeService } from "@/services/auth/privilege.service";
import { IconLock, IconNotes, IconUserShield } from "@tabler/icons-react";
import { Fragment, useCallback, useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

const turRoleService = new TurRoleService();
const turPrivilegeService = new TurPrivilegeService();

const ACTIONS = ["VIEW", "CREATE", "EDIT", "DELETE"] as const;

interface PrivilegeGroup {
    section: string;
    category: string;
    label: string;
}

const PRIVILEGE_LAYOUT: PrivilegeGroup[] = [
    { section: "Generative AI", category: "LLM", label: "Language Model" },
    { section: "Generative AI", category: "EMBEDDING", label: "Embedding Model" },
    { section: "Generative AI", category: "STORE", label: "Embedding Store" },
    { section: "Generative AI", category: "AI_AGENT", label: "AI Agent" },
    { section: "Generative AI", category: "INTENT", label: "Intent" },
    { section: "Enterprise Search", category: "SE", label: "Search Engine" },
    { section: "Enterprise Search", category: "SN", label: "Semantic Navigation" },
];

interface Props {
    value: TurRole;
    isNew: boolean;
}

export const AdminRoleForm: React.FC<Props> = ({ value, isNew }) => {
    const form = useForm<TurRole>({ defaultValues: value });
    const navigate = useNavigate();
    const [allPrivileges, setAllPrivileges] = useState<TurPrivilege[]>([]);

    useEffect(() => {
        form.reset({ ...value, turPrivileges: value.turPrivileges ?? [] });
    }, [value]);

    useEffect(() => {
        turPrivilegeService.query().then(setAllPrivileges);
    }, []);

    const selectedIds = new Set(
        (form.watch("turPrivileges") ?? []).map((p) => p.id)
    );

    const privilegesByCategory = useMemo(() => {
        const map: Record<string, TurPrivilege[]> = {};
        for (const p of allPrivileges) {
            if (!p.category || p.category === "SYSTEM") continue;
            (map[p.category] ??= []).push(p);
        }
        return map;
    }, [allPrivileges]);

    const sections = useMemo(() => {
        const result: { section: string; rows: { label: string; privileges: TurPrivilege[] }[] }[] = [];
        let currentSection = "";
        let currentRows: { label: string; privileges: TurPrivilege[] }[] = [];
        for (const group of PRIVILEGE_LAYOUT) {
            const privs = privilegesByCategory[group.category];
            if (!privs || privs.length === 0) continue;
            if (group.section !== currentSection) {
                if (currentRows.length > 0) {
                    result.push({ section: currentSection, rows: currentRows });
                }
                currentSection = group.section;
                currentRows = [];
            }
            currentRows.push({ label: group.label, privileges: privs });
        }
        if (currentRows.length > 0) {
            result.push({ section: currentSection, rows: currentRows });
        }
        return result;
    }, [privilegesByCategory]);

    const togglePrivilege = useCallback((privilege: TurPrivilege, checked: boolean) => {
        const current = form.getValues("turPrivileges") ?? [];
        if (checked) {
            form.setValue("turPrivileges", [...current, privilege], { shouldDirty: true });
        } else {
            form.setValue("turPrivileges", current.filter((p) => p.id !== privilege.id), { shouldDirty: true });
        }
    }, [form]);

    const toggleCategory = useCallback((privileges: TurPrivilege[], checked: boolean) => {
        const current = form.getValues("turPrivileges") ?? [];
        const ids = new Set(privileges.map((p) => p.id));
        if (checked) {
            const toAdd = privileges.filter((p) => !current.some((c) => c.id === p.id));
            form.setValue("turPrivileges", [...current, ...toAdd], { shouldDirty: true });
        } else {
            form.setValue("turPrivileges", current.filter((p) => !ids.has(p.id)), { shouldDirty: true });
        }
    }, [form]);

    async function onSubmit(data: TurRole) {
        try {
            if (isNew) {
                const result = await turRoleService.create(data);
                if (result) {
                    toast.success(`Role "${data.name}" was created.`);
                    navigate(ROUTES.ADMIN_ROLES);
                } else {
                    toast.error("Failed to create role.");
                }
            } else {
                const result = await turRoleService.update(data.id, data);
                if (result) {
                    toast.success(`Role "${data.name}" was updated.`);
                } else {
                    toast.error("Failed to update role.");
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error("Failed to save role. Please try again.");
        }
    }

    return (
        <div className="px-6">
            <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8">
                    <SectionCard variant="blue">
                        <SectionCard.Header icon={IconUserShield} title="Role Details" description="Name and identification for this role." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="name"
                                rules={{ required: "Role name is required." }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Role Name</FormLabel>
                                        <FormDescription>
                                            A unique name for this role (e.g. "Admin", "Editor", "Viewer").
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder="e.g. Admin" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    <SectionCard variant="violet">
                        <SectionCard.Header icon={IconNotes} title="Description" description="Optional details about this role's scope." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="description"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Description</FormLabel>
                                        <FormDescription>
                                            Describe what permissions or access level this role grants.
                                        </FormDescription>
                                        <FormControl>
                                            <Textarea
                                                {...field}
                                                placeholder="e.g. Full read/write access to all resources"
                                                className="resize-none"
                                            />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    {sections.length > 0 && (
                        <SectionCard variant="emerald">
                            <SectionCard.Header icon={IconLock} title="Privileges" description="Select which actions this role can perform on each resource." />
                            <SectionCard.Content>
                                <div className="overflow-x-auto rounded-lg border">
                                    <table className="w-full text-sm">
                                        <thead>
                                            <tr className="border-b bg-muted/50">
                                                <th className="text-left font-medium px-4 py-3">Resource</th>
                                                {ACTIONS.map((a) => (
                                                    <th key={a} className="text-center font-medium px-3 py-3 w-24">{a}</th>
                                                ))}
                                                <th className="text-center font-medium px-3 py-3 w-24">ALL</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {sections.map((sec) => (
                                                <Fragment key={sec.section}>
                                                    <tr className="bg-muted/30">
                                                        <td colSpan={ACTIONS.length + 2} className="px-4 py-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                                                            {sec.section}
                                                        </td>
                                                    </tr>
                                                    {sec.rows.map(({ label, privileges }) => {
                                                        const allChecked = privileges.every((p) => selectedIds.has(p.id));
                                                        return (
                                                            <tr key={label} className="border-b last:border-b-0 hover:bg-muted/20 transition-colors">
                                                                <td className="px-4 py-3 pl-6 font-medium">{label}</td>
                                                                {ACTIONS.map((action) => {
                                                                    const priv = privileges.find((p) => p.name.endsWith("_" + action));
                                                                    if (!priv) return <td key={action} className="text-center px-3 py-3" />;
                                                                    return (
                                                                        <td key={action} className="text-center px-3 py-3">
                                                                            <GradientSwitch
                                                                                checked={selectedIds.has(priv.id)}
                                                                                onCheckedChange={(checked) => togglePrivilege(priv, checked)}
                                                                            />
                                                                        </td>
                                                                    );
                                                                })}
                                                                <td className="text-center px-3 py-3">
                                                                    <GradientSwitch
                                                                        checked={allChecked}
                                                                        onCheckedChange={(checked) => toggleCategory(privileges, checked)}
                                                                    />
                                                                </td>
                                                            </tr>
                                                        );
                                                    })}
                                                </Fragment>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </SectionCard.Content>
                        </SectionCard>
                    )}

                    <FormActions>
                        <FormActions.Cancel onClick={() => navigate(ROUTES.ADMIN_ROLES)} />
                        <FormActions.Submit>{isNew ? "Create Role" : "Save Changes"}</FormActions.Submit>
                    </FormActions>
                </form>
            </Form>
        </div>
    );
};
