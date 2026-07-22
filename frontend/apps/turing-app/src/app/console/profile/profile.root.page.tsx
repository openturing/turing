import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconId } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function ProfileRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("profile.title"), href: `${ROUTES.PROFILE_INSTANCE}` });
  return (
    <Page turIcon={IconId} title={t("profile.title")} urlBase={ROUTES.PROFILE_INSTANCE} />
  );
}
