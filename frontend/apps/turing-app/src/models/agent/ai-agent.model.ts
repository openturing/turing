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

/**
 * T66 / §VII.6.g — per-agent retention policy for the completed
 * `chat_flow_submission` archive (LGPD / GDPR compliance hook).
 * - "RETAIN_FOREVER"      — keep every submission (default).
 * - "RETAIN_DAYS"         — daily job deletes submissions older than
 *                           {@link TurAIAgent.submissionRetentionDays}.
 * - "DELETE_AFTER_EXPORT" — purge a conversation's submissions right after
 *                           it is exported via the session export endpoint.
 * @since 2026.3.1
 */
export type TurSubmissionRetention =
  | "RETAIN_FOREVER"
  | "RETAIN_DAYS"
  | "DELETE_AFTER_EXPORT";

export interface TurAIAgent {
  id: string;
  title: string;
  description: string;
  systemPrompt?: string;
  /** User-authored brief that drives the SystemPromptEditor "Help me write" sheet. */
  systemPromptMetaPrompt?: string | null;
  enabled: number;
  ragEnabled: boolean;
  /** Opt-in: augments the system prompt with the ```html / ```d2 live-preview rendering guidance so the agent can emit interactive blocks (games, demos, Chart.js, diagrams). Default false. */
  richContentEnabled?: boolean;
  /** T323: opt-in to Anthropic-compatible skills. When enabled (and the skill sandbox + a Default LLM are available), every enabled skill is offered to the agent's chat turns via the `run_skill` tool; the skill always runs on the Global Settings Default LLM. Default false. */
  skillsEnabled?: boolean;
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
  /**
   * T115 / §IX.3.e — opt-in to workspace-backed memory compression. When the
   * older pool (turns past `chatMemoryRecentN`) exceeds
   * `chatMemoryCompressionThresholdTokens`, those turns are summarized via one
   * cheap LLM call, stored in the conversation workspace, and replaced by a
   * compact summary block in the prompt. Composes with T30. @since 2026.3.1
   */
  chatMemoryCompressionEnabled: boolean;
  /** Older-pool token estimate above which compression fires (default 8192). @since 2026.3.1 */
  chatMemoryCompressionThresholdTokens: number;
  /** LLM instance id used for the summary; empty → Global Settings default LLM. @since 2026.3.1 */
  chatMemoryCompressionLlmId?: string | null;
  /** ISO-8601 Duration min interval between summaries per conversation (default "PT1H"). @since 2026.3.1 */
  chatMemoryCompressionInterval: string;
  /**
   * T309 / §IX.3.f — run the summary LLM call off the hot path. When true
   * (default) the triggering turn proceeds uncompressed and a background worker
   * generates the summary for the next turn; false = synchronous (legacy T115).
   * @since 2026.3.1
   */
  chatMemoryCompressionAsync: boolean;
  /**
   * T123 / §IX.7 — enforceable token budget for the per-turn prompt.
   * `0` (default) disables the check; the executor estimates the prompt
   * size before the LLM call and applies {@link overBudgetBehavior} when
   * the estimate exceeds this value. @since 2026.3.1
   */
  maxPromptTokens?: number;
  /**
   * T123 / §IX.7 — failure mode when the predicted prompt exceeds
   * {@link maxPromptTokens}. `WARN` logs and proceeds; `ERROR` short-
   * circuits the LLM call with a structured error reply; `COMPACT` is
   * reserved for the future T115 history compression (V1 degrades to
   * WARN with a logged notice). @since 2026.3.1
   */
  overBudgetBehavior?: "WARN" | "ERROR" | "COMPACT";
  nativeTools?: string | null;
  /** How the executor issues the LLM round-trip. @since 2026.2.7 */
  chatMode?: TurAgentChatMode;
  /**
   * T66 / §VII.6.g — retention policy for this agent's completed chat-flow
   * submissions. Defaults to `RETAIN_FOREVER`. @since 2026.3.1
   */
  submissionRetention?: TurSubmissionRetention;
  /**
   * T66 / §VII.6.g — age threshold in days, used only when
   * `submissionRetention === "RETAIN_DAYS"`. Null/0 disables the purge.
   * @since 2026.3.1
   */
  submissionRetentionDays?: number | null;
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
