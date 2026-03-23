import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconDatabase } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function StoreInstanceListPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("store.title"), href: `${ROUTES.STORE_INSTANCE}` });
  return (
    <Page turIcon={IconDatabase} title={t("store.title")} urlBase={ROUTES.STORE_INSTANCE} />
  )
}
