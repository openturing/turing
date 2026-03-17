import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { AdminGroupForm } from "@/components/admin/admin.group.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurGroup } from "@/models/auth/group";
import { TurGroupService } from "@/services/auth/group.service";
import { IconUsersGroup } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turGroupService = new TurGroupService();

export default function AdminGroupPage() {
    const { groupId } = useParams() as { groupId: string };
    const [group, setGroup] = useState<TurGroup>();
    const [isNew, setIsNew] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [open, setOpen] = useState(false);
    const navigate = useNavigate();
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();

    useEffect(() => {
        if (groupId === "new") {
            setGroup({ id: "", name: "", description: "" } as TurGroup);
            setIsNew(true);
            setBreadcrumb([{ label: "Groups", href: `${ROUTES.ADMIN_ROOT}/groups` }, { label: "New" }]);
        } else {
            turGroupService.get(groupId).then((g) => {
                setGroup(g);
                setBreadcrumb([{ label: "Groups", href: `${ROUTES.ADMIN_ROOT}/groups` }, { label: g.name }]);
            }).catch(() => setError("Failed to load group."));
            setIsNew(false);
        }
    }, [groupId]);

    useSubPageBreadcrumb(breadcrumb);

    function onDelete() {
        turGroupService.delete(group!.id).then((success) => {
            if (success) {
                toast.success(`Group "${group!.name}" was deleted.`);
                navigate(ROUTES.ADMIN_GROUPS);
            } else {
                toast.error(`Failed to delete group "${group!.name}".`);
            }
        }).catch((error) => {
            console.error("Delete error", error);
            toast.error(`Failed to delete group "${group!.name}".`);
        }).finally(() => setOpen(false));
    }

    return (
        <LoadProvider checkIsNotUndefined={group} error={error} tryAgainUrl={`${ROUTES.ADMIN_ROOT}/groups/${groupId}`}>
            <SubPageHeader
                icon={IconUsersGroup}
                feature="Group"
                name={isNew ? "New Group" : (group?.name ?? "")}
                description={isNew ? "Create a new user group." : "Edit group details and members."}
                {...(isNew ? {} : { onDelete, open, setOpen })}
            />
            {group && <AdminGroupForm value={group} isNew={isNew} />}
        </LoadProvider>
    );
}
