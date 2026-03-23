import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { AdminRoleForm } from "@/components/admin/admin.role.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurRole } from "@/models/auth/role";
import { TurRoleService } from "@/services/auth/role.service";
import { IconUserShield } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turRoleService = new TurRoleService();

export default function AdminRolePage() {
    const { t } = useTranslation();
    const { roleId } = useParams() as { roleId: string };
    const [role, setRole] = useState<TurRole>();
    const [isNew, setIsNew] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();

    useEffect(() => {
        if (roleId === "new") {
            setRole({ id: "", name: "", description: "" } as TurRole);
            setIsNew(true);
            setBreadcrumb([{ label: t("admin.roles.title"), href: `${ROUTES.ADMIN_ROOT}/roles` }, { label: t("common.new") }]);
        } else {
            turRoleService.get(roleId).then((r) => {
                setRole(r);
                setBreadcrumb([{ label: t("admin.roles.title"), href: `${ROUTES.ADMIN_ROOT}/roles` }, { label: r.name }]);
            }).catch(() => setError(t("admin.roles.loadOneFailed")));
            setIsNew(false);
        }
    }, [roleId]);

    useSubPageBreadcrumb(breadcrumb);

    return (
        <LoadProvider checkIsNotUndefined={role} error={error} tryAgainUrl={`${ROUTES.ADMIN_ROOT}/roles/${roleId}`}>
            <SubPageHeader
                icon={IconUserShield}
                feature={isNew ? t("admin.roles.newRole") : "Role"}
                name={isNew ? t("admin.roles.newRole") : (role?.name ?? "")}
                description={isNew ? t("admin.roles.createDescription") : t("admin.roles.editDescription")}
            />
            {role && <AdminRoleForm value={role} isNew={isNew} />}
        </LoadProvider>
    );
}
