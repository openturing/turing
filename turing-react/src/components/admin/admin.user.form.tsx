import { ROUTES } from "@/app/routes.const";
import { DiceBearAvatarPicker } from "@/components/dicebear-avatar-picker";
import {
    Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage,
} from "@/components/ui/form";
import { GradientAvatar, GradientAvatarFallback, GradientAvatarImage } from "@/components/ui/gradient-avatar";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { FormActions } from "@/components/ui/form-actions";
import { SectionCard } from "@/components/ui/section-card";
import type { TurGroup } from "@/models/auth/group";
import type { TurUser } from "@/models/auth/user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { TurGroupService } from "@/services/auth/group.service";
import { TurUserService } from "@/services/auth/user.service";
import { IconLock, IconMail, IconPalette, IconTrash, IconUser, IconUsersGroup } from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";
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
    const [selectedGroupIds, setSelectedGroupIds] = useState<Set<string>>(new Set());
    const [avatarUrl, setAvatarUrl] = useState<string | undefined>(value.avatarUrl ?? undefined);
    const [pickerOpen, setPickerOpen] = useState(false);

    useEffect(() => {
        form.reset(value);
        setAvatarUrl(value.avatarUrl ?? undefined);
        const groupIds = new Set((value.turGroups ?? []).map(g => g.id));
        setSelectedGroupIds(groupIds);
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

    function toggleGroup(groupId: string) {
        setSelectedGroupIds(prev => {
            const next = new Set(prev);
            if (next.has(groupId)) {
                next.delete(groupId);
            } else {
                next.add(groupId);
            }
            return next;
        });
    }

    async function onSubmit(data: TurUser) {
        const userPayload = {
            ...data,
            avatarUrl: avatarUrl ?? null,
            turGroups: allGroups.filter(g => selectedGroupIds.has(g.id)),
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
                            <div className="flex items-center gap-4">
                                <div className="relative group">
                                    <GradientAvatar className="size-16">
                                        {avatarUrl
                                            ? <GradientAvatarImage src={avatarUrl} alt={username} />
                                            : null
                                        }
                                        <GradientAvatarFallback className="text-lg">{initials}</GradientAvatarFallback>
                                    </GradientAvatar>
                                    <button
                                        type="button"
                                        title="Choose avatar"
                                        onClick={() => setPickerOpen(true)}
                                        className="absolute inset-0 flex items-center justify-center rounded-full bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
                                    >
                                        <IconPalette className="size-5 text-white" />
                                    </button>
                                </div>
                                <div>
                                    <div className="text-sm font-medium">{firstName} {lastName}</div>
                                    {username && <div className="text-xs text-muted-foreground">@{username}</div>}
                                    <div className="flex items-center gap-2 mt-1">
                                        <button
                                            type="button"
                                            onClick={() => setPickerOpen(true)}
                                            className="text-xs text-primary hover:underline cursor-pointer"
                                        >
                                            Choose avatar
                                        </button>
                                        {avatarUrl && (
                                            <button
                                                type="button"
                                                onClick={handleAvatarRemove}
                                                className="text-xs text-destructive hover:underline cursor-pointer flex items-center gap-0.5"
                                            >
                                                <IconTrash className="size-3" />
                                                Remove
                                            </button>
                                        )}
                                    </div>
                                </div>
                            </div>
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

                    {/* Groups */}
                    <SectionCard variant="amber">
                        <SectionCard.Header icon={IconUsersGroup} title="Groups" description="Assign this user to one or more groups." />
                        <SectionCard.Content>
                            {allGroups.length === 0 ? (
                                <p className="text-sm text-muted-foreground">No groups available.</p>
                            ) : (
                                <div className="space-y-3">
                                    {allGroups.map((group) => (
                                        <label
                                            key={group.id}
                                            className="flex items-start gap-3 p-3 rounded-lg border hover:bg-muted/50 transition-colors cursor-pointer"
                                        >
                                            <Checkbox
                                                checked={selectedGroupIds.has(group.id)}
                                                onCheckedChange={() => toggleGroup(group.id)}
                                                className="mt-0.5"
                                            />
                                            <div className="flex-1 min-w-0">
                                                <div className="text-sm font-medium">{group.name}</div>
                                                {group.description && (
                                                    <div className="text-xs text-muted-foreground mt-0.5">{group.description}</div>
                                                )}
                                            </div>
                                        </label>
                                    ))}
                                </div>
                            )}
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

            <DiceBearAvatarPicker
                open={pickerOpen}
                onOpenChange={setPickerOpen}
                onSelect={handleAvatarSelect}
                defaultSeed={username || "user"}
                currentAvatarUrl={avatarUrl}
            />
        </div>
    );
};
