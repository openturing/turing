import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurUser } from "@/models/auth/user";
import type { TurKeycloakUser } from "@/models/auth/keycloak-user";
import { TurAdminUserService } from "@/services/auth/admin-user.service";
import { TurKeycloakAdminService } from "@/services/auth/keycloak-admin.service";
import { TurAuthorizationService } from "@/services/auth/authorization.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconUsers } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const turAdminUserService = new TurAdminUserService();
const turKeycloakAdminService = new TurKeycloakAdminService();
const turAuthorizationService = new TurAuthorizationService();

type AdminUserItem = {
    username: string;
    email?: string;
    description?: string;
};

export default function AdminUsersListPage() {
    const { t } = useTranslation();
    const [users, setUsers] = useState<AdminUserItem[]>();
    const [error, setError] = useState<string | null>(null);
    const [keycloak, setKeycloak] = useState<boolean | null>(null);
    useSubPageBreadcrumb(t("admin.users.title"));

    useEffect(() => {
        turAuthorizationService.discovery()
            .then((d) => setKeycloak(!!d.keycloak))
            .catch(() => setKeycloak(false));
    }, []);

    useEffect(() => {
        if (keycloak === null) return;
        if (keycloak) {
            turKeycloakAdminService.listUsers()
                .then((kcUsers: TurKeycloakUser[]) => {
                    setUsers(kcUsers.map((u) => ({
                        username: u.username,
                        email: u.email,
                        description: [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email,
                    })));
                })
                .catch(() => setError(t("admin.users.loadFailed")));
        } else {
            turAdminUserService.query()
                .then((list: TurUser[]) => {
                    setUsers(list.map((u) => ({
                        username: u.username,
                        email: u.email,
                        description: u.email,
                    })));
                })
                .catch(() => setError(t("admin.users.loadFailed")));
        }
    }, [keycloak]);

    const gridItemList = useGridAdapter(users, {
        id: "username",
        name: "username",
        description: "description",
        url: (item) => `${ROUTES.ADMIN_ROOT}/users/${item.username}`,
    });

    return (
        <LoadProvider checkIsNotUndefined={users} error={error} tryAgainUrl={ROUTES.ADMIN_USERS}>
            <SubPageHeader
                icon={IconUsers}
                feature={t("admin.users.title")}
                name={t("admin.users.title")}
                description={keycloak ? t("admin.users.keycloakDescription") : t("admin.users.description")}
            />
            {users && <GridList gridItemList={gridItemList}>
                {!keycloak && <GridList.NewButton to={`${ROUTES.ADMIN_ROOT}/users/new`} label="User" />}
            </GridList>}
        </LoadProvider>
    );
}
