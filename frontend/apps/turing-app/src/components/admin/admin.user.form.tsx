import { ROUTES } from "@/app/routes.const";
import { AvatarField } from "@/components/avatar-field";
import {
    Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { BentoActionsMenu, BentoFormHero } from "@/components/bento";
import { StickyPageHeader } from "@/components/sticky-page-header";
import { SectionCard } from "@/components/ui/section-card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { TurGroup } from "@/models/auth/group";
import type { TurRole } from "@/models/auth/role";
import type { TurUser } from "@/models/auth/user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { TurGroupService } from "@/services/auth/group.service";
import { TurUserService } from "@/services/auth/user.service";
import {
    IconCheck, IconDeviceFloppy, IconLock, IconMail, IconPlus, IconSearch,
    IconTrash, IconUser, IconUserShield, IconUsersGroup, IconX,
} from "@tabler/icons-react";
import { GradientButton } from "@/components/ui/gradient-button";
import { DialogDelete } from "@/components/dialog.delete";
import { type ReactNode, useCallback, useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turAdminUserService = new TurAdminUserService();
const turGroupService = new TurGroupService();
const turUserService = new TurUserService();

interface Props {
    value: TurUser;
    isNew: boolean;
    onDelete?: () => void;
    open?: boolean;
    setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
    /** List route to return to on save/cancel. Defaults to the console admin
     *  users list; the Bento surface passes its own route (T567). */
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

export const AdminUserForm: React.FC<Props> = ({ value, isNew, onDelete, open, setOpen, listRoute = ROUTES.ADMIN_USERS, bentoHero }) => {
    const { t } = useTranslation();
    const form = useForm<TurUser>({ defaultValues: value });
    const navigate = useNavigate();
    const isBento = bentoHero !== undefined;
    const [allGroups, setAllGroups] = useState<TurGroup[]>([]);
    const [userGroups, setUserGroups] = useState<TurGroup[]>([]);
    const [groupSearch, setGroupSearch] = useState("");
    const [avatarUrl, setAvatarUrl] = useState<string | undefined>(value.avatarUrl ?? undefined);

    useEffect(() => {
        form.reset(value);
        setAvatarUrl(value.avatarUrl ?? undefined);
        setUserGroups(value.turGroups ?? []);
    }, [value]);

    useEffect(() => {
        turGroupService.query().then(setAllGroups);
    }, []);

    const firstName = form.watch("firstName") ?? "";
    const lastName = form.watch("lastName") ?? "";
    const username = form.watch("username") ?? "";

    const initials = (() => {
        if (!firstName && !lastName) return "?";
        const parts = `${firstName} ${lastName}`.trim().split(" ").filter(Boolean);
        if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
        return parts[0].charAt(0).toUpperCase() + (parts.at(-1)?.charAt(0).toUpperCase() ?? "");
    })();

    const handleAvatarSelect = useCallback(async (url: string) => {
        if (isNew) {
            setAvatarUrl(url);
            form.setValue("avatarUrl", url);
            toast.success(t("forms.adminUser.avatarSelected"));
        } else {
            try {
                await turUserService.saveAvatarUrl(value.username, url);
                setAvatarUrl(url);
                form.setValue("avatarUrl", url);
                toast.success(t("forms.adminUser.avatarUpdated"));
            } catch {
                toast.error(t("forms.adminUser.avatarFailed"));
            }
        }
    }, [value, isNew, t]);

    const handleAvatarRemove = useCallback(async () => {
        if (isNew) {
            setAvatarUrl(undefined);
            form.setValue("avatarUrl", undefined);
            toast.success(t("forms.adminUser.avatarRemoved"));
        } else {
            try {
                await turUserService.saveAvatarUrl(value.username, null);
                setAvatarUrl(undefined);
                form.setValue("avatarUrl", undefined);
                toast.success(t("forms.adminUser.avatarRemoved"));
            } catch {
                toast.error(t("forms.adminUser.avatarRemoveFailed"));
            }
        }
    }, [value, isNew, t]);

    // Groups: search filters available (unassigned) groups
    const assignedGroupIds = useMemo(() => new Set(userGroups.map(g => g.id)), [userGroups]);

    const filteredAvailableGroups = useMemo(() => {
        const unassigned = allGroups.filter(g => !assignedGroupIds.has(g.id));
        if (!groupSearch) return unassigned;
        const q = groupSearch.toLowerCase();
        return unassigned.filter(g =>
            g.name.toLowerCase().includes(q) || (g.description ?? "").toLowerCase().includes(q)
        );
    }, [allGroups, assignedGroupIds, groupSearch]);

    function addGroup(group: TurGroup) {
        setUserGroups(prev => [...prev, group]);
        setGroupSearch("");
    }

    function removeGroup(groupId: string) {
        setUserGroups(prev => prev.filter(g => g.id !== groupId));
    }

    // Roles inherited from assigned groups (read-only)
    // Use allGroups as source of truth for turRoles since the user endpoint
    // may not eagerly load each group's roles
    const inheritedRoles = useMemo(() => {
        const groupMap = new Map(allGroups.map(g => [g.id, g]));
        const roleMap = new Map<string, TurRole>();
        for (const userGroup of userGroups) {
            const fullGroup = groupMap.get(userGroup.id);
            for (const role of fullGroup?.turRoles ?? userGroup.turRoles ?? []) {
                if (!roleMap.has(role.id)) {
                    roleMap.set(role.id, role);
                }
            }
        }
        return Array.from(roleMap.values());
    }, [userGroups, allGroups]);

    async function onSubmit(data: TurUser) {
        const userPayload = {
            ...data,
            avatarUrl: avatarUrl ?? null,
            turGroups: userGroups,
        };
        try {
            if (isNew) {
                const result = await turAdminUserService.create(userPayload as TurUser);
                if (result) {
                    toast.success(t("forms.adminUser.created", { name: data.username }));
                    navigate(listRoute);
                } else {
                    toast.error(t("forms.adminUser.createFailed"));
                }
            } else {
                const result = await turAdminUserService.update(data.username, userPayload as TurUser);
                if (result) {
                    toast.success(t("forms.adminUser.userUpdated", { name: data.username }));
                } else {
                    toast.error(t("forms.adminUser.updateFailed"));
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("forms.adminUser.saveFailed"));
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
                            <DialogDelete feature="User" name={isNew ? t("admin.users.newUser") : (value?.username ?? "")} onDelete={onDelete} open={open} setOpen={setOpen} trigger={<span className="hidden" aria-hidden />} />
                        )}
                    </>
                ) : (
                    <StickyPageHeader>
                        <StickyPageHeader.Title
                            icon={IconUser}
                            feature="User"
                            description={isNew ? t("admin.users.createDescription") : t("admin.users.editDescription")}
                        />
                        <StickyPageHeader.Actions>
                            {onDelete && open !== undefined && setOpen && <DialogDelete feature="User" name={isNew ? t("admin.users.newUser") : (value?.username ?? "")} onDelete={onDelete} open={open} setOpen={setOpen} />}
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
                    {/* Avatar Section */}
                    <SectionCard variant="slate">
                        <SectionCard.Header icon={IconUser} title={t("forms.adminUser.avatar")} description={t("forms.adminUser.avatarDesc")} />
                        <SectionCard.Content>
                            <AvatarField
                                avatarUrl={avatarUrl}
                                initials={initials}
                                seed={username || "user"}
                                onSelect={handleAvatarSelect}
                                onRemove={handleAvatarRemove}
                            >
                                <div className="text-sm font-medium">{firstName} {lastName}</div>
                                {username && <div className="text-xs text-muted-foreground">@{username}</div>}
                            </AvatarField>
                        </SectionCard.Content>
                    </SectionCard>

                    {/* Account Information */}
                    <SectionCard variant="blue">
                        <SectionCard.Header icon={IconUser} title={t("forms.adminUser.accountInfo")} description={t("forms.adminUser.accountInfoDesc")} />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="username"
                                rules={{ required: t("forms.adminUser.usernameRequired", "Username is required.") }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("forms.adminUser.username", "Username")}</FormLabel>
                                        <FormDescription>{t("forms.adminUser.usernameDesc")}</FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder={t("forms.adminUser.usernamePlaceholder")} readOnly={!isNew} />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                            <FormField
                                control={form.control}
                                name="firstName"
                                rules={{ required: t("forms.adminUser.firstNameRequired", "First name is required.") }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("forms.adminUser.firstName", "First Name")}</FormLabel>
                                        <FormControl>
                                            <Input {...field} placeholder="John" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                            <FormField
                                control={form.control}
                                name="lastName"
                                rules={{ required: t("forms.adminUser.lastNameRequired", "Last name is required.") }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("forms.adminUser.lastName", "Last Name")}</FormLabel>
                                        <FormControl>
                                            <Input {...field} placeholder="Doe" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    {/* Contact */}
                    <SectionCard variant="violet">
                        <SectionCard.Header icon={IconMail} title={t("forms.adminUser.contact")} description={t("forms.adminUser.contactDesc")} />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="email"
                                rules={{
                                    required: t("forms.adminUser.emailRequired", "Email is required."),
                                    pattern: { value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: t("forms.adminUser.invalidEmail") },
                                }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("forms.adminUser.email", "Email")}</FormLabel>
                                        <FormControl>
                                            <Input {...field} type="email" placeholder={t("forms.adminUser.emailPlaceholder")} />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    {/* Groups & Roles Tabs */}
                    <SectionCard variant="amber">
                        <SectionCard.Header icon={IconUsersGroup} title={t("forms.adminUser.groupsRoles")} description={t("forms.adminUser.groupsRolesDesc")} />
                        <SectionCard.Content>
                            <Tabs defaultValue="groups">
                                <TabsList>
                                    <TabsTrigger value="groups">
                                        <IconUsersGroup className="size-4" />
                                        {t("forms.adminUser.groups", { count: userGroups.length })}
                                    </TabsTrigger>
                                    <TabsTrigger value="roles">
                                        <IconUserShield className="size-4" />
                                        {t("forms.adminUser.roles", { count: inheritedRoles.length })}
                                    </TabsTrigger>
                                </TabsList>

                                {/* Groups Tab */}
                                <TabsContent value="groups" className="space-y-3 mt-3">
                                    {/* Search to add */}
                                    <div className="relative">
                                        <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                                        <Input
                                            placeholder={t("forms.adminUser.searchGroups")}
                                            value={groupSearch}
                                            onChange={(e) => setGroupSearch(e.target.value)}
                                            className="pl-9"
                                        />
                                    </div>

                                    {/* Search results */}
                                    {groupSearch && (
                                        <div className="rounded-lg border max-h-48 overflow-y-auto">
                                            {filteredAvailableGroups.length === 0 ? (
                                                <p className="text-sm text-muted-foreground p-3">{t("forms.adminUser.noGroupsFound")}</p>
                                            ) : (
                                                filteredAvailableGroups.map((group) => (
                                                    <button
                                                        key={group.id}
                                                        type="button"
                                                        onClick={() => addGroup(group)}
                                                        className="w-full flex items-center gap-3 p-3 text-left hover:bg-muted/50 transition-colors cursor-pointer border-b last:border-b-0"
                                                    >
                                                        <IconPlus className="size-4 shrink-0 text-primary" />
                                                        <div className="flex-1 min-w-0">
                                                            <div className="text-sm font-medium">{group.name}</div>
                                                            {group.description && (
                                                                <div className="text-xs text-muted-foreground mt-0.5">{group.description}</div>
                                                            )}
                                                        </div>
                                                    </button>
                                                ))
                                            )}
                                        </div>
                                    )}

                                    {/* Assigned groups */}
                                    {userGroups.length === 0 ? (
                                        <p className="text-sm text-muted-foreground">{t("forms.adminUser.noGroupsAssigned")}</p>
                                    ) : (
                                        <div className="space-y-2">
                                            {userGroups.map((group) => (
                                                <div
                                                    key={group.id}
                                                    className="flex items-center justify-between gap-3 p-3 rounded-lg border"
                                                >
                                                    <div className="flex items-center gap-2 flex-1 min-w-0">
                                                        <IconCheck className="size-4 shrink-0 text-emerald-500" />
                                                        <div className="flex-1 min-w-0">
                                                            <div className="text-sm font-medium">{group.name}</div>
                                                            {group.description && (
                                                                <div className="text-xs text-muted-foreground mt-0.5">{group.description}</div>
                                                            )}
                                                        </div>
                                                    </div>
                                                    <button
                                                        type="button"
                                                        onClick={() => removeGroup(group.id)}
                                                        className="shrink-0 p-1 rounded-md text-destructive hover:bg-destructive/10 transition-colors"
                                                        title={`Remove ${group.name}`}
                                                    >
                                                        <IconX className="size-4" />
                                                    </button>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </TabsContent>

                                {/* Roles Tab (read-only, inherited from groups) */}
                                <TabsContent value="roles" className="space-y-3 mt-3">
                                    <p className="text-xs text-muted-foreground">
                                        {t("forms.adminUser.rolesInherited")}
                                    </p>
                                    {inheritedRoles.length === 0 ? (
                                        <p className="text-sm text-muted-foreground">{t("forms.adminUser.noRolesInherited")}</p>
                                    ) : (
                                        <div className="space-y-2">
                                            {inheritedRoles.map((role) => (
                                                <div
                                                    key={role.id}
                                                    className="flex items-center gap-3 p-3 rounded-lg border bg-muted/30"
                                                >
                                                    <IconUserShield className="size-4 shrink-0 text-emerald-500" />
                                                    <div className="flex-1 min-w-0">
                                                        <div className="text-sm font-medium">{role.name}</div>
                                                        {role.description && (
                                                            <div className="text-xs text-muted-foreground mt-0.5">{role.description}</div>
                                                        )}
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </TabsContent>
                            </Tabs>
                        </SectionCard.Content>
                    </SectionCard>

                    {/* Security */}
                    <SectionCard variant="emerald">
                        <SectionCard.Header icon={IconLock} title={t("forms.adminUser.security")} description={t("forms.adminUser.securityDesc")} />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="password"
                                rules={isNew ? { required: t("forms.adminUser.passwordRequired", "Password is required for new users.") } : {}}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{isNew ? t("forms.adminUser.password", "Password") : t("forms.adminUser.newPassword")}</FormLabel>
                                        <FormDescription>
                                            {isNew ? t("forms.adminUser.setInitialPassword") : t("forms.adminUser.leaveBlank")}
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} type="password" placeholder="••••••••" value={field.value ?? ""} />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                </form>
        </Form>
    );
};
