import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconFileImport } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import ExchangeImportPage from "./exchange.import.page";

export default function ExchangeImportSubPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("home.features.import.title"));
  return (
    <>
      <SubPageHeader
        icon={IconFileImport}
        feature={t("home.features.import.title")}
        name={t("home.features.import.title")}
        description={t("home.features.import.description")}
      />
      <ExchangeImportPage />
    </>
  );
}
