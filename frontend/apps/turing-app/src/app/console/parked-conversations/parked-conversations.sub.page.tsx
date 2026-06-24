import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconPlayerPause } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import ParkedConversationsPage from "./parked-conversations.page";

export default function ParkedConversationsSubPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("home.features.parkedConversations.title"));
  return (
    <>
      <SubPageHeader
        icon={IconPlayerPause}
        feature={t("home.features.parkedConversations.title")}
        name={t("home.features.parkedConversations.title")}
        description={t("home.features.parkedConversations.description")}
      />
      <ParkedConversationsPage />
    </>
  );
}
