import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurRole } from "@/models/auth/role";
import { TurRoleService } from "@/services/auth/role.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconUserShield } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const turRoleService = new TurRoleService();

export default function AdminRolesListPage() {
    const { t } = useTranslation();
    const [roles, setRoles] = useState<TurRole[]>();
    const [error, setError] = useState<string | null>(null);
    useSubPageBreadcrumb(t("admin.roles.title"));

    useEffect(() => {
        turRoleService.query()
            .then(setRoles)
            .catch(() => setError(t("admin.roles.loadFailed")));
    }, []);

    const gridItemList = useGridAdapter(roles, {
        name: "name",
        description: "description",
        url: (item) => `${ROUTES.ADMIN_ROOT}/roles/${item.id}`,
    });

    return (
        <LoadProvider checkIsNotUndefined={roles} error={error} tryAgainUrl={ROUTES.ADMIN_ROLES}>
            <SubPageHeader
                icon={IconUserShield}
                feature={t("admin.roles.title")}
                name={t("admin.roles.title")}
                description={t("admin.roles.description")}
            />
            {roles && <GridList gridItemList={gridItemList}>
                <GridList.NewButton to={`${ROUTES.ADMIN_ROOT}/roles/new`} label="Role" />
            </GridList>}
        </LoadProvider>
    );
}
