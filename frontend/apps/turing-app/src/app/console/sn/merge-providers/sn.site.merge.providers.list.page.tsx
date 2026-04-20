import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate.tsx";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurSNSiteMerge } from "@/models/sn/sn-site-merge.model";
import { TurSNSiteMergeService } from "@/services/sn/sn.site.merge.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconGitMerge } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turSNSiteMergeService = new TurSNSiteMergeService();

export default function SNSiteMergeProvidersListPage() {
    const { id } = useParams() as { id: string };
    const [mergeProviderList, setMergeProviderList] = useState<TurSNSiteMerge[]>();
    const [error, setError] = useState<string | null>(null);
    const { t } = useTranslation();
    useSubPageBreadcrumb(t("sn.mergeProviders.title"));

    useEffect(() => {
        turSNSiteMergeService.query(id).then(setMergeProviderList).catch(() => setError("Connection error or timeout while fetching merge providers."));
    }, [id]);
    const gridItemList = useGridAdapter(mergeProviderList, {
        name: (item) => `${item.providerFrom} → ${item.providerTo}`,
        description: "description",
        url: (item) => `${ROUTES.SN_INSTANCE}/${id}/merge-providers/${item.id}`
    });
    return (
        <LoadProvider checkIsNotUndefined={mergeProviderList} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/merge-providers`}>
            <SubPageHeader icon={IconGitMerge} name={t("sn.mergeProviders.title")} feature={t("sn.mergeProviders.feature")}
                description={t("sn.mergeProviders.description")}>
                <SubPageHeader.Action label={t("sn.mergeProviders.newMergeProvider")} href={`${ROUTES.SN_INSTANCE}/${id}/merge-providers/new`} />
            </SubPageHeader>
            {mergeProviderList && mergeProviderList.length > 0 ? (
                <GridList gridItemList={gridItemList} />
            ) : (
                <BlankSlate
                    icon={IconGitMerge}
                    title={t("sn.mergeProviders.blankTitle")}
                    description={t("sn.mergeProviders.blankDescription")}
                    buttonText={t("sn.mergeProviders.newMergeProviderBlank")}
                    urlNew={`${ROUTES.SN_INSTANCE}/${id}/merge-providers/new`} />

            )}
        </LoadProvider>
    )
}