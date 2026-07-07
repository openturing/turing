import { SNFieldCoveragePanel } from "@/components/sn/field-coverage/sn.field.coverage.panel";
import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconChartBar } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function SNSiteFieldCoverageListPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("sn.fieldCoverage.title"));

  return (
    <>
      <SubPageHeader
        icon={IconChartBar}
        name={t("sn.fieldCoverage.title")}
        feature={t("sn.fieldCoverage.title")}
        description={t("sn.fieldCoverage.description")}
      />
      <div className="px-6">
        <SNFieldCoveragePanel siteId={id} />
      </div>
    </>
  );
}
