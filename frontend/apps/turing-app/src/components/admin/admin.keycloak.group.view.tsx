import { SectionCard } from "@/components/ui/section-card";
import { Badge } from "@/components/ui/badge";
import type { TurKeycloakGroup } from "@/models/auth/keycloak-group";
import {
    IconKey, IconUsers, IconUsersGroup, IconUserShield,
} from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

interface Props {
    value: TurKeycloakGroup;
    /** The page hero. T1026: it used to be optional, and omitting it selected a
     *  console header no caller ever asked for. */
    header?: ReactNode;
}

export const AdminKeycloakGroupView: React.FC<Props> = ({ value, header }) => {
    const { t } = useTranslation();

    return (
        <div className="flex flex-col gap-5 pb-8">
            {header}

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
