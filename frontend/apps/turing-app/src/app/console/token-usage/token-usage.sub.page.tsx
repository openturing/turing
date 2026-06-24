import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconChartBar } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import TokenUsagePage from "./token-usage.page";

export default function TokenUsageSubPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("home.features.tokenUsage.title"));
  return (
    <>
      <SubPageHeader
        icon={IconChartBar}
        feature={t("home.features.tokenUsage.title")}
        name={t("home.features.tokenUsage.title")}
        description={t("home.features.tokenUsage.description")}
      />
      <TokenUsagePage />
    </>
  );
}
