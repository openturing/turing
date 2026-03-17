import type { TurLLMInstance } from "../llm/llm-instance.model.ts";
import type { TurMcpServer } from "../mcp/mcp-server.model.ts";

export interface TurAIAgent {
  id: string;
  title: string;
  description: string;
  avatarUrl?: string | null;
  systemPrompt?: string;
  enabled: number;
  nativeTools?: string | null;
  llmInstances: TurLLMInstance[];
  mcpServers: TurMcpServer[];
}
