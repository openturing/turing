import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurGroup } from "@/models/auth/group";
import type { TurKeycloakGroup } from "@/models/auth/keycloak-group";
import { TurGroupService } from "@/services/auth/group.service";
import { TurKeycloakAdminService } from "@/services/auth/keycloak-admin.service";
import { TurAuthorizationService } from "@/services/auth/authorization.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconUsersGroup } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const turGroupService = new TurGroupService();
const turKeycloakAdminService = new TurKeycloakAdminService();
const turAuthorizationService = new TurAuthorizationService();

type AdminGroupItem = {
    id: string;
    name: string;
    description?: string;
};

export default function AdminGroupsListPage() {
    const { t } = useTranslation();
    const [groups, setGroups] = useState<AdminGroupItem[]>();
    const [error, setError] = useState<string | null>(null);
    const [keycloak, setKeycloak] = useState<boolean | null>(null);
    useSubPageBreadcrumb(t("admin.groups.title"));

    useEffect(() => {
        turAuthorizationService.discovery()
            .then((d) => setKeycloak(!!d.keycloak))
            .catch(() => setKeycloak(false));
    }, []);

    useEffect(() => {
        if (keycloak === null) return;
        if (keycloak) {
            turKeycloakAdminService.listGroups()
                .then((kcGroups: TurKeycloakGroup[]) => {
                    setGroups(kcGroups.map((g) => ({
                        id: g.id,
                        name: g.name,
                        description: g.path,
                    })));
                })
                .catch(() => setError(t("admin.groups.loadFailed")));
        } else {
            turGroupService.query()
                .then((list: TurGroup[]) => {
                    setGroups(list.map((g) => ({
                        id: g.id,
                        name: g.name,
                        description: g.description,
                    })));
                })
                .catch(() => setError(t("admin.groups.loadFailed")));
        }
    }, [keycloak]);

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
