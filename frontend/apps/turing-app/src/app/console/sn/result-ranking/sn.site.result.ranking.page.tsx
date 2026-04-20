import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteResultRankingForm } from "@/components/sn/result-ranking/sn.site.result.ranking.form";
import type { TurSNRankingExpression } from "@/models/sn/sn-ranking-expression.model";
import { TurSNRankingExpressionService } from "@/services/sn/sn.site.result.ranking.service";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turSNRankingExpressionService = new TurSNRankingExpressionService();

export default function SNSiteResultRankingPage() {
    const navigate = useNavigate();
    const { id, resultRankingId } = useParams() as { id: string, resultRankingId: string };
    const [resultRanking, setResultRanking] = useState<TurSNRankingExpression>();
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
    const { t } = useTranslation();

    useEffect(() => {
        if (resultRankingId === "new") {
            turSNRankingExpressionService.query(id).then(() => setResultRanking({} as TurSNRankingExpression)).catch(() => setError("Connection error or timeout while fetching result rankings."));
            setBreadcrumb([{ label: t("sn.resultRanking.title"), href: `${ROUTES.SN_INSTANCE}/${id}/result-ranking` }, { label: t("common.new") }]);
        } else {
            turSNRankingExpressionService.get(id, resultRankingId).then((ranking) => {
                setResultRanking(ranking);
                setBreadcrumb([{ label: t("sn.resultRanking.title"), href: `${ROUTES.SN_INSTANCE}/${id}/result-ranking` }, { label: ranking.name || resultRankingId }]);
            }).catch(() => setError("Connection error or timeout while fetching result ranking details."));
            setIsNew(false);
        }
    }, [resultRankingId]);

    useSubPageBreadcrumb(breadcrumb);
    async function onDelete() {
        if (!resultRanking) return;
        try {
            if (await turSNRankingExpressionService.delete(id, resultRanking)) {
                toast.success(t("sn.resultRanking.deleted", { name: resultRanking.name }));
                navigate(`${ROUTES.SN_INSTANCE}/${id}/result-ranking`);
            } else {
                toast.error(t("sn.resultRanking.notDeleted", { name: resultRanking.name }));
            }

        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("sn.resultRanking.notDeleted", { name: resultRanking.name }));
        }
        setOpen(false);
    }
    return (
        <LoadProvider checkIsNotUndefined={resultRanking} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/result-ranking`}>
            {resultRanking && <SNSiteResultRankingForm snSiteId={id} value={resultRanking} isNew={isNew} onDelete={onDelete} open={open} setOpen={setOpen} />}
        </LoadProvider>
    )
}