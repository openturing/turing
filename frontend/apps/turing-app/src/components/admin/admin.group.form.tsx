import { ROUTES } from "@/app/routes.const";
import {
    Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { SmartDescription } from "@/components/ui/smart-description";
import { BentoActionsMenu, BentoFormHero } from "@/components/bento";
import { StickyPageHeader } from "@/components/sticky-page-header";
import { SectionCard } from "@/components/ui/section-card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { TurGroup } from "@/models/auth/group";
import type { TurRole } from "@/models/auth/role";
import type { TurUser } from "@/models/auth/user";
import { TurGroupService } from "@/services/auth/group.service";
import { TurRoleService } from "@/services/auth/role.service";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import {
    IconCheck, IconDeviceFloppy, IconNotes, IconPlus, IconSearch, IconTrash, IconUserShield, IconUsers, IconUsersGroup, IconX,
} from "@tabler/icons-react";
import { GradientButton } from "@/components/ui/gradient-button";
import { DialogDelete } from "@/components/dialog.delete";
import { type ReactNode, useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turGroupService = new TurGroupService();
const turRoleService = new TurRoleService();
const turAdminUserService = new TurAdminUserService();

interface Props {
    value: TurGroup;
    isNew: boolean;
    onDelete?: () => void;
    open?: boolean;
    setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
    /** List route to return to on save/cancel. Defaults to the console admin
     *  groups list; the Bento surface passes its own route (T567). */
    listRoute?: string;
    /** Bento hero descriptor. When set, the form renders the standard
     *  {@link BentoFormHero} (Save/Cancel in the hero + scroll-linked sticky
     *  save-bar morph); otherwise it renders the console {@link StickyPageHeader}. */
    bentoHero?: {
        backTo: string;
        backLabel: string;
        leading: ReactNode;
        title: string;
        subtitle: string;
    };
}

export const AdminGroupForm: React.FC<Props> = ({ value, isNew, onDelete, open, setOpen, listRoute = ROUTES.ADMIN_GROUPS, bentoHero }) => {
    const { t } = useTranslation();
    const form = useForm<TurGroup>({ defaultValues: value });
    const navigate = useNavigate();
    const isBento = bentoHero !== undefined;
    const [allRoles, setAllRoles] = useState<TurRole[]>([]);
    const [allUsers, setAllUsers] = useState<TurUser[]>([]);
    const [groupRoles, setGroupRoles] = useState<TurRole[]>([]);
    const [groupUsers, setGroupUsers] = useState<TurUser[]>([]);
    const [roleSearch, setRoleSearch] = useState("");
    const [userSearch, setUserSearch] = useState("");

    useEffect(() => {
        form.reset(value);
        setGroupRoles(value.turRoles ?? []);
        setGroupUsers(value.turUsers ?? []);
    }, [value]);

    useEffect(() => {
        turRoleService.query().then(setAllRoles);
        turAdminUserService.query().then(setAllUsers);
    }, []);

    // Roles: search filters available (unassigned) roles
    const assignedRoleIds = useMemo(() => new Set(groupRoles.map(r => r.id)), [groupRoles]);

    const filteredAvailableRoles = useMemo(() => {
        const unassigned = allRoles.filter(r => !assignedRoleIds.has(r.id));
        if (!roleSearch) return unassigned;
        const q = roleSearch.toLowerCase();
        return unassigned.filter(r =>
            r.name.toLowerCase().includes(q) || (r.description ?? "").toLowerCase().includes(q)
        );
    }, [allRoles, assignedRoleIds, roleSearch]);

    function addRole(role: TurRole) {
        setGroupRoles(prev => [...prev, role]);
        setRoleSearch("");
    }

    function removeRole(roleId: string) {
        setGroupRoles(prev => prev.filter(r => r.id !== roleId));
    }

    // Users: search filters available (unassigned) users
    const assignedUsernames = useMemo(() => new Set(groupUsers.map(u => u.username)), [groupUsers]);

    const filteredAvailableUsers = useMemo(() => {
        const unassigned = allUsers.filter(u => !assignedUsernames.has(u.username));
        if (!userSearch) return unassigned;
        const q = userSearch.toLowerCase();
        return unassigned.filter(u =>
            u.username.toLowerCase().includes(q) ||
            (u.firstName ?? "").toLowerCase().includes(q) ||
            (u.lastName ?? "").toLowerCase().includes(q) ||
            (u.email ?? "").toLowerCase().includes(q)
        );
    }, [allUsers, assignedUsernames, userSearch]);

    function addUser(user: TurUser) {
        setGroupUsers(prev => [...prev, user]);
        setUserSearch("");
    }

    function removeUser(username: string) {
        setGroupUsers(prev => prev.filter(u => u.username !== username));
    }

    async function onSubmit(data: TurGroup) {
        const groupPayload: TurGroup = {
            ...data,
            turRoles: groupRoles,
            turUsers: groupUsers,
        };
        try {
            if (isNew) {
                const result = await turGroupService.create(groupPayload);
                if (result) {
                    toast.success(t("forms.adminGroup.created", { name: data.name }));
                    navigate(listRoute);
                } else {
                    toast.error(t("forms.adminGroup.createFailed"));
                }
            } else {
                const result = await turGroupService.update(data.id, groupPayload);
                if (result) {
                    toast.success(t("forms.adminGroup.groupUpdated", { name: data.name }));
                } else {
                    toast.error(t("forms.adminGroup.updateFailed"));
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("forms.adminGroup.saveFailed"));
        }
    }

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className={isBento ? "flex flex-col gap-5 pb-8" : "space-y-4 px-4 lg:px-6 pb-8"}>
                {bentoHero ? (
                    <>
                        <BentoFormHero
                            backTo={bentoHero.backTo}
                            backLabel={bentoHero.backLabel}
                            leading={bentoHero.leading}
                            title={bentoHero.title}
                            subtitle={bentoHero.subtitle}
                            stickyTitle={bentoHero.title}
                            onCancel={() => navigate(listRoute)}
                            saveDisabled={false}
                            trailing={onDelete && open !== undefined && setOpen ? (
                                <BentoActionsMenu
                                    actions={[{
                                        label: t("forms.formActions.delete"),
                                        icon: IconTrash,
                                        tone: "destructive",
                                        onSelect: () => setOpen(true),
                                    }]}
                                />
                            ) : undefined}
                        />
                        {onDelete && open !== undefined && setOpen && (
                            <DialogDelete feature="Group" name={isNew ? t("admin.groups.newGroup") : (value?.name ?? "")} onDelete={onDelete} open={open} setOpen={setOpen} trigger={<span className="hidden" aria-hidden />} />
                        )}
                    </>
                ) : (
                    <StickyPageHeader>
                        <StickyPageHeader.Title
                            icon={IconUsersGroup}
                            feature="Group"
                            description={isNew ? t("admin.groups.createDescription") : t("admin.groups.editDescription")}
                        />
                        <StickyPageHeader.Actions>
                            {onDelete && open !== undefined && setOpen && <DialogDelete feature="Group" name={isNew ? t("admin.groups.newGroup") : (value?.name ?? "")} onDelete={onDelete} open={open} setOpen={setOpen} />}
                            <GradientButton type="submit" size="sm">
                                <IconDeviceFloppy className="size-4" />
                                {t("forms.formActions.saveChanges")}
                            </GradientButton>
                            <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(listRoute)}>
                                <IconX className="size-4" />
                                {t("forms.formActions.cancel")}
                            </GradientButton>
                        </StickyPageHeader.Actions>
                    </StickyPageHeader>
                )}
                    {/* Group Details */}
                    <SectionCard variant="blue">
                        <SectionCard.Header icon={IconUsersGroup} title={t("forms.adminGroup.groupDetails")} description={t("forms.adminGroup.groupDetailsDesc")} />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="name"
                                rules={{ required: t("forms.adminGroup.groupNameRequired", "Group name is required.") }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("forms.adminGroup.groupName")}</FormLabel>
                                        <FormDescription>
                                            {t("forms.adminGroup.groupNameDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder={t("forms.adminGroup.groupNamePlaceholder")} />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    {/* Description */}
                    <SectionCard variant="violet">
                        <SectionCard.Header icon={IconNotes} title={t("forms.common.description")} description={t("forms.adminGroup.descriptionDesc")} />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="description"
                                render={({ field }) => (
                                    <FormItem>
                                        <SmartDescription
                                            value={field.value}
                                            onChange={field.onChange}
                                            placeholder={t("forms.adminGroup.descPlaceholder")}
                                            title={form.watch("name")}
                                            entityType={t("admin.groups.title")}
                                            enableMetaPrompt
                                        >
                                            <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                                            <SmartDescription.Description>{t("forms.adminGroup.descPlaceholder")}</SmartDescription.Description>
                                        </SmartDescription>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    {/* Users & Roles Tabs */}
                    <SectionCard variant="emerald">
                        <SectionCard.Header icon={IconUsers} title={t("forms.adminGroup.membersRoles")} description={t("forms.adminGroup.membersRolesDesc")} />
                        <SectionCard.Content>
                            <Tabs defaultValue="users">
                                <TabsList>
                                    <TabsTrigger value="users">
                                        <IconUsers className="size-4" />
                                        {t("forms.adminGroup.users", { count: groupUsers.length })}
                                    </TabsTrigger>
                                    <TabsTrigger value="roles">
                                        <IconUserShield className="size-4" />
                                        {t("forms.adminGroup.roles", { count: groupRoles.length })}
                                    </TabsTrigger>
                                </TabsList>

                                {/* Users Tab */}
                                <TabsContent value="users" className="space-y-3 mt-3">
                                    {/* Search to add */}
                                    <div className="relative">
                                        <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                                        <Input
                                            placeholder={t("forms.adminGroup.searchUsers")}
                                            value={userSearch}
                                            onChange={(e) => setUserSearch(e.target.value)}
                                            className="pl-9"
                                        />
                                    </div>

                                    {/* Search results */}
                                    {userSearch && (
                                        <div className="rounded-lg border max-h-48 overflow-y-auto">
                                            {filteredAvailableUsers.length === 0 ? (
                                                <p className="text-sm text-muted-foreground p-3">{t("forms.adminGroup.noUsersFound")}</p>
                                            ) : (
                                                filteredAvailableUsers.map((user) => (
                                                    <button
                                                        key={user.username}
                                                        type="button"
                                                        onClick={() => addUser(user)}
                                                        className="w-full flex items-center gap-3 p-3 text-left hover:bg-muted/50 transition-colors cursor-pointer border-b last:border-b-0"
                                                    >
                                                        <IconPlus className="size-4 shrink-0 text-primary" />
                                                        <div className="flex-1 min-w-0">
                                                            <div className="text-sm font-medium">
                                                                {user.firstName} {user.lastName}
                                                            </div>
                                                            <div className="text-xs text-muted-foreground">
                                                                @{user.username} {user.email && `· ${user.email}`}
                                                            </div>
                                                        </div>
                                                    </button>
                                                ))
                                            )}
                                        </div>
                                    )}

                                    {/* Assigned users */}
                                    {groupUsers.length === 0 ? (
                                        <p className="text-sm text-muted-foreground">{t("forms.adminGroup.noMembers")}</p>
                                    ) : (
                                        <div className="space-y-2">
                                            {groupUsers.map((user) => (
                                                <div
                                                    key={user.username}
                                                    className="flex items-center justify-between gap-3 p-3 rounded-lg border"
                                                >
                                                    <div className="flex items-center gap-2 flex-1 min-w-0">
                                                        <IconCheck className="size-4 shrink-0 text-emerald-500" />
                                                        <div className="flex-1 min-w-0">
                                                            <div className="text-sm font-medium">
                                                                {user.firstName} {user.lastName}
                                                            </div>
                                                            <div className="text-xs text-muted-foreground">
                                                                @{user.username} {user.email && `· ${user.email}`}
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <button
                                                        type="button"
                                                        onClick={() => removeUser(user.username)}
                                                        className="shrink-0 p-1 rounded-md text-destructive hover:bg-destructive/10 transition-colors"
                                                        title={`Remove ${user.username}`}
                                                    >
                                                        <IconX className="size-4" />
                                                    </button>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </TabsContent>

                                {/* Roles Tab */}
                                <TabsContent value="roles" className="space-y-3 mt-3">
                                    {/* Search to add */}
                                    <div className="relative">
                                        <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                                        <Input
                                            placeholder={t("forms.adminGroup.searchRoles")}
                                            value={roleSearch}
                                            onChange={(e) => setRoleSearch(e.target.value)}
                                            className="pl-9"
                                        />
                                    </div>

                                    {/* Search results */}
                                    {roleSearch && (
                                        <div className="rounded-lg border max-h-48 overflow-y-auto">
                                            {filteredAvailableRoles.length === 0 ? (
                                                <p className="text-sm text-muted-foreground p-3">{t("forms.adminGroup.noRolesFound")}</p>
                                            ) : (
                                                filteredAvailableRoles.map((role) => (
                                                    <button
                                                        key={role.id}
                                                        type="button"
                                                        onClick={() => addRole(role)}
                                                        className="w-full flex items-center gap-3 p-3 text-left hover:bg-muted/50 transition-colors cursor-pointer border-b last:border-b-0"
                                                    >
                                                        <IconPlus className="size-4 shrink-0 text-primary" />
                                                        <div className="flex-1 min-w-0">
                                                            <div className="text-sm font-medium">{role.name}</div>
                                                            {role.description && (
                                                                <div className="text-xs text-muted-foreground mt-0.5">{role.description}</div>
                                                            )}
                                                        </div>
                                                    </button>
                                                ))
                                            )}
                                        </div>
                                    )}

                                    {/* Assigned roles */}
                                    {groupRoles.length === 0 ? (
                                        <p className="text-sm text-muted-foreground">{t("forms.adminGroup.noRolesAssigned")}</p>
                                    ) : (
                                        <div className="space-y-2">
                                            {groupRoles.map((role) => (
                                                <div
                                                    key={role.id}
                                                    className="flex items-center justify-between gap-3 p-3 rounded-lg border"
                                                >
                                                    <div className="flex items-center gap-2 flex-1 min-w-0">
                                                        <IconCheck className="size-4 shrink-0 text-emerald-500" />
                                                        <div className="flex-1 min-w-0">
                                                            <div className="text-sm font-medium">{role.name}</div>
                                                            {role.description && (
                                                                <div className="text-xs text-muted-foreground mt-0.5">{role.description}</div>
                                                            )}
                                                        </div>
                                                    </div>
                                                    <button
                                                        type="button"
                                                        onClick={() => removeRole(role.id)}
                                                        className="shrink-0 p-1 rounded-md text-destructive hover:bg-destructive/10 transition-colors"
                                                        title={`Remove ${role.name}`}
                                                    >
                                                        <IconX className="size-4" />
                                                    </button>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </TabsContent>
                            </Tabs>
                        </SectionCard.Content>
                    </SectionCard>

                </form>
        </Form>
    );
};
