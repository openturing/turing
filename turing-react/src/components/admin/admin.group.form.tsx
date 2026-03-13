import { ROUTES } from "@/app/routes.const";
import {
    Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FormActions } from "@/components/ui/form-actions";
import { SectionCard } from "@/components/ui/section-card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { TurGroup } from "@/models/auth/group";
import type { TurRole } from "@/models/auth/role";
import type { TurUser } from "@/models/auth/user";
import { TurGroupService } from "@/services/auth/group.service";
import { TurRoleService } from "@/services/auth/role.service";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import {
    IconCheck, IconNotes, IconPlus, IconSearch, IconUserShield, IconUsers, IconUsersGroup, IconX,
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

const turGroupService = new TurGroupService();
const turRoleService = new TurRoleService();
const turAdminUserService = new TurAdminUserService();

interface Props {
    value: TurGroup;
    isNew: boolean;
}

export const AdminGroupForm: React.FC<Props> = ({ value, isNew }) => {
    const form = useForm<TurGroup>({ defaultValues: value });
    const navigate = useNavigate();
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
                    toast.success(`Group "${data.name}" was created.`);
                    navigate(ROUTES.ADMIN_GROUPS);
                } else {
                    toast.error("Failed to create group.");
                }
            } else {
                const result = await turGroupService.update(data.id, groupPayload);
                if (result) {
                    toast.success(`Group "${data.name}" was updated.`);
                } else {
                    toast.error("Failed to update group.");
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error("Failed to save group. Please try again.");
        }
    }

    return (
        <div className="px-6">
            <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8">
                    {/* Group Details */}
                    <SectionCard variant="blue">
                        <SectionCard.Header icon={IconUsersGroup} title="Group Details" description="Name and identification for this group." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="name"
                                rules={{ required: "Group name is required." }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Group Name</FormLabel>
                                        <FormDescription>
                                            A unique name to identify this group (e.g. "Administrators", "Editors").
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder="e.g. Administrators" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    {/* Description */}
                    <SectionCard variant="violet">
                        <SectionCard.Header icon={IconNotes} title="Description" description="Optional details about this group's purpose." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="description"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Description</FormLabel>
                                        <FormDescription>
                                            Describe the purpose or scope of permissions for this group.
                                        </FormDescription>
                                        <FormControl>
                                            <Textarea
                                                {...field}
                                                placeholder="e.g. Full administrative access to all features"
                                                className="resize-none"
                                            />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    {/* Users & Roles Tabs */}
                    <SectionCard variant="emerald">
                        <SectionCard.Header icon={IconUsers} title="Members & Roles" description="Manage users and role assignments for this group." />
                        <SectionCard.Content>
                            <Tabs defaultValue="users">
                                <TabsList>
                                    <TabsTrigger value="users">
                                        <IconUsers className="size-4" />
                                        Users ({groupUsers.length})
                                    </TabsTrigger>
                                    <TabsTrigger value="roles">
                                        <IconUserShield className="size-4" />
                                        Roles ({groupRoles.length})
                                    </TabsTrigger>
                                </TabsList>

                                {/* Users Tab */}
                                <TabsContent value="users" className="space-y-3 mt-3">
                                    {/* Search to add */}
                                    <div className="relative">
                                        <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                                        <Input
                                            placeholder="Search users to add..."
                                            value={userSearch}
                                            onChange={(e) => setUserSearch(e.target.value)}
                                            className="pl-9"
                                        />
                                    </div>

                                    {/* Search results */}
                                    {userSearch && (
                                        <div className="rounded-lg border max-h-48 overflow-y-auto">
                                            {filteredAvailableUsers.length === 0 ? (
                                                <p className="text-sm text-muted-foreground p-3">No users found.</p>
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
                                        <p className="text-sm text-muted-foreground">No members in this group yet.</p>
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
                                            placeholder="Search roles to add..."
                                            value={roleSearch}
                                            onChange={(e) => setRoleSearch(e.target.value)}
                                            className="pl-9"
                                        />
                                    </div>

                                    {/* Search results */}
                                    {roleSearch && (
                                        <div className="rounded-lg border max-h-48 overflow-y-auto">
                                            {filteredAvailableRoles.length === 0 ? (
                                                <p className="text-sm text-muted-foreground p-3">No roles found.</p>
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
                                        <p className="text-sm text-muted-foreground">No roles assigned yet.</p>
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

                    <FormActions>
                        <FormActions.Cancel onClick={() => navigate(ROUTES.ADMIN_GROUPS)} />
                        <FormActions.Submit>{isNew ? "Create Group" : "Save Changes"}</FormActions.Submit>
                    </FormActions>
                </form>
            </Form>
        </div>
    );
};
