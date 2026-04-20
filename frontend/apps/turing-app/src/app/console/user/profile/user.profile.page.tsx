import { IconDeviceFloppy, IconLoader2, IconLock } from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";

import { SubPageHeader } from "@/components/sub.page.header";
import { AvatarField } from "@/components/avatar-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useCurrentUser } from "@/contexts/user.context";
import type { TurUser } from "@/models/auth/user";
import { TurUserService } from "@/services/auth/user.service";
import { IconUser } from "@tabler/icons-react";

const turUserService = new TurUserService();

/**
 * Profile sub-page: personal info, avatar, and password.
 *
 * @since 2026.1.14
 */
export default function UserProfilePage() {
    const { t } = useTranslation();
    const { refreshUser } = useCurrentUser();
    const [user, setUser] = useState<TurUser | null>(null);
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [email, setEmail] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [saving, setSaving] = useState(false);
    const [loading, setLoading] = useState(true);
    const [avatarUrl, setAvatarUrl] = useState<string | undefined>(undefined);

    useSubPageBreadcrumb(t("account.profile.title"));

    const isOAuth2 = !!user?.realm;

    useEffect(() => {
        turUserService.get().then((currentUser) => {
            setUser(currentUser);
            setFirstName(currentUser.firstName ?? "");
            setLastName(currentUser.lastName ?? "");
            setEmail(currentUser.email ?? "");
            setAvatarUrl(currentUser.avatarUrl ?? undefined);
            setLoading(false);
        }).catch(() => {
            toast.error(t("account.loadFailed"));
            setLoading(false);
        });
    }, []);

    const initials = (() => {
        const first = firstName || "";
        const last = lastName || "";
        if (!first && !last) return " ";
        const parts = `${first} ${last}`.trim().split(" ").filter(Boolean);
        if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
        return parts[0].charAt(0).toUpperCase() + (parts.at(-1)?.charAt(0).toUpperCase() ?? "");
    })();

    const handleAvatarSelect = useCallback(async (url: string) => {
        if (!user) return;
        try {
            await turUserService.saveAvatarUrl(user.username, url);
            setAvatarUrl(url);
            refreshUser();
            toast.success(t("account.avatarUpdated"));
        } catch {
            toast.error(t("account.avatarFailed"));
        }
    }, [user]);

    const handleAvatarRemove = useCallback(async () => {
        if (!user) return;
        try {
            await turUserService.saveAvatarUrl(user.username, null);
            setAvatarUrl(undefined);
            refreshUser();
            toast.success(t("account.avatarRemoved"));
        } catch {
            toast.error(t("account.avatarRemoveFailed"));
        }
    }, [user]);

    const handleSave = async () => {
        if (!user) return;
        if (newPassword && newPassword !== confirmPassword) {
            toast.error(t("account.passwordMismatch"));
            return;
        }
        setSaving(true);
        try {
            const payload: Partial<TurUser> = {
                firstName,
                lastName,
                email,
                avatarUrl: avatarUrl ?? undefined,
            };
            if (newPassword) {
                payload.password = newPassword;
            }
            await turUserService.update(user.username, payload);
            refreshUser();
            toast.success(t("account.updateSuccess"));
            setNewPassword("");
            setConfirmPassword("");
        } catch {
            toast.error(t("account.updateFailed"));
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return (
            <div className="flex items-center justify-center h-full">
                <IconLoader2 className="size-8 animate-spin text-muted-foreground" />
            </div>
        );
    }

    if (!user) {
        return (
            <div className="flex items-center justify-center h-full text-muted-foreground">
                {t("account.unableToLoad")}
            </div>
        );
    }

    return (
        <>
            <SubPageHeader
                icon={IconUser}
                name={t("account.profile.title")}
                feature={t("account.profile.title")}
                description={isOAuth2 ? t("account.profile.descriptionOAuth2") : t("account.profile.description")}
            />
            <div className="max-w-2xl mx-auto py-8 px-6">
                {/* Avatar + username */}
                <AvatarField
                    avatarUrl={avatarUrl}
                    initials={initials}
                    seed={user.username}
                    onSelect={handleAvatarSelect}
                    onRemove={handleAvatarRemove}
                    className="mb-8"
                >
                    <div className="text-lg font-medium">{firstName} {lastName}</div>
                    <div className="text-sm text-muted-foreground">@{user.username}</div>
                </AvatarField>

                {/* Profile fields */}
                <div className="space-y-4">
                    {isOAuth2 && user.realm && (
                        <div className="space-y-2">
                            <Label>{t("account.provider")}</Label>
                            <div className="flex items-center gap-2 text-sm text-muted-foreground capitalize">
                                {user.realm}
                            </div>
                        </div>
                    )}
                    <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-2">
                            <Label htmlFor="firstName">{t("account.firstName")}</Label>
                            <Input id="firstName" value={firstName} readOnly={isOAuth2} disabled={isOAuth2} className={isOAuth2 ? "bg-muted" : ""} onChange={(e) => setFirstName(e.target.value)} />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="lastName">{t("account.lastName")}</Label>
                            <Input id="lastName" value={lastName} readOnly={isOAuth2} disabled={isOAuth2} className={isOAuth2 ? "bg-muted" : ""} onChange={(e) => setLastName(e.target.value)} />
                        </div>
                    </div>
                    {email && (
                        <div className="space-y-2">
                            <Label htmlFor="email">{t("account.email")}</Label>
                            <Input id="email" type="email" value={email} readOnly={isOAuth2} disabled={isOAuth2} className={isOAuth2 ? "bg-muted" : ""} onChange={(e) => setEmail(e.target.value)} />
                        </div>
                    )}
                    <div className="space-y-2">
                        <Label htmlFor="username">{t("account.username")}</Label>
                        <Input id="username" value={user.username} disabled className="bg-muted" />
                    </div>
                </div>

                {!isOAuth2 && (
                    <>
                        <Separator className="my-8" />

                        {/* Change password */}
                        <h2 className="text-lg font-medium mb-4 flex items-center gap-2">
                            <IconLock className="size-5" />
                            {t("account.changePassword")}
                        </h2>
                        <div className="space-y-4">
                            <div className="space-y-2">
                                <Label htmlFor="newPassword">{t("account.newPassword")}</Label>
                                <Input id="newPassword" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder={t("account.leaveBlank")} />
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="confirmPassword">{t("account.confirmPassword")}</Label>
                                <Input id="confirmPassword" type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder={t("account.repeatPassword")} />
                            </div>
                        </div>

                        <div className="mt-8">
                            <Button onClick={handleSave} disabled={saving}>
                                {saving ? <IconLoader2 className="size-4 animate-spin mr-2" /> : <IconDeviceFloppy className="size-4 mr-2" />}
                                {t("account.saveChanges")}
                            </Button>
                        </div>
                    </>
                )}
            </div>
        </>
    );
}
