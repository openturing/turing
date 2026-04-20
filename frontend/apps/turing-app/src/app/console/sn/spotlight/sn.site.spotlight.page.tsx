import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteSpotlightForm } from "@/components/sn/spotlight/sn.site.spotlight.form";
import type { TurSNSiteSpotlight } from "@/models/sn/sn-site-spotlight.model";
import { TurSNSiteSpotlightService } from "@/services/sn/sn.site.spotlight.service";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turSNSiteSpotlightService = new TurSNSiteSpotlightService();

export default function SNSiteSpotlightPage() {
    const navigate = useNavigate();
    const { id, spotlightId } = useParams() as { id: string; spotlightId: string };
    const [spotlight, setSpotlight] = useState<TurSNSiteSpotlight>();
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
    const { t } = useTranslation();

    useEffect(() => {
        if (spotlightId === "new") {
            turSNSiteSpotlightService.getStructure(id).then(setSpotlight).catch(() => setError("Connection error or timeout while fetching spotlight structure."));
            setBreadcrumb([{ label: t("sn.spotlight.title"), href: `${ROUTES.SN_INSTANCE}/${id}/spotlight` }, { label: t("common.new") }]);
        } else {
            turSNSiteSpotlightService.get(id, spotlightId).then((s) => {
                setSpotlight(s);
                setBreadcrumb([{ label: t("sn.spotlight.title"), href: `${ROUTES.SN_INSTANCE}/${id}/spotlight` }, { label: s.name || spotlightId }]);
            }).catch(() => setError("Connection error or timeout while fetching spotlight details."));
            setIsNew(false);
        }
    }, [id, spotlightId]);

    useSubPageBreadcrumb(breadcrumb);

    async function onDelete() {
        if (!spotlight) return;
        try {
            if (await turSNSiteSpotlightService.delete(spotlight)) {
                toast.success(t("sn.spotlight.deleted"));
                navigate(`${ROUTES.SN_INSTANCE}/${id}/spotlight`);
            } else {
                toast.error(t("sn.spotlight.notDeleted"));
            }
        } catch (error) {
            console.error("Delete error", error);
            toast.error(t("sn.spotlight.notDeleted"));
        }
        setOpen(false);
    }

    return (
        <LoadProvider checkIsNotUndefined={spotlight} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/spotlight`}>
            {spotlight && <SNSiteSpotlightForm snSiteId={id} value={spotlight} isNew={isNew} onDelete={isNew ? undefined : onDelete} open={open} setOpen={setOpen} />}
        </LoadProvider>
    );
}
