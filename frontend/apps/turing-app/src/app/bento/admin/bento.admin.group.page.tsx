import { ROUTES } from "@/app/routes.const";
import { AdminGroupForm } from "@/components/admin/admin.group.form";
import { AdminKeycloakGroupView } from "@/components/admin/admin.keycloak.group.view";
import { BentoHero } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import type { TurGroup } from "@/models/auth/group";
import type { TurKeycloakGroup } from "@/models/auth/keycloak-group";
import { TurAuthorizationService } from "@/services/auth/authorization.service";
import { TurGroupService } from "@/services/auth/group.service";
import { TurKeycloakAdminService } from "@/services/auth/keycloak-admin.service";
import { IconUsersGroup } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turGroupService = new TurGroupService();
const turKeycloakAdminService = new TurKeycloakAdminService();
const turAuthorizationService = new TurAuthorizationService();

/**
 * Bento admin group detail — T567. Replicates the console detail loader
 * (Keycloak-vs-JPA discovery gate) but reuses the shared `AdminGroupForm` /
 * `AdminKeycloakGroupView` with the Bento list route so save/cancel/back/delete
 * stay inside the Bento shell.
 */
export default function BentoAdminGroupPage() {
  const { t } = useTranslation();
  const { groupId } = useParams() as { groupId: string };
  const [group, setGroup] = useState<TurGroup>();
  const [keycloakGroup, setKeycloakGroup] = useState<TurKeycloakGroup>();
  const [isNew, setIsNew] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [keycloak, setKeycloak] = useState<boolean | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    turAuthorizationService
      .discovery()
      .then((d) => setKeycloak(!!d.keycloak))
      .catch(() => setKeycloak(false));
  }, []);

  useEffect(() => {
    if (keycloak === null) return;

    if (keycloak) {
      turKeycloakAdminService
        .getGroup(groupId)
        .then(setKeycloakGroup)
        .catch(() => setError(t("admin.groups.loadOneFailed")));
      setIsNew(false);
      return;
    }

    if (groupId === "new") {
      setGroup({ id: "", name: "", description: "" } as TurGroup);
      setIsNew(true);
    } else {
      turGroupService
        .get(groupId)
        .then(setGroup)
        .catch(() => setError(t("admin.groups.loadOneFailed")));
      setIsNew(false);
    }
  }, [groupId, keycloak, t]);

  function onDelete() {
    turGroupService
      .delete(group!.id)
      .then((success) => {
        if (success) {
          toast.success(t("admin.groups.deleted", { name: group!.name }));
          navigate(ROUTES.BENTO_ADMIN_GROUPS);
        } else {
          toast.error(t("admin.groups.deleteFailed", { name: group!.name }));
        }
      })
      .catch((err) => {
        console.error("Delete error", err);
        toast.error(t("admin.groups.deleteFailed", { name: group!.name }));
      })
      .finally(() => setOpen(false));
  }

  const leading = (
    <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-slate-600 to-slate-700 text-white shadow-md">
      <IconUsersGroup size={24} />
    </span>
  );

  // Read-only Keycloak view has no form/save bar, so it renders a plain BentoHero.
  const hero = (title: string, subtitle: string) => (
    <BentoHero
      backTo={ROUTES.BENTO_ADMIN_GROUPS}
      backLabel={t("admin.groups.title")}
      leading={leading}
      title={title}
      subtitle={subtitle}
    />
  );

  if (keycloak) {
    return (
      <SectionCardChromeProvider chrome="bento">
        <LoadProvider checkIsNotUndefined={keycloakGroup} error={error} tryAgainUrl={`${ROUTES.BENTO_ADMIN_GROUPS}/${groupId}`}>
          {keycloakGroup && (
            <AdminKeycloakGroupView
              value={keycloakGroup}
              listRoute={ROUTES.BENTO_ADMIN_GROUPS}
              header={hero(keycloakGroup.name, t("admin.groups.keycloakViewDescription"))}
            />
          )}
        </LoadProvider>
      </SectionCardChromeProvider>
    );
  }

  return (
    <SectionCardChromeProvider chrome="bento">
      <LoadProvider checkIsNotUndefined={group} error={error} tryAgainUrl={`${ROUTES.BENTO_ADMIN_GROUPS}/${groupId}`}>
        {group && (
          <AdminGroupForm
            value={group}
            isNew={isNew}
            onDelete={isNew ? undefined : onDelete}
            open={open}
            setOpen={setOpen}
            listRoute={ROUTES.BENTO_ADMIN_GROUPS}
            bentoHero={{
              backTo: ROUTES.BENTO_ADMIN_GROUPS,
              backLabel: t("admin.groups.title"),
              leading,
              title: isNew ? t("admin.groups.newGroup") : (group.name || ""),
              subtitle: isNew ? t("admin.groups.createDescription") : t("admin.groups.editDescription"),
            }}
          />
        )}
      </LoadProvider>
    </SectionCardChromeProvider>
  );
}
