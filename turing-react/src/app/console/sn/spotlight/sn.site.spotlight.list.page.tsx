import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate.tsx";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurSNSiteSpotlight } from "@/models/sn/sn-site-spotlight.model";
import { TurSNSiteSpotlightService } from "@/services/sn/sn.site.spotlight.service";
import { IconSpeakerphone } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turSNSiteSpotlightService = new TurSNSiteSpotlightService();

export default function SNSiteSpotlightListPage() {
    const { id } = useParams() as { id: string };
    const [spotlightList, setSpotlightList] = useState<TurSNSiteSpotlight[]>();
    const [error, setError] = useState<string | null>(null);
    useEffect(() => {
        turSNSiteSpotlightService.query(id).then(setSpotlightList).catch(() => setError("Connection error or timeout while fetching spotlight."));
    }, [id])
    const gridItemList = useGridAdapter(spotlightList, {
        name: "name",
        description: "description",
        url: (item) => `${ROUTES.SN_INSTANCE}/${id}/spotlight/${item.id}`
    });
    return (
        <LoadProvider checkIsNotUndefined={spotlightList} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/spotlight`}>
            {spotlightList && spotlightList.length > 0 ? (
                <>
                    <SubPageHeader icon={IconSpeakerphone} name="Spotlight" feature="Spotlight"
                        description="Define content that will be featured in the term-based search." />
                    <GridList gridItemList={gridItemList} />
                </>
            ) : (
                <BlankSlate
                    icon={IconSpeakerphone}
                    title="You don’t seem to have any spotlight."
                    description="Create a spotlight component that highlights content triggered by specific search terms."
                    buttonText="New spotlight"
                    urlNew={`${ROUTES.SN_INSTANCE}/${id}/spotlight/new`} />
            )}
        </LoadProvider>
    )
}