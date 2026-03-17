import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate.tsx";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurSNSiteMerge } from "@/models/sn/sn-site-merge.model";
import { TurSNSiteMergeService } from "@/services/sn/sn.site.merge.service";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconGitMerge } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";

const turSNSiteMergeService = new TurSNSiteMergeService();

export default function SNSiteMergeProvidersListPage() {
    const { id } = useParams() as { id: string };
    const [mergeProviderList, setMergeProviderList] = useState<TurSNSiteMerge[]>();
    const [error, setError] = useState<string | null>(null);
    const { pushItem, popItem } = useBreadcrumb();
    const breadcrumbAdded = useRef(false);

    useEffect(() => {
        if (!breadcrumbAdded.current) {
            pushItem({ label: "Merge Providers" });
            breadcrumbAdded.current = true;
        }
        turSNSiteMergeService.query(id).then(setMergeProviderList).catch(() => setError("Connection error or timeout while fetching merge providers."));
    }, [id]);

    useEffect(() => {
        return () => {
            if (breadcrumbAdded.current) {
                popItem();
                breadcrumbAdded.current = false;
            }
        };
    }, []);
    const gridItemList = useGridAdapter(mergeProviderList, {
        name: (item) => `${item.providerFrom} → ${item.providerTo}`,
        description: "description",
        url: (item) => `${ROUTES.SN_INSTANCE}/${id}/merge-providers/${item.id}`
    });
    return (
        <LoadProvider checkIsNotUndefined={mergeProviderList} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/merge-providers`}>
            {mergeProviderList && mergeProviderList.length > 0 ? (
                <>
                    <SubPageHeader icon={IconGitMerge} name="Merge Providers" feature="Merge Providers"
                        description="Unify different sources contents." />
                    <GridList gridItemList={gridItemList} />
                </>
            ) : (
                <BlankSlate
                    icon={IconGitMerge}
                    title="You don’t seem to have any merge provider."
                    description="Create a new merge provider and allow to merge documents from different providers during indexing."
                    buttonText="New merge provider"
                    urlNew={`${ROUTES.SN_INSTANCE}/${id}/merge-providers/new`} />

            )}
        </LoadProvider>
    )
}