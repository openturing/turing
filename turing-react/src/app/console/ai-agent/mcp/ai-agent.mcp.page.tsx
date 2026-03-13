import { ROUTES } from "@/app/routes.const";
import { AIAgentMcpForm } from "@/components/agent/ai-agent.mcp.form";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";
import { TurAIAgentService } from "@/services/agent/ai-agent.service";
import { IconServer2 } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turAIAgentService = new TurAIAgentService();

export default function AIAgentMcpPage() {
  const { id } = useParams() as { id: string };
  const [agent, setAgent] = useState<TurAIAgent>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    turAIAgentService.get(id)
      .then(setAgent)
      .catch(() => setError("Connection error or timeout while fetching AI agent."));
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.AI_AGENT_INSTANCE}/${id}/mcp`}>
      <SubPageHeader icon={IconServer2} name="MCP Servers" feature="MCP Servers"
        description="Select one or more MCP servers for tool access." />
      {agent && <AIAgentMcpForm value={agent} />}
    </LoadProvider>
  );
}
