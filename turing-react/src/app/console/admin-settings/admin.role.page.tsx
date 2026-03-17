import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { AdminRoleForm } from "@/components/admin/admin.role.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurRole } from "@/models/auth/role";
import { TurRoleService } from "@/services/auth/role.service";
import { IconUserShield } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";

const turRoleService = new TurRoleService();

export default function AdminRolePage() {
    const { roleId } = useParams() as { roleId: string };
    const [role, setRole] = useState<TurRole>();
    const [isNew, setIsNew] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const { pushItem, popItem } = useBreadcrumb();
    const breadcrumbAdded = useRef(false);

    useEffect(() => {
        if (roleId === "new") {
            setRole({ id: "", name: "", description: "" } as TurRole);
            setIsNew(true);
            if (!breadcrumbAdded.current) {
                pushItem({ label: "Roles", href: `${ROUTES.ADMIN_ROOT}/roles` });
                pushItem({ label: "New" });
                breadcrumbAdded.current = true;
            }
        } else {
            turRoleService.get(roleId).then((r) => {
                setRole(r);
                if (!breadcrumbAdded.current) {
                    pushItem({ label: "Roles", href: `${ROUTES.ADMIN_ROOT}/roles` });
                    pushItem({ label: r.name });
                    breadcrumbAdded.current = true;
                }
            }).catch(() => setError("Failed to load role."));
            setIsNew(false);
        }
    }, [roleId]);

    useEffect(() => {
        return () => {
            if (breadcrumbAdded.current) {
                popItem();
                popItem();
                breadcrumbAdded.current = false;
            }
        };
    }, []);

    return (
        <LoadProvider checkIsNotUndefined={role} error={error} tryAgainUrl={`${ROUTES.ADMIN_ROOT}/roles/${roleId}`}>
            <SubPageHeader
                icon={IconUserShield}
                feature={isNew ? "New Role" : "Role"}
                name={isNew ? "New Role" : (role?.name ?? "")}
                description={isNew ? "Create a new access role." : "Edit role details and permissions."}
            />
            {role && <AdminRoleForm value={role} isNew={isNew} />}
        </LoadProvider>
    );
}
