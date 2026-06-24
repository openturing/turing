import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconMessageCircle2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import ChatAnalyticsPage from "./chat-analytics.page";

export default function ChatAnalyticsSubPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("home.features.chatAnalytics.title"));
  return (
    <>
      <SubPageHeader
        icon={IconMessageCircle2}
        feature={t("home.features.chatAnalytics.title")}
        name={t("home.features.chatAnalytics.title")}
        description={t("home.features.chatAnalytics.description")}
      />
      <ChatAnalyticsPage />
    </>
  );
}
