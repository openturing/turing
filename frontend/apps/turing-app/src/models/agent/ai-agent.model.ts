import type { TurCustomTool } from "../customtool/custom-tool.model.ts";
import type { TurEmbeddingModel } from "../embedding/embedding-model.model.ts";
import type { TurLLMInstance } from "../llm/llm-instance.model.ts";
import type { TurMcpServer } from "../mcp/mcp-server.model.ts";
import type { TurPersona } from "../persona/persona.model.ts";
import type { TurSEInstance } from "../se/se-instance.model.ts";
import type { TurStoreInstance } from "../store/store-instance.model.ts";

/**
 * Per-agent toggle for how the executor issues the LLM round-trip.
 * - "CALL"   — blocking `chatModel.call(prompt)`; assistant text arrives
 *              as a single SSE event after the LLM finishes (default).
 * - "STREAM" — reactive `chatModel.stream(prompt)`; each token chunk is
 *              forwarded immediately to the SSE pipeline so the bubble
 *              fills in token-by-token. Effective on the non-flow path
 *              only — flow-driven turns silently fall back to CALL so
 *              the advance/regen pipeline still works.
 * @since 2026.2.7
 */
export type TurAgentChatMode = "CALL" | "STREAM";

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
  /**
   * T30 / §IV.4 — opt-in to BM25 retrieval of older turns when persisted
   * memory has more entries than `chatMemoryRecentN`. The retrieved top-K
   * are prepended (with position/timestamp markers) to the recent window
   * fed to the LLM. @since 2026.3.1
   */
  chatMemoryRelevanceEnabled: boolean;
  /** Number of older turns the BM25 retriever returns per request (1-20). @since 2026.3.1 */
  chatMemoryRelevanceTopK: number;
  /** Size of the recent FIFO window kept verbatim; the retriever only scores turns OLDER than this. @since 2026.3.1 */
  chatMemoryRecentN: number;
  nativeTools?: string | null;
  /** How the executor issues the LLM round-trip. @since 2026.2.7 */
  chatMode?: TurAgentChatMode;
  // T19/T24 reposition (2026.2.7): ragBm25Fallback and ragHybridSearch
  // moved from TurAIAgent to TurSNSiteGenAi — see sn-site-genai.model.ts.
  /**
   * Optional agent-specific Python `requirements.txt` addendum. Unioned
   * with the global `pythonRequirements` setting before the Code
   * Interpreter sandbox runs any Custom Tool that calls
   * `code.executePython(...)`. Empty/null = global-only (preserves
   * pre-2026.2.7 behavior). @since 2026.2.7
   */
  pythonRequirements?: string | null;
  llmInstances: TurLLMInstance[];
  mcpServers: TurMcpServer[];
  customTools: TurCustomTool[];
  /** Catalog of voices this agent is allowed to speak with — chat flows pick from here. @since 2026.2.7 */
  personas?: TurPersona[];
  /** Voice used when no chat flow has overridden the active persona. @since 2026.2.7 */
  defaultPersona?: TurPersona | null;
  turEmbeddingModelInstance?: TurEmbeddingModel | null;
  turStoreInstance?: TurStoreInstance | null;
  /**
   * T28 / §III.5 Phase C — optional SE binding for the analytics intent
   * index. When null, {@code TurAnalyticsIntentIndexer} falls back to the
   * global default property (or the first registered SE instance).
   * @since 2026.3.1
   */
  analyticsSeInstance?: TurSEInstance | null;
  icon?: string | null;
}
