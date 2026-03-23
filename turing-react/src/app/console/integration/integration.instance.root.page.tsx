import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconPlugConnectedX } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function IntegrationInstanceRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("integration.title"), href: `${ROUTES.INTEGRATION_INSTANCE}` });
  return (
    <Page turIcon={IconPlugConnectedX} title={t("integration.title")} urlBase={ROUTES.INTEGRATION_INSTANCE} />
  )
}
