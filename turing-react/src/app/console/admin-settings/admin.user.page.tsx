import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { AdminUserForm } from "@/components/admin/admin.user.form";
import { AdminKeycloakUserView } from "@/components/admin/admin.keycloak.user.view";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurUser } from "@/models/auth/user";
import type { TurKeycloakUser } from "@/models/auth/keycloak-user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { TurKeycloakAdminService } from "@/services/auth/keycloak-admin.service";
import { TurAuthorizationService } from "@/services/auth/authorization.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turAdminUserService = new TurAdminUserService();
const turKeycloakAdminService = new TurKeycloakAdminService();
const turAuthorizationService = new TurAuthorizationService();

export default function AdminUserPage() {
    const { t } = useTranslation();
    const { username } = useParams() as { username: string };
    const [user, setUser] = useState<TurUser>();
    const [keycloakUser, setKeycloakUser] = useState<TurKeycloakUser>();
    const [isNew, setIsNew] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [open, setOpen] = useState(false);
    const navigate = useNavigate();
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
    const [keycloak, setKeycloak] = useState<boolean | null>(null);

    useEffect(() => {
        turAuthorizationService.discovery()
            .then((d) => setKeycloak(!!d.keycloak))
            .catch(() => setKeycloak(false));
    }, []);

    useEffect(() => {
        if (keycloak === null) return;

        if (keycloak) {
            turKeycloakAdminService.getUser(username)
                .then((u) => {
                    setKeycloakUser(u);
                    setBreadcrumb([{ label: t("admin.users.title"), href: `${ROUTES.ADMIN_ROOT}/users` }, { label: u.username }]);
                })
                .catch(() => setError(t("admin.users.loadOneFailed")));
            setIsNew(false);
            return;
        }

        if (username === "new") {
            setUser({ username: "", firstName: "", lastName: "", email: "", admin: false } as TurUser);
            setIsNew(true);
            setBreadcrumb([{ label: t("admin.users.title"), href: `${ROUTES.ADMIN_ROOT}/users` }, { label: t("common.new") }]);
        } else {
            turAdminUserService.get(username).then((u) => {
                setUser(u);
                setBreadcrumb([{ label: t("admin.users.title"), href: `${ROUTES.ADMIN_ROOT}/users` }, { label: u.username }]);
            }).catch(() => setError(t("admin.users.loadOneFailed")));
            setIsNew(false);
        }
    }, [username, keycloak]);

    useSubPageBreadcrumb(breadcrumb);

    function onDelete() {
        turAdminUserService.delete(username).then((success) => {
            if (success) {
                toast.success(t("admin.users.deleted", { name: username }));
                navigate(ROUTES.ADMIN_USERS);
            } else {
                toast.error(t("admin.users.deleteFailed", { name: username }));
            }
        }).catch((error) => {
            console.error("Delete error", error);
            toast.error(t("admin.users.deleteFailed", { name: username }));
        }).finally(() => setOpen(false));
    }

    if (keycloak) {
        return (
            <LoadProvider checkIsNotUndefined={keycloakUser} error={error} tryAgainUrl={`${ROUTES.ADMIN_ROOT}/users/${username}`}>
                {keycloakUser && <AdminKeycloakUserView value={keycloakUser} />}
            </LoadProvider>
        );
    }

    return (
        <LoadProvider checkIsNotUndefined={user} error={error} tryAgainUrl={`${ROUTES.ADMIN_ROOT}/users/${username}`}>
            {user && <AdminUserForm value={user} isNew={isNew} onDelete={isNew ? undefined : onDelete} open={open} setOpen={setOpen} />}
        </LoadProvider>
    );
}
