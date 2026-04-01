import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteCustomSortForm } from "@/components/sn/custom-sort/sn.site.custom.sort.form";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurSNSiteCustomSort } from "@/models/sn/sn-site-custom-sort.model";
import { TurSNSiteCustomSortService } from "@/services/sn/sn.site.custom.sort.service";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconArrowsSort } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turSNSiteCustomSortService = new TurSNSiteCustomSortService();

export default function SNSiteCustomSortPage() {
    const navigate = useNavigate();
    const { id, customSortId } = useParams() as { id: string; customSortId: string };
    const [customSort, setCustomSort] = useState<TurSNSiteCustomSort>();
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
    const { t } = useTranslation();

    useEffect(() => {
        if (customSortId === "new") {
            setCustomSort({ name: "", items: [] });
            setBreadcrumb([
                { label: t("sn.customSort.title"), href: `${ROUTES.SN_INSTANCE}/${id}/custom-sort` },
                { label: t("common.new") }
            ]);
        } else {
            turSNSiteCustomSortService.get(id, customSortId).then((s) => {
                setCustomSort(s);
                setBreadcrumb([
                    { label: t("sn.customSort.title"), href: `${ROUTES.SN_INSTANCE}/${id}/custom-sort` },
                    { label: s.name || customSortId }
                ]);
            }).catch(() => setError("Connection error or timeout while fetching custom sort details."));
            setIsNew(false);
        }
    }, [id, customSortId]);

    useSubPageBreadcrumb(breadcrumb);

    async function onDelete() {
        if (!customSort?.id) return;
        try {
            if (await turSNSiteCustomSortService.delete(id, customSort.id)) {
                toast.success(t("sn.customSort.deleted"));
                navigate(`${ROUTES.SN_INSTANCE}/${id}/custom-sort`);
            } else {
                toast.error(t("sn.customSort.notDeleted"));
            }
        } catch (error) {
            console.error("Delete error", error);
            toast.error(t("sn.customSort.notDeleted"));
        }
        setOpen(false);
    }

    const name = customSort?.name || t("sn.customSort.newCustomSort");

    return (
        <LoadProvider checkIsNotUndefined={customSort} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/custom-sort`}>
            <SubPageHeader
                icon={IconArrowsSort}
                name={name}
                feature={t("sn.customSort.title")}
                description={t("sn.customSort.description")}
                onDelete={isNew ? undefined : onDelete}
                open={open}
                setOpen={setOpen}
            />
            {customSort && <SNSiteCustomSortForm snSiteId={id} value={customSort} isNew={isNew} />}
        </LoadProvider>
    );
}
