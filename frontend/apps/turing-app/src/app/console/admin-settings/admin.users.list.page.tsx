import { useAdminUsers } from "@/api/queries/admin.queries";
import { useAuthDiscovery } from "@/api/queries/auth.queries";
import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconUsers } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function AdminUsersListPage() {
    const { t } = useTranslation();
    const { data: discovery, isError: discoveryError } = useAuthDiscovery();
    // Discovery resolves to a boolean once it returns or fails; null while pending.
    // The legacy code treated a failed discovery as "no Keycloak" — preserved here.
    const keycloak: boolean | null = discovery !== undefined
        ? !!discovery.keycloak
        : (discoveryError ? false : null);
    const { data: users, isError: usersError } = useAdminUsers(keycloak);
    const error = usersError ? t("admin.users.loadFailed") : null;
    useSubPageBreadcrumb(t("admin.users.title"));

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
