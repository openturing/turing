import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";
import { TurAIAgentService } from "@/services/agent/ai-agent.service";
import { IconBraces, IconBulb, IconCpu2, IconHistory, IconRobot, IconServer2, IconSettings, IconSitemap, IconTool } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turAIAgentService = new TurAIAgentService();

export default function AIAgentPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [agent, setAgent] = useState<TurAIAgent>({} as TurAIAgent);
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[] | undefined>(
    id === "new" ? [{ label: t("aiAgent.newInstance") }] : undefined
  );
  useSubPageBreadcrumb(breadcrumb);
  const navigate = useNavigate();
  const urlBase = `${ROUTES.AI_AGENT_INSTANCE}/${id}`;

  const data = useMemo(() => {
    if (isNew) {
      return {
        navMain: [
          { title: t("aiAgent.nav.settings"), url: "/settings", icon: IconSettings },
        ],
      };
    }
    return {
      navMain: [
        { title: t("aiAgent.nav.settings"), url: "/settings", icon: IconSettings },
        { title: t("aiAgent.nav.languageModels"), url: "/llm", icon: IconCpu2 },
        { title: t("aiAgent.nav.nativeTools"), url: "/tools", icon: IconTool },
        { title: t("aiAgent.nav.mcpServers"), url: "/mcp", icon: IconServer2 },
        { title: t("aiAgent.nav.customTools"), url: "/custom-tool", icon: IconBraces },
        { title: t("aiAgent.nav.intents"), url: "/intent", icon: IconBulb },
        {
          // No `url` → InternalSidebar renders this as a SidebarGroupLabel
          // heading the children below, instead of a clickable nav item.
          title: t("aiAgent.nav.chat"),
          children: [
            { title: t("aiAgent.nav.chatFlow"), url: "/chat-flow", icon: IconSitemap },
            { title: t("aiAgent.nav.history"), url: "/history", icon: IconHistory },
          ],
        },
      ],
    };
  }, [isNew, t]);

  useEffect(() => {
    if (id === "new") {
      setAgent({} as TurAIAgent);
      setIsNew(true);
    } else {
      turAIAgentService.get(id).then((a) => {
        setAgent(a);
        setBreadcrumb([{ label: a.title, href: `${ROUTES.AI_AGENT_INSTANCE}/${a.id}` }]);
      }).catch(() => setError(t("common.connectionError", { resource: t("aiAgent.title").toLowerCase() })));
      setIsNew(false);
    }
  }, [id]);

  async function onDelete() {
    try {
      if (await turAIAgentService.delete(agent)) {
        toast.success(t("aiAgent.deleted", { name: agent.title }));
        navigate(ROUTES.AI_AGENT_INSTANCE);
      } else {
        toast.error(t("aiAgent.notDeleted", { name: agent.title }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("aiAgent.notDeleted", { name: agent.title }));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.AI_AGENT_INSTANCE}/${id}`}>
      <SubPage icon={IconRobot} feature={t("aiAgent.title")} name={agent.title}
        onDelete={onDelete} data={data} isNew={isNew} urlBase={urlBase} open={open} setOpen={setOpen} />
    </LoadProvider>
  );
}
