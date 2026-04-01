import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate.tsx";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurSNSiteCustomSort } from "@/models/sn/sn-site-custom-sort.model";
import { TurSNSiteCustomSortService } from "@/services/sn/sn.site.custom.sort.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconArrowsSort } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turSNSiteCustomSortService = new TurSNSiteCustomSortService();

export default function SNSiteCustomSortListPage() {
    const { id } = useParams() as { id: string };
    const [customSortList, setCustomSortList] = useState<TurSNSiteCustomSort[]>();
    const [error, setError] = useState<string | null>(null);
    const { t } = useTranslation();
    useSubPageBreadcrumb(t("sn.customSort.title"));

    useEffect(() => {
        turSNSiteCustomSortService.query(id).then(setCustomSortList).catch(() => setError("Connection error or timeout while fetching custom sorts."));
    }, [id]);

    const gridItemList = useGridAdapter(customSortList, {
        name: "name",
        description: "description",
        url: (item) => `${ROUTES.SN_INSTANCE}/${id}/custom-sort/${item.id}`
    });

    return (
        <LoadProvider checkIsNotUndefined={customSortList} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/custom-sort`}>
            <SubPageHeader icon={IconArrowsSort} name={t("sn.customSort.title")} feature={t("sn.customSort.title")}
                description={t("sn.customSort.description")}>
                <SubPageHeader.Action label={t("sn.customSort.newCustomSort")} href={`${ROUTES.SN_INSTANCE}/${id}/custom-sort/new`} />
            </SubPageHeader>
            {customSortList && customSortList.length > 0 ? (
                <GridList gridItemList={gridItemList} />
            ) : (
                <BlankSlate
                    icon={IconArrowsSort}
                    title={t("sn.customSort.blankTitle")}
                    description={t("sn.customSort.blankDescription")}
                    buttonText={t("sn.customSort.newCustomSortBlank")}
                    urlNew={`${ROUTES.SN_INSTANCE}/${id}/custom-sort/new`} />
            )}
        </LoadProvider>
    )
}
