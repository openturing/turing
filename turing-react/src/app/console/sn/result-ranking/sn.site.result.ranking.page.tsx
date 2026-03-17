import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteResultRankingForm } from "@/components/sn/result-ranking/sn.site.result.ranking.form";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurSNRankingExpression } from "@/models/sn/sn-ranking-expression.model";
import { TurSNRankingExpressionService } from "@/services/sn/sn.site.result.ranking.service";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconNumber123 } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turSNRankingExpressionService = new TurSNRankingExpressionService();

export default function SNSiteResultRankingPage() {
    const navigate = useNavigate();
    const { id, resultRankingId } = useParams() as { id: string, resultRankingId: string };
    const [resultRanking, setResultRanking] = useState<TurSNRankingExpression>();
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const { pushItem, popItem } = useBreadcrumb();
    const breadcrumbAdded = useRef(false);

    useEffect(() => {
        if (resultRankingId === "new") {
            turSNRankingExpressionService.query(id).then(() => setResultRanking({} as TurSNRankingExpression)).catch(() => setError("Connection error or timeout while fetching result rankings."));
            if (!breadcrumbAdded.current) {
                pushItem({ label: "Result Ranking", href: `${ROUTES.SN_INSTANCE}/${id}/result-ranking` });
                pushItem({ label: "New" });
                breadcrumbAdded.current = true;
            }
        } else {
            turSNRankingExpressionService.get(id, resultRankingId).then((ranking) => {
                setResultRanking(ranking);
                if (!breadcrumbAdded.current) {
                    pushItem({ label: "Result Ranking", href: `${ROUTES.SN_INSTANCE}/${id}/result-ranking` });
                    pushItem({ label: ranking.name || resultRankingId });
                    breadcrumbAdded.current = true;
                }
            }).catch(() => setError("Connection error or timeout while fetching result ranking details."));
            setIsNew(false);
        }
    }, [resultRankingId]);

    useEffect(() => {
        return () => {
            if (breadcrumbAdded.current) {
                popItem();
                popItem();
                breadcrumbAdded.current = false;
            }
        };
    }, []);
    async function onDelete() {
        if (!resultRanking) return;
        try {
            if (await turSNRankingExpressionService.delete(id, resultRanking)) {
                toast.success(`The ${resultRanking.name} Result Ranking Instance was deleted`);
                navigate(`${ROUTES.SN_INSTANCE}/${id}/result-ranking`);
            } else {
                toast.error(`The ${resultRanking.name} Result Ranking Instance was not deleted`);
            }

        } catch (error) {
            console.error("Form submission error", error);
            toast.error(`The ${resultRanking.name} Result Ranking Instance was not deleted`);
        }
        setOpen(false);
    }
    return (
        <LoadProvider checkIsNotUndefined={resultRanking} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/result-ranking`}>
            {resultRanking && (
                <>
                    <SubPageHeader icon={IconNumber123} name={resultRanking.name} feature="Result Ranking"
                        description="Define content that will be featured in the term-based search."
                        onDelete={onDelete}
                        open={open}
                        setOpen={setOpen} />
                    <SNSiteResultRankingForm snSiteId={id} value={resultRanking} isNew={isNew} />
                </>
            )}
        </LoadProvider>
    )
}