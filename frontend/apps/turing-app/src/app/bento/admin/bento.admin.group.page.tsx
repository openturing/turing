import { useAdminGroup } from "@/api/queries/admin.queries";
import { useAuthDiscovery } from "@/api/queries/auth.queries";
import { ROUTES } from "@/app/routes.const";
import { AdminGroupForm } from "@/components/admin/admin.group.form";
import { AdminKeycloakGroupView } from "@/components/admin/admin.keycloak.group.view";
import { BentoHero, bentoChipClass } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import type { TurGroup } from "@/models/auth/group";
import type { TurKeycloakGroup } from "@/models/auth/keycloak-group";
import { TurGroupService } from "@/services/auth/group.service";
import { IconUsersGroup } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turGroupService = new TurGroupService();

/**
 * Bento admin group detail — T567. Replicates the console detail loader
 * (Keycloak-vs-JPA discovery gate) but reuses the shared `AdminGroupForm` /
 * `AdminKeycloakGroupView` with the Bento list route so save/cancel/back/delete
 * stay inside the Bento shell.
 */
const NEW_GROUP = { id: "", name: "", description: "" } as TurGroup;

export default function BentoAdminGroupPage() {
  const { t } = useTranslation();
  const { groupId } = useParams() as { groupId: string };
  const [open, setOpen] = useState(false);
  // T1018 — discovery and the record both come from the cache. Discovery is one
  // query shared with the other admin pages rather than an effect per page, and
  // `keycloak` stays `null` until it answers, which is what gates the read.
  const { data: discovery } = useAuthDiscovery();
  const keycloak = discovery === undefined ? null : Boolean(discovery.keycloak);
  // T1017 — derived, not stored. `isNew` was state an effect wrote, and the two
  // things it wrote from are both already here: the route param, and whether the
  // backend is Keycloak. A Keycloak-backed record is never new, which is why the
  // discovery flag is part of the expression rather than a branch above it.
  // While discovery is pending `keycloak` is null, so this reads the param alone
  // — the same answer the effect's early return used to leave standing.
  const isNew = !keycloak && groupId === "new";
  const navigate = useNavigate();

  // The two backends return different shapes, so the record is read once and
  // narrowed here; the `new` route has no record to read and uses the seed.
  const { data: record, isError } = useAdminGroup(groupId, keycloak);
  const keycloakGroup = keycloak ? (record as TurKeycloakGroup | undefined) : undefined;
  const group = keycloak
    ? undefined
    : isNew
      ? NEW_GROUP
      : (record as TurGroup | undefined);
  const error = isError ? t("admin.groups.loadOneFailed") : null;

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
    <span className={`grid h-12 w-12 place-items-center rounded-2xl ${bentoChipClass('slate')} text-white shadow-md`}>
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
