import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { AdminUserForm } from "@/components/admin/admin.user.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurUser } from "@/models/auth/user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { IconUser } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turAdminUserService = new TurAdminUserService();

export default function AdminUserPage() {
    const { t } = useTranslation();
    const { username } = useParams() as { username: string };
    const [user, setUser] = useState<TurUser>();
    const [isNew, setIsNew] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [open, setOpen] = useState(false);
    const navigate = useNavigate();
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();

    useEffect(() => {
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
    }, [username]);

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

    return (
        <LoadProvider checkIsNotUndefined={user} error={error} tryAgainUrl={`${ROUTES.ADMIN_ROOT}/users/${username}`}>
            <SubPageHeader
                icon={IconUser}
                feature="User"
                name={isNew ? t("admin.users.newUser") : (user?.username ?? "")}
                description={isNew ? t("admin.users.createDescription") : t("admin.users.editDescription")}
                {...(isNew ? {} : { onDelete, open, setOpen })}
            />
            {user && <AdminUserForm value={user} isNew={isNew} />}
        </LoadProvider>
    );
}
