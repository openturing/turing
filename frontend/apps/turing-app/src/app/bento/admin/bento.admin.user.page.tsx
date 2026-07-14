import { ROUTES } from "@/app/routes.const";
import { AdminKeycloakUserView } from "@/components/admin/admin.keycloak.user.view";
import { AdminUserForm } from "@/components/admin/admin.user.form";
import { BentoHero } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import type { TurKeycloakUser } from "@/models/auth/keycloak-user";
import type { TurUser } from "@/models/auth/user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { TurAuthorizationService } from "@/services/auth/authorization.service";
import { TurKeycloakAdminService } from "@/services/auth/keycloak-admin.service";
import { IconUser } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turAdminUserService = new TurAdminUserService();
const turKeycloakAdminService = new TurKeycloakAdminService();
const turAuthorizationService = new TurAuthorizationService();

/**
 * Bento admin user detail — T567. Replicates the console detail loader
 * (Keycloak-vs-JPA discovery gate) but reuses the shared `AdminUserForm` /
 * `AdminKeycloakUserView` with the Bento list route so save/cancel/back/delete
 * stay inside the Bento shell.
 */
export default function BentoAdminUserPage() {
  const { t } = useTranslation();
  const { username } = useParams() as { username: string };
  const [user, setUser] = useState<TurUser>();
  const [keycloakUser, setKeycloakUser] = useState<TurKeycloakUser>();
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
        .getUser(username)
        .then(setKeycloakUser)
        .catch(() => setError(t("admin.users.loadOneFailed")));
      setIsNew(false);
      return;
    }

    if (username === "new") {
      setUser({ username: "", firstName: "", lastName: "", email: "", admin: false } as TurUser);
      setIsNew(true);
    } else {
      turAdminUserService
        .get(username)
        .then(setUser)
        .catch(() => setError(t("admin.users.loadOneFailed")));
      setIsNew(false);
    }
  }, [username, keycloak, t]);

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
    <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-slate-600 to-slate-700 text-white shadow-md">
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
