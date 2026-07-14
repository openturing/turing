import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { SNFieldCoveragePanel } from "@/components/sn/field-coverage/sn.field.coverage.panel";
import { IconChartHistogram } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Bento SN field-coverage — T558. The T388 coverage bars + T472 content-fit
 * charts, reusing the shared {@link SNFieldCoveragePanel} under a `BentoHero`.
 */
export default function BentoSNFieldCoveragePage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}`;

  return (
    <>
      <BentoHero
        backTo={instanceRoute}
        backLabel={t("sn.title")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
            <IconChartHistogram size={24} />
          </span>
        }
        title={t("sn.fieldCoverage.title")}
        subtitle={t("sn.fieldCoverage.description")}
      />
      <SNFieldCoveragePanel siteId={id} />
    </>
  );
}
