import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurUser } from "@/models/auth/user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconUsers } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const turAdminUserService = new TurAdminUserService();

export default function AdminUsersListPage() {
    const { t } = useTranslation();
    const [users, setUsers] = useState<TurUser[]>();
    const [error, setError] = useState<string | null>(null);
    useSubPageBreadcrumb(t("admin.users.title"));

    useEffect(() => {
        turAdminUserService.query()
            .then(setUsers)
            .catch(() => setError(t("admin.users.loadFailed")));
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
                feature={t("admin.users.title")}
                name={t("admin.users.title")}
                description={t("admin.users.description")}
            />
            {users && <GridList gridItemList={gridItemList}>
                <GridList.NewButton to={`${ROUTES.ADMIN_ROOT}/users/new`} label="User" />
            </GridList>}
        </LoadProvider>
    );
}
