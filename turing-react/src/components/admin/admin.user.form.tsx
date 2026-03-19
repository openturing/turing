import { ROUTES } from "@/app/routes.const";
import { AvatarField } from "@/components/avatar-field";
import {
    Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { FormActions } from "@/components/ui/form-actions";
import { SectionCard } from "@/components/ui/section-card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { TurGroup } from "@/models/auth/group";
import type { TurRole } from "@/models/auth/role";
import type { TurUser } from "@/models/auth/user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { TurGroupService } from "@/services/auth/group.service";
import { TurUserService } from "@/services/auth/user.service";
import {
    IconCheck, IconLock, IconMail, IconPlus, IconSearch,
    IconUser, IconUserShield, IconUsersGroup, IconX,
} from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

const turAdminUserService = new TurAdminUserService();
const turGroupService = new TurGroupService();
const turUserService = new TurUserService();

interface Props {
    value: TurUser;
    isNew: boolean;
}

export const AdminUserForm: React.FC<Props> = ({ value, isNew }) => {
    const form = useForm<TurUser>({ defaultValues: value });
    const navigate = useNavigate();
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
            toast.success("Avatar selected.");
        } else {
            try {
                await turUserService.saveAvatarUrl(value.username, url);
                setAvatarUrl(url);
                form.setValue("avatarUrl", url);
                toast.success("Avatar updated.");
            } catch {
                toast.error("Failed to save avatar.");
            }
        }
    }, [value, isNew]);

    const handleAvatarRemove = useCallback(async () => {
        if (isNew) {
            setAvatarUrl(undefined);
            form.setValue("avatarUrl", undefined);
            toast.success("Avatar removed.");
        } else {
            try {
                await turUserService.saveAvatarUrl(value.username, null);
                setAvatarUrl(undefined);
                form.setValue("avatarUrl", undefined);
                toast.success("Avatar removed.");
            } catch {
                toast.error("Failed to remove avatar.");
            }
        }
    }, [value, isNew]);

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
                    toast.success(`User ${data.username} was created.`);
                    navigate(ROUTES.ADMIN_USERS);
                } else {
                    toast.error("Failed to create user.");
                }
            } else {
                const result = await turAdminUserService.update(data.username, userPayload as TurUser);
                if (result) {
                    toast.success(`User ${data.username} was updated.`);
                } else {
                    toast.error("Failed to update user.");
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error("Failed to save user. Please try again.");
        }
    }

    return (
        <div className="px-6">
            <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8">
                    {/* Avatar Section */}
                    <SectionCard variant="slate">
                        <SectionCard.Header icon={IconUser} title="Avatar" description="Profile picture for this user." />
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
                        <SectionCard.Header icon={IconUser} title="Account Information" description="Username and personal details." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="username"
                                rules={{ required: "Username is required." }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Username</FormLabel>
                                        <FormDescription>Unique identifier for this user account.</FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder="e.g. john.doe" readOnly={!isNew} />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                            <FormField
                                control={form.control}
                                name="firstName"
                                rules={{ required: "First name is required." }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>First Name</FormLabel>
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
                                rules={{ required: "Last name is required." }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Last Name</FormLabel>
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
                        <SectionCard.Header icon={IconMail} title="Contact" description="Email address for notifications and recovery." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="email"
                                rules={{
                                    required: "Email is required.",
                                    pattern: { value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: "Invalid email address." },
                                }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Email</FormLabel>
                                        <FormControl>
                                            <Input {...field} type="email" placeholder="john.doe@example.com" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    {/* Groups & Roles Tabs */}
                    <SectionCard variant="amber">
                        <SectionCard.Header icon={IconUsersGroup} title="Groups & Roles" description="Manage group membership and role assignments." />
                        <SectionCard.Content>
                            <Tabs defaultValue="groups">
                                <TabsList>
                                    <TabsTrigger value="groups">
                                        <IconUsersGroup className="size-4" />
                                        Groups ({userGroups.length})
                                    </TabsTrigger>
                                    <TabsTrigger value="roles">
                                        <IconUserShield className="size-4" />
                                        Roles ({inheritedRoles.length})
                                    </TabsTrigger>
                                </TabsList>

                                {/* Groups Tab */}
                                <TabsContent value="groups" className="space-y-3 mt-3">
                                    {/* Search to add */}
                                    <div className="relative">
                                        <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                                        <Input
                                            placeholder="Search groups to add..."
                                            value={groupSearch}
                                            onChange={(e) => setGroupSearch(e.target.value)}
                                            className="pl-9"
                                        />
                                    </div>

                                    {/* Search results */}
                                    {groupSearch && (
                                        <div className="rounded-lg border max-h-48 overflow-y-auto">
                                            {filteredAvailableGroups.length === 0 ? (
                                                <p className="text-sm text-muted-foreground p-3">No groups found.</p>
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
                                        <p className="text-sm text-muted-foreground">No groups assigned yet.</p>
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
                                        Roles are inherited from assigned groups. To change roles, edit the group's role assignments.
                                    </p>
                                    {inheritedRoles.length === 0 ? (
                                        <p className="text-sm text-muted-foreground">No roles inherited. Assign groups with roles to grant permissions.</p>
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
                        <SectionCard.Header icon={IconLock} title="Security" description="Set or change the user password." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="password"
                                rules={isNew ? { required: "Password is required for new users." } : {}}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{isNew ? "Password" : "New Password"}</FormLabel>
                                        <FormDescription>
                                            {isNew ? "Set the initial password." : "Leave blank to keep the current password."}
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

                    <FormActions>
                        <FormActions.Cancel onClick={() => navigate(ROUTES.ADMIN_USERS)} />
                        <FormActions.Submit>{isNew ? "Create User" : "Save Changes"}</FormActions.Submit>
                    </FormActions>
                </form>
            </Form>

        </div>
    );
};
