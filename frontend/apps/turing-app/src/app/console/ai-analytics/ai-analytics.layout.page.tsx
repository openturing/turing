import { ROUTES } from "@/app/routes.const";
import { SubPage } from "@/components/sub.page";
import { IconReportAnalytics } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useAiAnalyticsNav } from "./use-ai-analytics-nav";

/**
 * Level-2 layout for "AI Analytics & Costs": renders the secondary sidebar
 * grouping the observability sub-pages inside the site header provided by
 * {@link AiAnalyticsRootPage}. The index redirect to the first visible
 * sub-page lives in {@link AiAnalyticsIndexRedirect}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export default function AiAnalyticsLayoutPage() {
  const { t } = useTranslation();
  const { items } = useAiAnalyticsNav();
  const feature = t("home.features.aiAnalytics.title");

  return (
    <SubPage
      icon={IconReportAnalytics}
      feature={feature}
      name={feature}
      urlBase={ROUTES.AI_ANALYTICS_ROOT}
      data={{ navMain: items }}
    />
  );
}
