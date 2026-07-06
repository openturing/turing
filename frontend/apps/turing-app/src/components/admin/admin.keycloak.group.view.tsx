import { ROUTES } from "@/app/routes.const";
import { StickyPageHeader } from "@/components/sticky-page-header";
import { SectionCard } from "@/components/ui/section-card";
import { GradientButton } from "@/components/ui/gradient-button";
import { Badge } from "@/components/ui/badge";
import type { TurKeycloakGroup } from "@/models/auth/keycloak-group";
import {
    IconArrowLeft, IconKey, IconUsers, IconUsersGroup, IconUserShield,
} from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

interface Props {
    value: TurKeycloakGroup;
    /** List route the Back button returns to. Defaults to the console admin
     *  groups list; the Bento surface passes its own route (T567). */
    listRoute?: string;
    /** Optional header override. The console renders its own sidebar-coupled
     *  {@link StickyPageHeader}; Bento passes a `BentoHero` (T567). */
    header?: ReactNode;
}

export const AdminKeycloakGroupView: React.FC<Props> = ({ value, listRoute = ROUTES.ADMIN_GROUPS, header }) => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const isBento = header !== undefined;

    return (
        <div className={isBento ? "flex flex-col gap-5 pb-8" : "space-y-4 px-4 lg:px-6 pb-8"}>
            {isBento ? header : (
                <StickyPageHeader>
                    <StickyPageHeader.Title
                        icon={IconUsersGroup}
                        feature="Group"
                        description={t("admin.groups.keycloakViewDescription")}
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
                    title={t("admin.groups.keycloakSourceTitle")}
                    description={t("admin.groups.keycloakSourceDesc")}
                />
                <SectionCard.Content>
                    <div className="grid gap-4 sm:grid-cols-2">
                        <Field label={t("forms.adminGroup.groupName")} value={value.name} />
                        <Field label="Path" value={value.path} mono />
                        <Field label="ID" value={value.id} mono />
                    </div>
                </SectionCard.Content>
            </SectionCard>

            <SectionCard variant="emerald">
                <SectionCard.Header
                    icon={IconUsers}
                    title={t("admin.groups.keycloakMembersTitle")}
                    description={t("admin.groups.keycloakMembersDesc")}
                />
                <SectionCard.Content>
                    {value.members && value.members.length > 0 ? (
                        <div className="space-y-2">
                            {value.members.map((m) => {
                                const fullName = [m.firstName, m.lastName].filter(Boolean).join(" ");
                                return (
                                    <div key={m.id} className="flex items-center justify-between gap-3 p-3 rounded-lg border">
                                        <div className="flex items-center gap-2 flex-1 min-w-0">
                                            <IconUsers className="size-4 shrink-0 text-emerald-500" />
                                            <div className="flex-1 min-w-0">
                                                <div className="text-sm font-medium">{fullName || m.username}</div>
                                                <div className="text-xs text-muted-foreground">
                                                    @{m.username} {m.email && `· ${m.email}`}
                                                </div>
                                            </div>
                                        </div>
                                        {m.enabled === false && (
                                            <Badge variant="destructive">{t("admin.users.disabled")}</Badge>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    ) : (
                        <p className="text-sm text-muted-foreground">{t("forms.adminGroup.noMembers")}</p>
                    )}
                </SectionCard.Content>
            </SectionCard>

            {value.realmRoles && value.realmRoles.length > 0 && (
                <SectionCard variant="amber">
                    <SectionCard.Header
                        icon={IconUserShield}
                        title={t("admin.groups.keycloakRealmRolesTitle")}
                        description={t("admin.groups.keycloakRealmRolesDesc")}
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

            {value.subGroups && value.subGroups.length > 0 && (
                <SectionCard variant="violet">
                    <SectionCard.Header
                        icon={IconUsersGroup}
                        title={t("admin.groups.keycloakSubGroupsTitle")}
                        description={t("admin.groups.keycloakSubGroupsDesc")}
                    />
                    <SectionCard.Content>
                        <div className="flex flex-wrap gap-2">
                            {value.subGroups.map((g) => (
                                <Badge key={g.id} variant="secondary" className="text-sm">
                                    <IconUsersGroup className="size-3 mr-1" /> {g.name}
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
