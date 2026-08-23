import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteResultRankingForm } from "@/components/sn/result-ranking/sn.site.result.ranking.form";
import type { TurSNRankingExpression } from "@/models/sn/sn-ranking-expression.model";
import { TurSNRankingExpressionService } from "@/services/sn/sn.site.result.ranking.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const service = new TurSNRankingExpressionService();

/** Bento SN result-ranking detail — T576. */
export default function BentoSNResultRankingPage() {
  const { id, resultRankingId } = useParams() as { id: string; resultRankingId: string };
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [value, setValue] = useState<TurSNRankingExpression>();
  // T1017 — derived, not stored. `isNew` was state that an effect wrote to
  // `resultRankingId === "new"`, which is the same value the router already has: holding it
  // meant a route change from an existing record to "new" rendered one pass
  // with the previous answer before the effect corrected it.
  const isNew = resultRankingId === "new";
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/result-ranking`;

  useEffect(() => {
    if (resultRankingId === "new") {
      service.query(id).then(() => setValue({} as TurSNRankingExpression))
        .catch(() => setError(t("common.connectionError", { resource: t("sn.resultRanking.title") })));
    } else {
      service.get(id, resultRankingId).then((r) => { setValue(r); })
        .catch(() => setError(t("common.connectionError", { resource: t("sn.resultRanking.title") })));
    }
  }, [id, resultRankingId, t]);

  async function onDelete() {
    if (!value) return;
    try {
      if (await service.delete(id, value)) {
        toast.success(t("sn.resultRanking.deleted", { name: value.name }));
        navigate(listRoute);
      } else {
        toast.error(t("sn.resultRanking.notDeleted", { name: value.name }));
      }
    } catch (err) {
      console.error("Delete error", err);
      toast.error(t("sn.resultRanking.notDeleted", { name: value.name }));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={value} error={error} tryAgainUrl={`${listRoute}/${resultRankingId}`}>
      {value && (
        <SNSiteResultRankingForm
          snSiteId={id}
          value={value}
          isNew={isNew}
          onDelete={onDelete}
          open={open}
          setOpen={setOpen}
          baseRoute={ROUTES.BENTO_SN_INSTANCE}
          chrome="bento"
        />
      )}
    </LoadProvider>
  );
}
