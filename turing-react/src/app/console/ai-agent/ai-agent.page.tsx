import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";
import { TurAIAgentService } from "@/services/agent/ai-agent.service";
import { IconCpu2, IconRobot, IconServer2, IconSettings, IconTool } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turAIAgentService = new TurAIAgentService();

export default function AIAgentPage() {
  const { id } = useParams() as { id: string };
  const [agent, setAgent] = useState<TurAIAgent>({} as TurAIAgent);
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();
  const navigate = useNavigate();
  const urlBase = `${ROUTES.AI_AGENT_INSTANCE}/${id}`;

  const data = useMemo(() => {
    if (isNew) {
      return {
        navMain: [
          { title: "Settings", url: "/settings", icon: IconSettings },
        ],
      };
    }
    return {
      navMain: [
        { title: "Settings", url: "/settings", icon: IconSettings },
        { title: "Language Models", url: "/llm", icon: IconCpu2 },
        { title: "Native Tools", url: "/tools", icon: IconTool },
        { title: "MCP Servers", url: "/mcp", icon: IconServer2 },
      ],
    };
  }, [isNew]);

  useEffect(() => {
    let added = false;
    if (id === "new") {
      setAgent({} as TurAIAgent);
      setIsNew(true);
      pushItem({ label: "New AI Agent" });
      added = true;
    } else {
      turAIAgentService.get(id).then((a) => {
        setAgent(a);
        pushItem({ label: a.title, href: `${ROUTES.AI_AGENT_INSTANCE}/${a.id}` });
        added = true;
      }).catch(() => setError("Connection error or timeout while fetching AI agent."));
      setIsNew(false);
    }
    return () => {
      if (added) popItem();
    };
  }, [id]);

  async function onDelete() {
    try {
      if (await turAIAgentService.delete(agent)) {
        toast.success(`The ${agent.title} AI Agent was deleted`);
        navigate(ROUTES.AI_AGENT_INSTANCE);
      } else {
        toast.error(`The ${agent.title} AI Agent was not deleted`);
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(`The ${agent.title} AI Agent was not deleted`);
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.AI_AGENT_INSTANCE}/${id}`}>
      <SubPage icon={IconRobot} feature="AI Agent" name={agent.title}
        onDelete={onDelete} data={data} isNew={isNew} urlBase={urlBase} open={open} setOpen={setOpen} />
    </LoadProvider>
  );
}
