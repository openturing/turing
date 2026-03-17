import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { AdminUserForm } from "@/components/admin/admin.user.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurUser } from "@/models/auth/user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { IconUser } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
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
    const { pushItem, popItem } = useBreadcrumb();
    const breadcrumbAdded = useRef(false);

    useEffect(() => {
        if (username === "new") {
            setUser({ username: "", firstName: "", lastName: "", email: "", admin: false } as TurUser);
            setIsNew(true);
            if (!breadcrumbAdded.current) {
                pushItem({ label: "Users", href: `${ROUTES.ADMIN_ROOT}/users` });
                pushItem({ label: "New" });
                breadcrumbAdded.current = true;
            }
        } else {
            turAdminUserService.get(username).then((u) => {
                setUser(u);
                if (!breadcrumbAdded.current) {
                    pushItem({ label: "Users", href: `${ROUTES.ADMIN_ROOT}/users` });
                    pushItem({ label: u.username });
                    breadcrumbAdded.current = true;
                }
            }).catch(() => setError("Failed to load user."));
            setIsNew(false);
        }
    }, [username]);

    useEffect(() => {
        return () => {
            if (breadcrumbAdded.current) {
                popItem();
                popItem();
                breadcrumbAdded.current = false;
            }
        };
    }, []);

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
