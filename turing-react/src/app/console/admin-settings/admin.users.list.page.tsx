import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurUser } from "@/models/auth/user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconUsers } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";

const turAdminUserService = new TurAdminUserService();

export default function AdminUsersListPage() {
    const [users, setUsers] = useState<TurUser[]>();
    const [error, setError] = useState<string | null>(null);
    const { pushItem, popItem } = useBreadcrumb();
    const breadcrumbAdded = useRef(false);

    useEffect(() => {
        if (!breadcrumbAdded.current) {
            pushItem({ label: "Users" });
            breadcrumbAdded.current = true;
        }
        turAdminUserService.query()
            .then(setUsers)
            .catch(() => setError("Failed to load users."));
    }, []);

    useEffect(() => {
        return () => {
            if (breadcrumbAdded.current) {
                popItem();
                breadcrumbAdded.current = false;
            }
        };
    }, []);

    const gridItemList = useGridAdapter(users, {
        id: "username",
        name: "username",
        description: "email",
        url: (item) => `${ROUTES.ADMIN_ROOT}/users/${item.username}`,
    });

    return (
        <LoadProvider checkIsNotUndefined={users} error={error} tryAgainUrl={ROUTES.ADMIN_USERS}>
            <SubPageHeader
                icon={IconUsers}
                feature="Users"
                name="Users"
                description="Manage user accounts and access."
                urlNew={`${ROUTES.ADMIN_ROOT}/users/new`}
            />
            {users && <GridList gridItemList={gridItemList} />}
        </LoadProvider>
    );
}
