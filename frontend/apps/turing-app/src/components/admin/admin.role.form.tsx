import { ROUTES } from "@/app/routes.const";
import {
    Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { SmartDescription } from "@/components/ui/smart-description";
import { StickyPageHeader } from "@/components/sticky-page-header";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { SectionCard } from "@/components/ui/section-card";
import type { TurPrivilege, TurRole } from "@/models/auth/role";
import { TurRoleService } from "@/services/auth/role.service";
import { TurPrivilegeService } from "@/services/auth/privilege.service";
import { IconDeviceFloppy, IconLock, IconNotes, IconUserShield, IconX } from "@tabler/icons-react";
import { GradientButton } from "@/components/ui/gradient-button";
import { Fragment, useCallback, useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turRoleService = new TurRoleService();
const turPrivilegeService = new TurPrivilegeService();

const ACTIONS = ["VIEW", "CREATE", "EDIT", "DELETE"] as const;

interface PrivilegeGroup {
    section: string;
    category: string;
    label: string;
}

const PRIVILEGE_LAYOUT: PrivilegeGroup[] = [
    { section: "home.sections.generativeAi.label", category: "LLM", label: "home.features.languageModel.title" },
    { section: "home.sections.generativeAi.label", category: "EMBEDDING", label: "home.features.embeddingModel.title" },
    { section: "home.sections.generativeAi.label", category: "STORE", label: "home.features.embeddingStore.title" },
    { section: "home.sections.generativeAi.label", category: "AI_AGENT", label: "home.features.aiAgent.title" },
    { section: "home.sections.generativeAi.label", category: "INTENT", label: "home.features.intent.title" },
    { section: "home.sections.enterpriseSearch.label", category: "SE", label: "home.features.searchEngine.title" },
    { section: "home.sections.enterpriseSearch.label", category: "SN", label: "home.features.semanticNavigation.title" },
];

interface Props {
    value: TurRole;
    isNew: boolean;
}

export const AdminRoleForm: React.FC<Props> = ({ value, isNew }) => {
    const { t } = useTranslation();
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
                    toast.success(t("forms.adminRole.created", { name: data.name }));
                    navigate(ROUTES.ADMIN_ROLES);
                } else {
                    toast.error(t("forms.adminRole.createFailed"));
                }
            } else {
                const result = await turRoleService.update(data.id, data);
                if (result) {
                    toast.success(t("forms.adminRole.roleUpdated", { name: data.name }));
                } else {
                    toast.error(t("forms.adminRole.updateFailed"));
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("forms.adminRole.saveFailed"));
        }
    }

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
                <StickyPageHeader>
                    <StickyPageHeader.Title
                        icon={IconUserShield}
                        feature={isNew ? t("admin.roles.newRole") : "Role"}
                        description={isNew ? t("admin.roles.createDescription") : t("admin.roles.editDescription")}
                    />
                    <StickyPageHeader.Actions>
                        <GradientButton type="submit" size="sm">
                            <IconDeviceFloppy className="size-4" />
                            {t("forms.formActions.saveChanges")}
                        </GradientButton>
                        <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(ROUTES.ADMIN_ROLES)}>
                            <IconX className="size-4" />
                            {t("forms.formActions.cancel")}
                        </GradientButton>
                    </StickyPageHeader.Actions>
                </StickyPageHeader>
                    <SectionCard variant="blue">
                        <SectionCard.Header icon={IconUserShield} title={t("forms.adminRole.roleDetails")} description={t("forms.adminRole.roleDetailsDesc")} />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="name"
                                rules={{ required: t("forms.adminRole.roleNameRequired", "Role name is required.") }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("forms.adminRole.roleName")}</FormLabel>
                                        <FormDescription>
                                            {t("forms.adminRole.roleNameDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder={t("forms.adminRole.roleNamePlaceholder")} />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    <SectionCard variant="violet">
                        <SectionCard.Header icon={IconNotes} title={t("forms.common.description")} description={t("forms.adminRole.descriptionDesc")} />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="description"
                                render={({ field }) => (
                                    <FormItem>
                                        <SmartDescription
                                            value={field.value}
                                            onChange={field.onChange}
                                            placeholder={t("forms.adminRole.descPlaceholder")}
                                            title={form.watch("name")}
                                            entityType={t("admin.roles.title")}
                                            enableMetaPrompt
                                        >
                                            <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                                            <SmartDescription.Description>{t("forms.adminRole.descPlaceholder")}</SmartDescription.Description>
                                        </SmartDescription>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    {sections.length > 0 && (
                        <SectionCard variant="emerald">
                            <SectionCard.Header icon={IconLock} title={t("forms.adminRole.privileges")} description={t("forms.adminRole.privilegesDesc")} />
                            <SectionCard.Content>
                                <div className="overflow-x-auto rounded-lg border">
                                    <table className="w-full text-sm">
                                        <thead>
                                            <tr className="border-b bg-muted/50">
                                                <th className="text-left font-medium px-4 py-3">{t("forms.adminRole.resource")}</th>
                                                {ACTIONS.map((a) => (
                                                    <th key={a} className="text-center font-medium px-3 py-3 w-24">{t(`forms.adminRole.${a === "VIEW" ? "view" : a === "CREATE" ? "create" : a === "EDIT" ? "editPriv" : "deletePriv"}`)}</th>
                                                ))}
                                                <th className="text-center font-medium px-3 py-3 w-24">{t("forms.adminRole.allPriv")}</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {sections.map((sec) => (
                                                <Fragment key={sec.section}>
                                                    <tr className="bg-muted/30">
                                                        <td colSpan={ACTIONS.length + 2} className="px-4 py-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                                                            {t(sec.section)}
                                                        </td>
                                                    </tr>
                                                    {sec.rows.map(({ label, privileges }) => {
                                                        const allChecked = privileges.every((p) => selectedIds.has(p.id));
                                                        return (
                                                            <tr key={label} className="border-b last:border-b-0 hover:bg-muted/20 transition-colors">
                                                                <td className="px-4 py-3 pl-6 font-medium">{t(label)}</td>
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

                </form>
        </Form>
    );
};
