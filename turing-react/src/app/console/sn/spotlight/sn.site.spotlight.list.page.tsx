import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate.tsx";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurSNSiteSpotlight } from "@/models/sn/sn-site-spotlight.model";
import { TurSNSiteSpotlightService } from "@/services/sn/sn.site.spotlight.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconSpeakerphone } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turSNSiteSpotlightService = new TurSNSiteSpotlightService();

export default function SNSiteSpotlightListPage() {
    const { id } = useParams() as { id: string };
    const [spotlightList, setSpotlightList] = useState<TurSNSiteSpotlight[]>();
    const [error, setError] = useState<string | null>(null);
    const { t } = useTranslation();
    useSubPageBreadcrumb(t("sn.spotlight.title"));

    useEffect(() => {
        turSNSiteSpotlightService.query(id).then(setSpotlightList).catch(() => setError("Connection error or timeout while fetching spotlight."));
    }, [id]);
    const gridItemList = useGridAdapter(spotlightList, {
        name: "name",
        description: "description",
        url: (item) => `${ROUTES.SN_INSTANCE}/${id}/spotlight/${item.id}`
    });
    return (
        <LoadProvider checkIsNotUndefined={spotlightList} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/spotlight`}>
            <SubPageHeader icon={IconSpeakerphone} name={t("sn.spotlight.title")} feature={t("sn.spotlight.title")}
                description={t("sn.spotlight.description")}>
                <SubPageHeader.Action label={t("sn.spotlight.newSpotlight")} href={`${ROUTES.SN_INSTANCE}/${id}/spotlight/new`} />
            </SubPageHeader>
            {spotlightList && spotlightList.length > 0 ? (
                <GridList gridItemList={gridItemList} />
            ) : (
                <BlankSlate
                    icon={IconSpeakerphone}
                    title={t("sn.spotlight.blankTitle")}
                    description={t("sn.spotlight.blankDescription")}
                    buttonText={t("sn.spotlight.newSpotlightBlank")}
                    urlNew={`${ROUTES.SN_INSTANCE}/${id}/spotlight/new`} />
            )}
        </LoadProvider>
    )
}