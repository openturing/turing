import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconPlayerPause } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function ParkedConversationsRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({
    label: t("parkedConversations.title"),
    href: ROUTES.PARKED_CONVERSATIONS,
  });
  return (
    <Page
      turIcon={IconPlayerPause}
      title={t("parkedConversations.title")}
      urlBase={ROUTES.PARKED_CONVERSATIONS}
    />
  );
}
