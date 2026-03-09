import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate.tsx";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurSNRankingExpression } from "@/models/sn/sn-ranking-expression.model.ts";
import { TurSNRankingExpressionService } from "@/services/sn/sn.site.result.ranking.service";
import { IconNumber123 } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turSNRankingExpressionService = new TurSNRankingExpressionService();

export default function SNSiteResultRankingListPage() {
    const { id } = useParams() as { id: string };
    const [rankingList, setRankingList] = useState<TurSNRankingExpression[]>();
    const [error, setError] = useState<string | null>(null);
    useEffect(() => {
        turSNRankingExpressionService.query(id).then(setRankingList).catch(() => setError("Connection error or timeout while fetching result ranking."));
    }, [id])
    const gridItemList = useGridAdapter(rankingList, {
        name: "name",
        description: "description",
        url: (item) => `${ROUTES.SN_INSTANCE}/${id}/result-ranking/${item.id}`
    });
    return (
        <LoadProvider checkIsNotUndefined={rankingList} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/result-ranking`}>
            {rankingList && rankingList.length > 0 ? (
                <>
                    <SubPageHeader icon={IconNumber123}
                        name="Result Ranking"
                        feature="Result Ranking"
                        description="Define content that will be featured in the term-based search."
                        urlNew={`${ROUTES.SN_INSTANCE}/${id}/result-ranking/new`} />
                    <GridList gridItemList={gridItemList} />
                </>
            ) : (
                <BlankSlate
                    icon={IconNumber123}
                    title="You don’t seem to have any result ranking."
                    description="Create a new result ranking to define relevance rules."
                    buttonText="New result ranking"
                    urlNew={`${ROUTES.SN_INSTANCE}/${id}/result-ranking/new`} />

            )}
        </LoadProvider>
    )
}