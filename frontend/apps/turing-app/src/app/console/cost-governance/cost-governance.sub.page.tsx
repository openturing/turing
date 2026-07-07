import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconCoin } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import CostGovernancePage from "./cost-governance.page";

export default function CostGovernanceSubPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("home.features.costGovernance.title"));
  return (
    <>
      <SubPageHeader
        icon={IconCoin}
        feature={t("home.features.costGovernance.title")}
        name={t("home.features.costGovernance.title")}
        description={t("home.features.costGovernance.description")}
      />
      <CostGovernancePage />
    </>
  );
}
