import { ROUTES } from "@/app/routes.const";
import { StickyPageHeader } from "@/components/sticky-page-header";
import { SectionCard } from "@/components/ui/section-card";
import { GradientButton } from "@/components/ui/gradient-button";
import { Badge } from "@/components/ui/badge";
import type { TurKeycloakUser } from "@/models/auth/keycloak-user";
import {
    IconArrowLeft, IconKey, IconMail, IconShieldCheck, IconUser, IconUsersGroup, IconUserShield,
} from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

interface Props {
    value: TurKeycloakUser;
    /** List route the Back button returns to. Defaults to the console admin
     *  users list; the Bento surface passes its own route (T567). */
    listRoute?: string;
    /** Optional header override. The console renders its own sidebar-coupled
     *  {@link StickyPageHeader}; Bento passes a `BentoHero` (T567). */
    header?: ReactNode;
}

export const AdminKeycloakUserView: React.FC<Props> = ({ value, listRoute = ROUTES.ADMIN_USERS, header }) => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const isBento = header !== undefined;

    const fullName = [value.firstName, value.lastName].filter(Boolean).join(" ");

    return (
        <div className={isBento ? "flex flex-col gap-5 pb-8" : "space-y-4 px-4 lg:px-6 pb-8"}>
            {isBento ? header : (
                <StickyPageHeader>
                    <StickyPageHeader.Title
                        icon={IconUser}
                        feature="User"
                        description={t("admin.users.keycloakViewDescription")}
                    />
                    <StickyPageHeader.Actions>
                        <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(listRoute)}>
                            <IconArrowLeft className="size-4" />
                            {t("forms.formActions.back", "Back")}
                        </GradientButton>
                    </StickyPageHeader.Actions>
                </StickyPageHeader>
            )}

            <SectionCard variant="blue">
                <SectionCard.Header
                    icon={IconKey}
                    title={t("admin.users.keycloakSourceTitle")}
                    description={t("admin.users.keycloakSourceDesc")}
                />
                <SectionCard.Content>
                    <div className="grid gap-4 sm:grid-cols-2">
                        <Field label={t("forms.adminUser.username", "Username")} value={value.username} />
                        <Field label={t("admin.users.fullName")} value={fullName || "—"} />
                        <Field label={t("forms.adminUser.firstName", "First Name")} value={value.firstName} />
                        <Field label={t("forms.adminUser.lastName", "Last Name")} value={value.lastName} />
                        <Field label="ID" value={value.id} mono />
                    </div>
                </SectionCard.Content>
            </SectionCard>

            <SectionCard variant="violet">
                <SectionCard.Header
                    icon={IconMail}
                    title={t("forms.adminUser.contact")}
                    description={t("forms.adminUser.contactDesc")}
                />
                <SectionCard.Content>
                    <div className="grid gap-4 sm:grid-cols-2">
                        <Field label={t("forms.adminUser.email", "Email")} value={value.email ?? "—"} />
                        <div>
                            <div className="text-xs text-muted-foreground">{t("admin.users.emailVerified")}</div>
                            <div className="mt-1">
                                {value.emailVerified ? (
                                    <Badge variant="default" className="bg-emerald-600">
                                        <IconShieldCheck className="size-3 mr-1" /> {t("common.yes", "Yes")}
                                    </Badge>
                                ) : (
                                    <Badge variant="secondary">{t("common.no", "No")}</Badge>
                                )}
                            </div>
                        </div>
                        <div>
                            <div className="text-xs text-muted-foreground">{t("admin.users.enabled")}</div>
                            <div className="mt-1">
                                {value.enabled ? (
                                    <Badge variant="default" className="bg-emerald-600">{t("common.yes", "Yes")}</Badge>
                                ) : (
                                    <Badge variant="destructive">{t("common.no", "No")}</Badge>
                                )}
                            </div>
                        </div>
                    </div>
                </SectionCard.Content>
            </SectionCard>

            <SectionCard variant="amber">
                <SectionCard.Header
                    icon={IconUsersGroup}
                    title={t("admin.users.keycloakGroupsTitle")}
                    description={t("admin.users.keycloakGroupsDesc")}
                />
                <SectionCard.Content>
                    {value.groups && value.groups.length > 0 ? (
                        <div className="flex flex-wrap gap-2">
                            {value.groups.map((g) => (
                                <Badge key={g} variant="secondary" className="text-sm">
                                    <IconUsersGroup className="size-3 mr-1" /> {g}
                                </Badge>
                            ))}
                        </div>
                    ) : (
                        <p className="text-sm text-muted-foreground">{t("forms.adminUser.noGroupsAssigned")}</p>
                    )}
                </SectionCard.Content>
            </SectionCard>

            {value.realmRoles && value.realmRoles.length > 0 && (
                <SectionCard variant="emerald">
                    <SectionCard.Header
                        icon={IconUserShield}
                        title={t("admin.users.keycloakRealmRolesTitle")}
                        description={t("admin.users.keycloakRealmRolesDesc")}
                    />
                    <SectionCard.Content>
                        <div className="flex flex-wrap gap-2">
                            {value.realmRoles.map((r) => (
                                <Badge key={r} variant="outline" className="text-sm">
                                    <IconUserShield className="size-3 mr-1" /> {r}
                                </Badge>
                            ))}
                        </div>
                    </SectionCard.Content>
                </SectionCard>
            )}
        </div>
    );
};

const Field: React.FC<{ label: string; value?: string; mono?: boolean }> = ({ label, value, mono }) => (
    <div>
        <div className="text-xs text-muted-foreground">{label}</div>
        <div className={`mt-1 text-sm ${mono ? "font-mono" : "font-medium"}`}>{value || "—"}</div>
    </div>
);
