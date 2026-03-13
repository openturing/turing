import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { AdminUserForm } from "@/components/admin/admin.user.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurUser } from "@/models/auth/user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { IconUser } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turAdminUserService = new TurAdminUserService();

export default function AdminUserPage() {
    const { username } = useParams() as { username: string };
    const [user, setUser] = useState<TurUser>();
    const [isNew, setIsNew] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const { pushItem, popItem } = useBreadcrumb();

    useEffect(() => {
        let added = false;
        if (username === "new") {
            setUser({ username: "", firstName: "", lastName: "", email: "", admin: false } as TurUser);
            setIsNew(true);
            pushItem({ label: "New User" });
            added = true;
        } else {
            turAdminUserService.get(username).then((u) => {
                setUser(u);
                pushItem({ label: u.username, href: `${ROUTES.ADMIN_ROOT}/users/${u.username}` });
                added = true;
            }).catch(() => setError("Failed to load user."));
            setIsNew(false);
        }
        return () => { if (added) popItem(); };
    }, [username]);

    return (
        <LoadProvider checkIsNotUndefined={user} error={error} tryAgainUrl={`${ROUTES.ADMIN_ROOT}/users/${username}`}>
            <SubPageHeader
                icon={IconUser}
                feature={isNew ? "New User" : "User"}
                name={isNew ? "New User" : (user?.username ?? "")}
                description={isNew ? "Create a new user account." : "Edit user account details."}
            />
            {user && <AdminUserForm value={user} isNew={isNew} />}
        </LoadProvider>
    );
}
