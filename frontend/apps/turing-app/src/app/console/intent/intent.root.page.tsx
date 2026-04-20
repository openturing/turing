import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconBulb } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function IntentRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("intent.title"), href: `${ROUTES.INTENT_INSTANCE}` });
  return (
    <Page turIcon={IconBulb} title={t("intent.title")} urlBase={ROUTES.INTENT_INSTANCE} />
  )
}
