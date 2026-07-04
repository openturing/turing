import { ROUTES } from "@/app/routes.const";
import type { TurSNRankingExpression } from "@/models/sn/sn-ranking-expression.model";
import { TurSNRankingExpressionService } from "@/services/sn/sn.site.result.ranking.service";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconNumber123 } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoSnSectionList } from "./bento.sn.section-list";

const service = new TurSNRankingExpressionService();

/** Bento SN result-ranking list — T576. */
export default function BentoSNResultRankingListPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [items, setItems] = useState<TurSNRankingExpression[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    service.query(id).then(setItems).catch(() => setError(t("common.connectionError", { resource: t("sn.resultRanking.title") })));
  }, [id, t]);

  const gridItemList = useGridAdapter(items, {
    name: "name",
    description: "description",
    url: (item) => `${ROUTES.BENTO_SN_INSTANCE}/${id}/result-ranking/${item.id}`,
  });

  return (
    <BentoSnSectionList
      siteId={id}
      section="result-ranking"
      icon={IconNumber123}
      title={t("sn.resultRanking.title")}
      subtitle={t("sn.resultRanking.description")}
      items={items}
      gridItemList={gridItemList}
      error={error}
      newLabel={t("sn.resultRanking.newResultRanking")}
      blankTitle={t("sn.resultRanking.blankTitle")}
      blankDescription={t("sn.resultRanking.blankDescription")}
      blankButton={t("sn.resultRanking.newResultRankingBlank")}
    />
  );
}
