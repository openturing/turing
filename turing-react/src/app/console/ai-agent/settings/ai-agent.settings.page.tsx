import { ROUTES } from "@/app/routes.const";
import { AIAgentSettingsForm } from "@/components/agent/ai-agent.settings.form";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";
import { TurAIAgentService } from "@/services/agent/ai-agent.service";
import { IconSettings } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turAIAgentService = new TurAIAgentService();

export default function AIAgentSettingsPage() {
  const { id } = useParams() as { id: string };
  const [agent, setAgent] = useState<TurAIAgent>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (id === "new") {
      setAgent({} as TurAIAgent);
    } else {
      turAIAgentService.get(id)
        .then(setAgent)
        .catch(() => setError("Connection error or timeout while fetching AI agent."));
      setIsNew(false);
    }
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.AI_AGENT_INSTANCE}/${id}/settings`}>
      <SubPageHeader icon={IconSettings} name="Settings" feature="Settings"
        description="General information, system prompt and status." />
      {agent && <AIAgentSettingsForm value={agent} isNew={isNew} />}
    </LoadProvider>
  );
}
