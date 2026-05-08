import type { TurCustomTool } from "../customtool/custom-tool.model.ts";
import type { TurEmbeddingModel } from "../embedding/embedding-model.model.ts";
import type { TurLLMInstance } from "../llm/llm-instance.model.ts";
import type { TurMcpServer } from "../mcp/mcp-server.model.ts";
import type { TurPersona } from "../persona/persona.model.ts";
import type { TurStoreInstance } from "../store/store-instance.model.ts";

export interface TurAIAgent {
  id: string;
  title: string;
  description: string;
  systemPrompt?: string;
  /** User-authored brief that drives the SystemPromptEditor "Help me write" sheet. */
  systemPromptMetaPrompt?: string | null;
  enabled: number;
  ragEnabled: boolean;
  /** Persists every chat turn to the configured logging engine (mongodb/redis), keyed by conversationId. No-op when turing.logging.engine == none. */
  chatMemoryEnabled: boolean;
  /** Worker flush cadence in minutes. */
  chatMemoryFlushIntervalMinutes: number;
  /** Hard cap on persisted messages per conversation. */
  chatMemoryMaxMessages: number;
  nativeTools?: string | null;
  llmInstances: TurLLMInstance[];
  mcpServers: TurMcpServer[];
  customTools: TurCustomTool[];
  /** Catalog of voices this agent is allowed to speak with — chat flows pick from here. @since 2026.2.7 */
  personas?: TurPersona[];
  /** Voice used when no chat flow has overridden the active persona. @since 2026.2.7 */
  defaultPersona?: TurPersona | null;
  turEmbeddingModelInstance?: TurEmbeddingModel | null;
  turStoreInstance?: TurStoreInstance | null;
  icon?: string | null;
}
