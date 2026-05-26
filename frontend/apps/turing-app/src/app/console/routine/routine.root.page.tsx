import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconClock } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * @since 2026.3.1
 */
export default function RoutineRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("routine.title"), href: `${ROUTES.ROUTINE_INSTANCE}` });
  return <Page turIcon={IconClock} title={t("routine.title")} urlBase={ROUTES.ROUTINE_INSTANCE} />;
}
