import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { AdminGroupForm } from "@/components/admin/admin.group.form";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurGroup } from "@/models/auth/group";
import { TurGroupService } from "@/services/auth/group.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turGroupService = new TurGroupService();

export default function AdminGroupPage() {
    const { t } = useTranslation();
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
            setBreadcrumb([{ label: t("admin.groups.title"), href: `${ROUTES.ADMIN_ROOT}/groups` }, { label: t("common.new") }]);
        } else {
            turGroupService.get(groupId).then((g) => {
                setGroup(g);
                setBreadcrumb([{ label: t("admin.groups.title"), href: `${ROUTES.ADMIN_ROOT}/groups` }, { label: g.name }]);
            }).catch(() => setError(t("admin.groups.loadOneFailed")));
            setIsNew(false);
        }
    }, [groupId]);

    useSubPageBreadcrumb(breadcrumb);

    function onDelete() {
        turGroupService.delete(group!.id).then((success) => {
            if (success) {
                toast.success(t("admin.groups.deleted", { name: group!.name }));
                navigate(ROUTES.ADMIN_GROUPS);
            } else {
                toast.error(t("admin.groups.deleteFailed", { name: group!.name }));
            }
        }).catch((error) => {
            console.error("Delete error", error);
            toast.error(t("admin.groups.deleteFailed", { name: group!.name }));
        }).finally(() => setOpen(false));
    }

    return (
        <LoadProvider checkIsNotUndefined={group} error={error} tryAgainUrl={`${ROUTES.ADMIN_ROOT}/groups/${groupId}`}>
            {group && <AdminGroupForm value={group} isNew={isNew} onDelete={isNew ? undefined : onDelete} open={open} setOpen={setOpen} />}
        </LoadProvider>
    );
}
