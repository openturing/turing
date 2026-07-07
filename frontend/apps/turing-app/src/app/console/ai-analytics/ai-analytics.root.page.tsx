import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconReportAnalytics } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * "AI Analytics & Costs" — level-1 page that provides the site header and
 * breadcrumb. The secondary sidebar grouping the observability sub-pages is
 * rendered one level down by {@link AiAnalyticsLayoutPage}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export default function AiAnalyticsRootPage() {
  const { t } = useTranslation();
  const feature = t("home.features.aiAnalytics.title");
  useSubPageBreadcrumb({ label: feature, href: ROUTES.AI_ANALYTICS_ROOT });
  return (
    <Page turIcon={IconReportAnalytics} title={feature} urlBase={ROUTES.AI_ANALYTICS_ROOT} />
  );
}
