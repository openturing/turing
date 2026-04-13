import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteMergeForm } from "@/components/sn/merge/sn.site.merge.form";
import type { TurSNSiteMerge } from "@/models/sn/sn-site-merge.model";
import { TurSNSiteMergeService } from "@/services/sn/sn.site.merge.service";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turSNSiteMergeService = new TurSNSiteMergeService();

export default function SNSiteMergeProvidersPage() {
    const navigate = useNavigate();
    const { id, mergeProviderId } = useParams() as { id: string; mergeProviderId: string };
    const [mergeProvider, setMergeProvider] = useState<TurSNSiteMerge>();
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
    const { t } = useTranslation();

    useEffect(() => {
        if (mergeProviderId === "new") {
            turSNSiteMergeService.getStructure(id).then(setMergeProvider).catch(() => setError("Connection error or timeout while fetching merge provider structure."));
            setBreadcrumb([{ label: t("sn.mergeProviders.title"), href: `${ROUTES.SN_INSTANCE}/${id}/merge-providers` }, { label: t("common.new") }]);
        } else {
            turSNSiteMergeService.get(id, mergeProviderId).then((merge) => {
                setMergeProvider(merge);
                const label = merge.providerFrom && merge.providerTo
                    ? `${merge.providerFrom} → ${merge.providerTo}`
                    : mergeProviderId;
                setBreadcrumb([{ label: t("sn.mergeProviders.title"), href: `${ROUTES.SN_INSTANCE}/${id}/merge-providers` }, { label }]);
            }).catch(() => setError("Connection error or timeout while fetching merge provider details."));
            setIsNew(false);
        }
    }, [id, mergeProviderId]);

    useSubPageBreadcrumb(breadcrumb);

    async function onDelete() {
        if (!mergeProvider) return;
        try {
            if (await turSNSiteMergeService.delete(mergeProvider)) {
                toast.success(t("sn.mergeProviders.deleted"));
                navigate(`${ROUTES.SN_INSTANCE}/${id}/merge-providers`);
            } else {
                toast.error(t("sn.mergeProviders.notDeleted"));
            }
        } catch (error) {
            console.error("Delete error", error);
            toast.error(t("sn.mergeProviders.notDeleted"));
        }
        setOpen(false);
    }

    return (
        <LoadProvider checkIsNotUndefined={mergeProvider} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/merge-providers`}>
            {mergeProvider && <SNSiteMergeForm snSiteId={id} value={mergeProvider} isNew={isNew} onDelete={isNew ? undefined : onDelete} open={open} setOpen={setOpen} />}
        </LoadProvider>
    );
}
