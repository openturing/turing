import type { TurEmbeddingModel } from "../embedding/embedding-model.model.ts";
import type { TurLLMInstance } from "../llm/llm-instance.model.ts";
import type { TurMcpServer } from "../mcp/mcp-server.model.ts";
import type { TurStoreInstance } from "../store/store-instance.model.ts";

export interface TurAIAgent {
  id: string;
  title: string;
  description: string;
  systemPrompt?: string;
  enabled: number;
  ragEnabled: boolean;
  nativeTools?: string | null;
  llmInstances: TurLLMInstance[];
  mcpServers: TurMcpServer[];
  turEmbeddingModelInstance?: TurEmbeddingModel | null;
  turStoreInstance?: TurStoreInstance | null;
  icon?: string | null;
}
