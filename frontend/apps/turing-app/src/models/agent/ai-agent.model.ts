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

/**
 * T166 / §X.9.d — scope for the Anthropic memory tool (T163) file area.
 * - "CONVERSATION" — memory isolated per conversation (default).
 * - "USER"         — memory shared across every conversation an authenticated
 *                    end user has with the agent (per-Keycloak-`sub`); falls
 *                    back to per-conversation when no stable identity exists.
 * @since 2026.3.4
 */
export type TurMemoryScope = "CONVERSATION" | "USER";

/**
 * Per-agent grounding policy. OPEN (default) keeps the flexible general-purpose
 * behaviour (code generation, tool calling, prompt used verbatim). STRICT_RAG
 * makes the agent answer ONLY from retrieved website content: a non-removable
 * guard is prefixed to the system prompt at runtime and off-topic tasks are
 * refused. Enforced on the Semantic Navigation RAG chat path.
 */
export type TurAgentGroundingMode = "OPEN" | "STRICT_RAG";

export interface TurAIAgent {
  id: string;
  title: string;
  description: string;
  systemPrompt?: string;
  /** Per-agent grounding policy (OPEN / STRICT_RAG). Default OPEN. */
  groundingMode?: TurAgentGroundingMode;
  /** User-authored brief that drives the SystemPromptEditor "Help me write" sheet. */
  systemPromptMetaPrompt?: string | null;
  enabled: number;
  ragEnabled: boolean;
  /** Opt-in: augments the system prompt with the ```html / ```d2 live-preview rendering guidance so the agent can emit interactive blocks (games, demos, Chart.js, diagrams). Default false. */
  richContentEnabled?: boolean;
  /** T787 — inject the model's knowledge cutoff into the system prompt. */
  discloseKnowledgeCutoff?: boolean;
  /** T187: opt-in "Live answers" hybrid mode. On a native OpenAI/Anthropic turn the agent fuses the indexed knowledge base + web_search + web_fetch + code_execution into ONE cited answer (injects [KB-n] grounding + a fusion-guidance prompt; the web/code tools come from the agent's selected native capabilities). Default false. */
  liveAnswersEnabled?: boolean;
  /** T189: opt-in real-time multilingual call-center augmentation. Enables `POST /call-center/assist`, which translates a transcribed customer utterance into the operator's language, answers it with citations from the index, and translates the answer back. Default false (endpoint returns 403). */
  callCenterEnabled?: boolean;
  /** T190: opt-in self-installing connectors. Enables the `/connector` catalog/guide/test endpoints so the agent can walk a customer through connecting a third-party system (token-generation steps + connection test before save/enable). When a real computer-use driver is deployed the steps can be driven in a browser; otherwise guided. Default false (endpoints return 403). */
  connectorSetupEnabled?: boolean;
  /** T191: opt-in overnight self-improvement. A nightly job distills the agent's recent production traffic into a fine-tuned candidate, scores it against the eval golden sets, and — only if it beats the incumbent — opens a DISTILLATION_CANDIDATE suggestion for human approval (never auto-applied). Requires the distillation tier enabled + an OpenAI-backed eval LLM. Default false. */
  overnightImprovementEnabled?: boolean;
  /** T436: opt-in to live `tool_call` events on the chat SSE so the UI can show the agent's tool activity ("calling search_knowledge_base…") while the tool loop runs. Applies to CALL-mode turns. Default false (streams stay byte-identical). */
  toolCallEventsEnabled?: boolean;
  /** T618: opt-in to capturing the exact assembled prompt per turn (system message + whole-message list) onto the storage seam, so the Live Preview can replay any past turn verbatim. Requires object storage enabled; bounded (per-conversation ring + per-entry cap + retention sweep). Default false (no extra storage writes). */
  promptCaptureEnabled?: boolean;
  /** T323: opt-in to Anthropic-compatible skills. When enabled (and the skill sandbox + a Default LLM are available), every enabled skill is offered to the agent's chat turns via the `run_skill` tool; the skill always runs on the Global Settings Default LLM. Default false. */
  skillsEnabled?: boolean;
  /** T603: opt-in to continuous / online eval. When enabled (and the global online-eval switch is on), a nightly sweep samples the agent's recent live traffic, grades it, and records a quality snapshot that flags drift vs the baseline. Default false. */
  onlineEvalEnabled?: boolean;
  /** T603: the reusable grader stack (T600) online eval scores sampled live traffic with. Blank = signal-only snapshot (sentiment / citation / failing rates, no grader scoring). */
  onlineEvalGraderStackId?: string | null;
  /** T145: opt-in to cross-vendor MCP federation. On a native OpenAI/Anthropic turn the agent's selected HTTP MCP servers are called directly by the provider (OpenAI remote mcp tool / Anthropic MCP Connector) instead of Turing's MCP client. Default false. */
  mcpNativeFederation?: boolean;
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
  /**
   * T291 / §XVI.3 (Block L) — soft monthly spend cap (USD). null/<=0 disables
   * the turn-time budget gate. On breach the gate downgrades to
   * {@link budgetDowngradeLlmId} (if set) or warns and proceeds. @since 2026.3.4
   */
  monthlyBudgetUsd?: number | null;
  /**
   * T291 / §XVI.3 (Block L) — per-turn cost warning threshold (USD). null/<=0
   * disables it. Post-hoc operator signal; never aborts a turn. @since 2026.3.4
   */
  perTurnSoftCapUsd?: number | null;
  /**
   * T291 / §XVI.3 (Block L) — cheaper LLM instance id to downgrade to once the
   * monthly budget is breached. null/blank = warn only. @since 2026.3.4
   */
  budgetDowngradeLlmId?: string | null;
  nativeTools?: string | null;
  /**
   * T433 / §X.18.b — per-agent selection of provider-native capabilities (CSV of
   * `TurNativeCapability` keys, mirroring {@link nativeTools}). `null`/undefined =
   * no explicit selection (legacy winner-takes-all native path); a non-null value
   * (even "") opts into the "capacidade é mutex, resto coexiste" execution model.
   * Written by the Tools & Capabilities picker (T434). @since 2026.3.4
   */
  nativeCapabilities?: string | null;
  /**
   * T435 / §X.18.d — per-agent REQUEST_OPTION selections as a JSON object keyed by
   * capability key (e.g. `{"reasoning-effort":"high","citations":"true"}`). Written
   * by the registry-driven Request Options settings; `null` = none. @since 2026.3.4
   */
  requestOptionsJson?: string | null;
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
  /**
   * T166 / §X.9.d — scope for the Anthropic memory tool (T163). Defaults to
   * `CONVERSATION`. @since 2026.3.4
   */
  memoryScope?: TurMemoryScope;
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
