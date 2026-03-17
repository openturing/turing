import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurRole } from "@/models/auth/role";
import { TurRoleService } from "@/services/auth/role.service";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconUserShield } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";

const turRoleService = new TurRoleService();

export default function AdminRolesListPage() {
    const [roles, setRoles] = useState<TurRole[]>();
    const [error, setError] = useState<string | null>(null);
    const { pushItem, popItem } = useBreadcrumb();
    const breadcrumbAdded = useRef(false);

    useEffect(() => {
        if (!breadcrumbAdded.current) {
            pushItem({ label: "Roles" });
            breadcrumbAdded.current = true;
        }
        turRoleService.query()
            .then(setRoles)
            .catch(() => setError("Failed to load roles."));
    }, []);

    useEffect(() => {
        return () => {
            if (breadcrumbAdded.current) {
                popItem();
                breadcrumbAdded.current = false;
            }
        };
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
                feature="Roles"
                name="Roles"
                description="Define roles and their associated permissions."
                urlNew={`${ROUTES.ADMIN_ROOT}/roles/new`}
            />
            {roles && <GridList gridItemList={gridItemList} />}
        </LoadProvider>
    );
}
