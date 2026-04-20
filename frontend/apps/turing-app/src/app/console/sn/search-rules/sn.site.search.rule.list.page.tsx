import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate.tsx";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurSNSiteSearchRule } from "@/models/sn/sn-site-search-rule.model";
import { TurSNSiteSearchRuleService } from "@/services/sn/sn.site.search.rule.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconGavel } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turSNSiteSearchRuleService = new TurSNSiteSearchRuleService();

export default function SNSiteSearchRuleListPage() {
    const { id } = useParams() as { id: string };
    const [searchRuleList, setSearchRuleList] = useState<TurSNSiteSearchRule[]>();
    const [error, setError] = useState<string | null>(null);
    const { t } = useTranslation();
    useSubPageBreadcrumb(t("sn.searchRule.title"));

    useEffect(() => {
        turSNSiteSearchRuleService.query(id).then(setSearchRuleList).catch(() => setError("Connection error or timeout while fetching search rules."));
    }, [id]);

    const gridItemList = useGridAdapter(searchRuleList, {
        name: "name",
        description: "description",
        url: (item) => `${ROUTES.SN_INSTANCE}/${id}/search-rule/${item.id}`
    });

    return (
        <LoadProvider checkIsNotUndefined={searchRuleList} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/search-rule`}>
            <SubPageHeader icon={IconGavel} name={t("sn.searchRule.title")} feature={t("sn.searchRule.title")}
                description={t("sn.searchRule.description")}>
                <SubPageHeader.Action label={t("sn.searchRule.newSearchRule")} href={`${ROUTES.SN_INSTANCE}/${id}/search-rule/new`} />
            </SubPageHeader>
            {searchRuleList && searchRuleList.length > 0 ? (
                <GridList gridItemList={gridItemList} />
            ) : (
                <BlankSlate
                    icon={IconGavel}
                    title={t("sn.searchRule.blankTitle")}
                    description={t("sn.searchRule.blankDescription")}
                    buttonText={t("sn.searchRule.newSearchRuleBlank")}
                    urlNew={`${ROUTES.SN_INSTANCE}/${id}/search-rule/new`} />
            )}
        </LoadProvider>
    )
}
