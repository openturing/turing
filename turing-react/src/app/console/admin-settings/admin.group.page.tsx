import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { AdminGroupForm } from "@/components/admin/admin.group.form";
import { AdminKeycloakGroupView } from "@/components/admin/admin.keycloak.group.view";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurGroup } from "@/models/auth/group";
import type { TurKeycloakGroup } from "@/models/auth/keycloak-group";
import { TurGroupService } from "@/services/auth/group.service";
import { TurKeycloakAdminService } from "@/services/auth/keycloak-admin.service";
import { TurAuthorizationService } from "@/services/auth/authorization.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turGroupService = new TurGroupService();
const turKeycloakAdminService = new TurKeycloakAdminService();
const turAuthorizationService = new TurAuthorizationService();

export default function AdminGroupPage() {
    const { t } = useTranslation();
    const { groupId } = useParams() as { groupId: string };
    const [group, setGroup] = useState<TurGroup>();
    const [keycloakGroup, setKeycloakGroup] = useState<TurKeycloakGroup>();
    const [isNew, setIsNew] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [open, setOpen] = useState(false);
    const navigate = useNavigate();
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
    const [keycloak, setKeycloak] = useState<boolean | null>(null);

    useEffect(() => {
        turAuthorizationService.discovery()
            .then((d) => setKeycloak(!!d.keycloak))
            .catch(() => setKeycloak(false));
    }, []);

    useEffect(() => {
        if (keycloak === null) return;

        if (keycloak) {
            turKeycloakAdminService.getGroup(groupId)
                .then((g) => {
                    setKeycloakGroup(g);
                    setBreadcrumb([{ label: t("admin.groups.title"), href: `${ROUTES.ADMIN_ROOT}/groups` }, { label: g.name }]);
                })
                .catch(() => setError(t("admin.groups.loadOneFailed")));
            setIsNew(false);
            return;
        }

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
    }, [groupId, keycloak]);

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

    if (keycloak) {
        return (
            <LoadProvider checkIsNotUndefined={keycloakGroup} error={error} tryAgainUrl={`${ROUTES.ADMIN_ROOT}/groups/${groupId}`}>
                {keycloakGroup && <AdminKeycloakGroupView value={keycloakGroup} />}
            </LoadProvider>
        );
    }

    return (
        <LoadProvider checkIsNotUndefined={group} error={error} tryAgainUrl={`${ROUTES.ADMIN_ROOT}/groups/${groupId}`}>
            {group && <AdminGroupForm value={group} isNew={isNew} onDelete={isNew ? undefined : onDelete} open={open} setOpen={setOpen} />}
        </LoadProvider>
    );
}
