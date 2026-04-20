import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconRobot } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function AIAgentRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("aiAgent.title"), href: `${ROUTES.AI_AGENT_INSTANCE}` });
  return (
    <Page turIcon={IconRobot} title={t("aiAgent.title")} urlBase={ROUTES.AI_AGENT_INSTANCE} />
  )
}
