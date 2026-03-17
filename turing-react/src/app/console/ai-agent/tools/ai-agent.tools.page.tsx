import { ROUTES } from "@/app/routes.const";
import { AIAgentToolsForm } from "@/components/agent/ai-agent.tools.form";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";
import { TurAIAgentService } from "@/services/agent/ai-agent.service";
import { IconTool } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turAIAgentService = new TurAIAgentService();

export default function AIAgentToolsPage() {
  const { id } = useParams() as { id: string };
  const [agent, setAgent] = useState<TurAIAgent>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    turAIAgentService.get(id)
      .then(setAgent)
      .catch(() => setError("Connection error or timeout while fetching AI agent."));
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.AI_AGENT_INSTANCE}/${id}/tools`}>
      <SubPageHeader icon={IconTool} name="Native Tools" feature="Native Tools"
        description="Select which native tool callings are available to this agent." />
      {agent && <AIAgentToolsForm value={agent} />}
    </LoadProvider>
  );
}
