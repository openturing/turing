import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconReceiptRupee } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import LoggingInstanceListPage from "./logging.instance.list.page";

export default function LoggingInstanceSubPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("home.features.logging.title"));
  return (
    <>
      <SubPageHeader
        icon={IconReceiptRupee}
        feature={t("home.features.logging.title")}
        name={t("home.features.logging.title")}
        description={t("home.features.logging.description")}
      />
      <LoggingInstanceListPage />
    </>
  );
}
