import { useAdminGroups } from "@/api/queries/admin.queries";
import { useAuthDiscovery } from "@/api/queries/auth.queries";
import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconUsersGroup } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function AdminGroupsListPage() {
    const { t } = useTranslation();
    const { data: discovery, isError: discoveryError } = useAuthDiscovery();
    const keycloak: boolean | null = discovery !== undefined
        ? !!discovery.keycloak
        : (discoveryError ? false : null);
    const { data: groups, isError: groupsError } = useAdminGroups(keycloak);
    const error = groupsError ? t("admin.groups.loadFailed") : null;
    useSubPageBreadcrumb(t("admin.groups.title"));

    const gridItemList = useGridAdapter(groups, {
        name: "name",
        description: "description",
        url: (item) => `${ROUTES.ADMIN_ROOT}/groups/${item.id}`,
    });

    return (
        <LoadProvider checkIsNotUndefined={groups} error={error} tryAgainUrl={ROUTES.ADMIN_GROUPS}>
            <SubPageHeader
                icon={IconUsersGroup}
                feature={t("admin.groups.title")}
                name={t("admin.groups.title")}
                description={keycloak ? t("admin.groups.keycloakDescription") : t("admin.groups.description")}
            />
            {groups && <GridList gridItemList={gridItemList}>
                {!keycloak && <GridList.NewButton to={`${ROUTES.ADMIN_ROOT}/groups/new`} label="Group" />}
            </GridList>}
        </LoadProvider>
    );
}
