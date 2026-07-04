import { ROUTES } from "@/app/routes.const";
import { AdminKeycloakUserView } from "@/components/admin/admin.keycloak.user.view";
import { AdminUserForm } from "@/components/admin/admin.user.form";
import { LoadProvider } from "@/components/loading-provider";
import type { TurKeycloakUser } from "@/models/auth/keycloak-user";
import type { TurUser } from "@/models/auth/user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { TurAuthorizationService } from "@/services/auth/authorization.service";
import { TurKeycloakAdminService } from "@/services/auth/keycloak-admin.service";
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

  if (keycloak) {
    return (
      <LoadProvider checkIsNotUndefined={keycloakUser} error={error} tryAgainUrl={`${ROUTES.BENTO_ADMIN_USERS}/${username}`}>
        {keycloakUser && <AdminKeycloakUserView value={keycloakUser} listRoute={ROUTES.BENTO_ADMIN_USERS} />}
      </LoadProvider>
    );
  }

  return (
    <LoadProvider checkIsNotUndefined={user} error={error} tryAgainUrl={`${ROUTES.BENTO_ADMIN_USERS}/${username}`}>
      {user && (
        <AdminUserForm
          value={user}
          isNew={isNew}
          onDelete={isNew ? undefined : onDelete}
          open={open}
          setOpen={setOpen}
          listRoute={ROUTES.BENTO_ADMIN_USERS}
        />
      )}
    </LoadProvider>
  );
}
