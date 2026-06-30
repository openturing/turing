import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconGridDots } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import CapabilityMatrixPage from "./capability-matrix.page";

export default function CapabilityMatrixSubPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("capabilityMatrix.title"));
  return (
    <>
      <SubPageHeader
        icon={IconGridDots}
        feature={t("capabilityMatrix.title")}
        name={t("capabilityMatrix.title")}
        description={t("capabilityMatrix.description")}
      />
      <CapabilityMatrixPage />
    </>
  );
}
