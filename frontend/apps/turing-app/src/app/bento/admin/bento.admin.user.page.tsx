import { useAdminUser } from "@/api/queries/admin.queries";
import { useAuthDiscovery } from "@/api/queries/auth.queries";
import { ROUTES } from "@/app/routes.const";
import { AdminKeycloakUserView } from "@/components/admin/admin.keycloak.user.view";
import { AdminUserForm } from "@/components/admin/admin.user.form";
import { BentoHero, bentoChipClass } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import type { TurKeycloakUser } from "@/models/auth/keycloak-user";
import type { TurUser } from "@/models/auth/user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { IconUser } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turAdminUserService = new TurAdminUserService();

/**
 * Bento admin user detail — T567. Replicates the console detail loader
 * (Keycloak-vs-JPA discovery gate) but reuses the shared `AdminUserForm` /
 * `AdminKeycloakUserView` with the Bento list route so save/cancel/back/delete
 * stay inside the Bento shell.
 */
const NEW_USER = { username: "", firstName: "", lastName: "", email: "", admin: false } as TurUser;

export default function BentoAdminUserPage() {
  const { t } = useTranslation();
  const { username } = useParams() as { username: string };
  const [open, setOpen] = useState(false);
  // T1018 — discovery and the record both come from the cache now. Discovery was
  // its own effect in every admin page; it is one query with a ten-minute
  // staleTime, so the three of them ask once between them. `keycloak` stays
  // `null` until it answers, which is what gates the record read below.
  const { data: discovery } = useAuthDiscovery();
  const keycloak = discovery === undefined ? null : Boolean(discovery.keycloak);
  // T1017 — derived, not stored. `isNew` was state an effect wrote, and the two
  // things it wrote from are both already here: the route param, and whether the
  // backend is Keycloak. A Keycloak-backed record is never new, which is why the
  // discovery flag is part of the expression rather than a branch above it.
  // While discovery is pending `keycloak` is null, so this reads the param alone
  // — the same answer the effect's early return used to leave standing.
  const isNew = !keycloak && username === "new";
  const navigate = useNavigate();

  // The two backends return different shapes, so the record is read once and
  // narrowed here; the `new` route has no record to read and uses the seed.
  const { data: record, isError } = useAdminUser(username, keycloak);
  const keycloakUser = keycloak ? (record as TurKeycloakUser | undefined) : undefined;
  const user = keycloak
    ? undefined
    : isNew
      ? NEW_USER
      : (record as TurUser | undefined);
  const error = isError ? t("admin.users.loadOneFailed") : null;

  function onDelete() {
    turAdminUserService
      .delete(username)
      .then((success) => {
        if (success) {
          toast.success(t("admin.users.deleted", { name: username }));
          navigate(ROUTES.BENTO_ADMIN_USERS);
        } else {
          toast.error(t("admin.users.deleteFailed", { name: username }));
        }
      })
      .catch((err) => {
        console.error("Delete error", err);
        toast.error(t("admin.users.deleteFailed", { name: username }));
      })
      .finally(() => setOpen(false));
  }

  const leading = (
    <span className={`grid h-12 w-12 place-items-center rounded-2xl ${bentoChipClass('slate')} text-white shadow-md`}>
      <IconUser size={24} />
    </span>
  );

  // Read-only Keycloak view has no form/save bar, so it renders a plain BentoHero.
  const hero = (title: string, subtitle: string) => (
    <BentoHero
      backTo={ROUTES.BENTO_ADMIN_USERS}
      backLabel={t("admin.users.title")}
      leading={leading}
      title={title}
      subtitle={subtitle}
    />
  );

  if (keycloak) {
    return (
      <SectionCardChromeProvider chrome="bento">
        <LoadProvider checkIsNotUndefined={keycloakUser} error={error} tryAgainUrl={`${ROUTES.BENTO_ADMIN_USERS}/${username}`}>
          {keycloakUser && (
            <AdminKeycloakUserView
              value={keycloakUser}
              listRoute={ROUTES.BENTO_ADMIN_USERS}
              header={hero(keycloakUser.username, t("admin.users.keycloakViewDescription"))}
            />
          )}
        </LoadProvider>
      </SectionCardChromeProvider>
    );
  }

  return (
    <SectionCardChromeProvider chrome="bento">
      <LoadProvider checkIsNotUndefined={user} error={error} tryAgainUrl={`${ROUTES.BENTO_ADMIN_USERS}/${username}`}>
        {user && (
          <AdminUserForm
            value={user}
            isNew={isNew}
            onDelete={isNew ? undefined : onDelete}
            open={open}
            setOpen={setOpen}
            listRoute={ROUTES.BENTO_ADMIN_USERS}
            bentoHero={{
              backTo: ROUTES.BENTO_ADMIN_USERS,
              backLabel: t("admin.users.title"),
              leading,
              title: isNew ? t("admin.users.newUser") : (user.username || username),
              subtitle: isNew ? t("admin.users.createDescription") : t("admin.users.editDescription"),
            }}
          />
        )}
      </LoadProvider>
    </SectionCardChromeProvider>
  );
}
