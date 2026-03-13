import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurUser } from "@/models/auth/user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { IconUsers } from "@tabler/icons-react";
import { useEffect, useState } from "react";

const turAdminUserService = new TurAdminUserService();

export default function AdminUsersListPage() {
    const [users, setUsers] = useState<TurUser[]>();
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        turAdminUserService.query()
            .then(setUsers)
            .catch(() => setError("Failed to load users."));
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
