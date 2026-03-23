import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconZoomCode } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function SEInstanceRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("se.title"), href: `${ROUTES.SE_INSTANCE}` });
  return (
    <Page turIcon={IconZoomCode} title={t("se.title")} urlBase={ROUTES.SE_INSTANCE} />
  )
}
