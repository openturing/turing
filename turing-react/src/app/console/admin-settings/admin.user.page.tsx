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
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turAdminUserService = new TurAdminUserService();

export default function AdminUserPage() {
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
            setBreadcrumb([{ label: "Users", href: `${ROUTES.ADMIN_ROOT}/users` }, { label: "New" }]);
        } else {
            turAdminUserService.get(username).then((u) => {
                setUser(u);
                setBreadcrumb([{ label: "Users", href: `${ROUTES.ADMIN_ROOT}/users` }, { label: u.username }]);
            }).catch(() => setError("Failed to load user."));
            setIsNew(false);
        }
    }, [username]);

    useSubPageBreadcrumb(breadcrumb);

    function onDelete() {
        turAdminUserService.delete(username).then((success) => {
            if (success) {
                toast.success(`User "${username}" was deleted.`);
                navigate(ROUTES.ADMIN_USERS);
            } else {
                toast.error(`Failed to delete user "${username}".`);
            }
        }).catch((error) => {
            console.error("Delete error", error);
            toast.error(`Failed to delete user "${username}".`);
        }).finally(() => setOpen(false));
    }

    return (
        <LoadProvider checkIsNotUndefined={user} error={error} tryAgainUrl={`${ROUTES.ADMIN_ROOT}/users/${username}`}>
            <SubPageHeader
                icon={IconUser}
                feature="User"
                name={isNew ? "New User" : (user?.username ?? "")}
                description={isNew ? "Create a new user account." : "Edit user account details."}
                {...(isNew ? {} : { onDelete, open, setOpen })}
            />
            {user && <AdminUserForm value={user} isNew={isNew} />}
        </LoadProvider>
    );
}
