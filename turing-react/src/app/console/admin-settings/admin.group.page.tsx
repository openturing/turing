import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { AdminGroupForm } from "@/components/admin/admin.group.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
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
    const { pushItem, popItem } = useBreadcrumb();

    useEffect(() => {
        let added = false;
        if (groupId === "new") {
            setGroup({ id: "", name: "", description: "" } as TurGroup);
            setIsNew(true);
            pushItem({ label: "New Group" });
            added = true;
        } else {
            turGroupService.get(groupId).then((g) => {
                setGroup(g);
                pushItem({ label: g.name, href: `${ROUTES.ADMIN_ROOT}/groups/${g.id}` });
                added = true;
            }).catch(() => setError("Failed to load group."));
            setIsNew(false);
        }
        return () => { if (added) popItem(); };
    }, [groupId]);

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
