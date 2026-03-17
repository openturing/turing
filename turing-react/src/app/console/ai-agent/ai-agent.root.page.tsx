import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconRobot } from "@tabler/icons-react";

export default function AIAgentRootPage() {
  useSubPageBreadcrumb({ label: "AI Agent", href: `${ROUTES.AI_AGENT_INSTANCE}` });
  return (
    <Page turIcon={IconRobot} title="AI Agent" urlBase={ROUTES.AI_AGENT_INSTANCE} urlNew={`${ROUTES.AI_AGENT_INSTANCE}/new`} />
  )
}
