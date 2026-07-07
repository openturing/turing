import { ROUTES } from "@/app/routes.const";
import { AiSummaryPanel } from "@/components/ai-summary-panel";
import { BentoHero } from "@/components/bento";
import { IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";

/** Bento SN insights — T576. Reuses `AiSummaryPanel` under a `BentoHero`. */
export default function BentoSNInsightsPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}`;

  return (
    <>
      <BentoHero
        eyebrow={<Link to={instanceRoute} className="hover:text-foreground">{t("sn.title")}</Link>}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
            <IconSparkles size={24} />
          </span>
        }
        title={t("sn.insights.title")}
        subtitle={t("sn.insights.description")}
      />
      <AiSummaryPanel endpoint={`/sn/${id}/summary`} i18nPrefix="sn.insights" />
    </>
  );
}
