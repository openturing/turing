import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurGroup } from "@/models/auth/group";
import { TurGroupService } from "@/services/auth/group.service";
import { IconUsersGroup } from "@tabler/icons-react";
import { useEffect, useState } from "react";

const turGroupService = new TurGroupService();

export default function AdminGroupsListPage() {
    const [groups, setGroups] = useState<TurGroup[]>();
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        turGroupService.query()
            .then(setGroups)
            .catch(() => setError("Failed to load groups."));
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
                feature="Groups"
                name="Groups"
                description="Organize users into groups with shared permissions."
                urlNew={`${ROUTES.ADMIN_ROOT}/groups/new`}
            />
            {groups && <GridList gridItemList={gridItemList} />}
        </LoadProvider>
    );
}
