import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurGroup } from "@/models/auth/group";
import { TurGroupService } from "@/services/auth/group.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconUsersGroup } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const turGroupService = new TurGroupService();

export default function AdminGroupsListPage() {
    const { t } = useTranslation();
    const [groups, setGroups] = useState<TurGroup[]>();
    const [error, setError] = useState<string | null>(null);
    useSubPageBreadcrumb(t("admin.groups.title"));

    useEffect(() => {
        turGroupService.query()
            .then(setGroups)
            .catch(() => setError(t("admin.groups.loadFailed")));
    }, []);

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
                description={t("admin.groups.description")}
            />
            {groups && <GridList gridItemList={gridItemList}>
                <GridList.NewButton to={`${ROUTES.ADMIN_ROOT}/groups/new`} label="Group" />
            </GridList>}
        </LoadProvider>
    );
}
