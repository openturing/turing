import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconRobot } from "@tabler/icons-react";
import { useEffect } from "react";

export default function AIAgentRootPage() {
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    pushItem({ label: "AI Agent", href: `${ROUTES.AI_AGENT_INSTANCE}` });
    return () => popItem();
  }, []);
  return (
    <Page turIcon={IconRobot} title="AI Agent" urlBase={ROUTES.AI_AGENT_INSTANCE} urlNew={`${ROUTES.AI_AGENT_INSTANCE}/new`} />
  )
}
