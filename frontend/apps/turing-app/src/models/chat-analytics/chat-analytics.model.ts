/**
 * Mirrors the JSON returned by /api/system/chat-analytics/**.
 *
 * The store backends (Mongo + Redis) emit the same column names so this single
 * type covers both. Enrichment fields are populated lazily by the scheduled
 * TurChatAnalyticsEnricher — every enrichment column must therefore tolerate
 * `null` even on rows the AI has eventually classified.
 *
 * @since 2026.2.7
 */
export interface TurChatAnalyticsSession {
  conversationId: string;
  agentId?: string | null;
  /** Resolved agent display name — falls back to agentId when the agent was deleted. @since 2026.2.7 */
  agentTitle?: string | null;
  personaId?: string | null;
  /** Resolved persona display name — falls back to personaId when the persona was deleted. @since 2026.2.7 */
  personaName?: string | null;
  llmInstanceId?: string | null;
  embeddingModelId?: string | null;
  storeInstanceId?: string | null;
  userId?: string | null;
  locale?: string | null;
  /** IANA timezone the visitor's browser reported via X-Timezone (T74). */
  timezone?: string | null;
  /** Device class derived from the User-Agent (T74). */
  deviceType?: string | null;
  outcome?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  totalTurns?: number | null;
  totalTokensIn?: number | null;
  totalTokensOut?: number | null;
  /** Total tool callbacks invoked during the session (e.g. RAG search, custom tools). @since 2026.2.7 */
  totalToolCalls?: number | null;
  /** Subset of {@link totalToolCalls} that threw or returned an error envelope. @since 2026.2.7 */
  totalToolErrors?: number | null;
  /** Sum of per-tool-call wall-clock latency for the entire session, in ms. @since 2026.2.7 */
  totalToolLatencyMs?: number | null;
  durationMs?: number | null;
  firstUserMessage?: string | null;
  /**
   * Conversation id of the orchestrator session that spawned this one via a
   * Custom Tool `agent.invoke(...)` call (T109). {@code null} for top-level
   * (visitor-initiated) sessions. Drives the "Parent conversation"
   * group-by dimension on the scorecard so admins can see how many
   * sub-tasks an orchestrator agent dispatches per turn.
   *
   * @since 2026.3.1 (T110)
   */
  parentConversationId?: string | null;
  // Enrichment columns — null until the scheduled enricher classifies the session.
  intentLabel?: string | null;
  intentConfidence?: number | null;
  goalSummary?: string | null;
  goalAchieved?: string | null;
  sentiment?: string | null;
  keyTerms?: string[] | null;
  /**
   * Per-turn sentiment trajectory (T87) — one sentiment label per user turn in
   * chronological order, so the drill-down can chart where the conversation
   * soured rather than collapsing it to the single session-level {@link sentiment}.
   * Only the LLM classifier populates it; absent/empty for the catalog-driven
   * strategies and until the scheduled enricher has run.
   *
   * @since 2026.3.1 (T87)
   */
  sentimentTrajectory?: string[] | null;
  processedAt?: string | null;
}

export interface TurChatTranscriptMessage {
  role?: string | null;
  content?: string | null;
  timestamp?: string | null;
}

export interface TurChatAnalyticsTranscript {
  session: TurChatAnalyticsSession;
  messages: TurChatTranscriptMessage[];
}

export interface TurChatAnalyticsHealth {
  enabled: boolean;
  engine: string;
}

export interface TurChatAnalyticsSessionsParams {
  from?: string;
  to?: string;
  agentId?: string;
  personaId?: string;
  outcome?: string;
  intentLabel?: string;
  goalAchieved?: string;
  sentiment?: string;
  limit?: number;
}

/**
 * Metrics accepted by the {@code /system/chat-analytics/timeseries} endpoint.
 * The backend whitelists the same set in {@code sanitizeMetric}; anything
 * outside this union silently falls back to "sessions".
 *
 * @since 2026.2.7
 */
export type TurChatAnalyticsMetric =
  | "sessions"
  | "goal_achievement_rate"
  | "negative_sentiment_rate"
  | "avg_duration_ms"
  | "avg_tokens_out"
  | "tool_calls_per_session"
  | "tool_errors_per_session"
  | "avg_tool_latency_ms"
  | "tool_error_rate_pct";

/**
 * One bucket of the {@code /timeseries} response. {@code value} units depend
 * on the {@code metric}: counts for {@code sessions}, ms for the duration/
 * latency metrics, fractions 0-1 for {@code *_rate}, percent 0-100 for
 * {@code *_pct} variants.
 */
export interface TurChatAnalyticsTimeseriesPoint {
  /** ISO-8601 bucket start (hour or day floor, per the {@code interval} param). */
  bucket: string;
  /** Number of sessions in this bucket (constant across metrics). */
  total: number;
  /** Metric value for this bucket — see {@link TurChatAnalyticsMetric}. */
  value: number;
}

export interface TurChatAnalyticsTimeseriesParams {
  from?: string;
  to?: string;
  /** Defaults to "sessions" on the server. */
  metric?: TurChatAnalyticsMetric;
  /** "hour" or "day". Defaults to "hour" on the server. */
  interval?: "hour" | "day";
}

/**
 * Dimensions accepted by the {@code /scorecard} endpoint. {@code agentId}
 * and {@code personaId} pivot on identity for head-to-head agent/persona
 * comparison; {@code experimentKey} and {@code variantLabel} pivot on the
 * A/B test arm so you can see which variant lifts the metric;
 * {@code parentConversationId} (T110) pivots on the orchestrator that
 * spawned the session via Custom Tool {@code agent.invoke(...)} so a
 * drill-down can answer "orchestrator agent X invoked specialist Y in
 * N sessions, average sub-task duration M ms."
 *
 * @since 2026.2.7
 */
export type TurChatAnalyticsScorecardDimension =
  | "agentId"
  | "personaId"
  | "experimentKey"
  | "variantLabel"
  | "parentConversationId"
  // T74 — visitor cohort dimensions (device class / locale / timezone).
  | "deviceType"
  | "locale"
  | "timezone";

/**
 * Device-class cohort values (T74). Classified server-side from the
 * {@code User-Agent} by {@code TurChatCohortResolver}; mirrors its
 * {@code DEVICE_*} constants. Used as the headline "mobile vs desktop"
 * scorecard filter.
 */
export type TurChatDeviceType = "mobile" | "tablet" | "desktop" | "bot" | "unknown";

/**
 * One row of the {@code /scorecard} response. {@code bucket} is the raw
 * identity (agent id / persona id / experimentKey / variantLabel),
 * {@code bucketLabel} is the human-readable name the server resolved
 * (agent.title / persona.name) — for experiment dimensions both columns
 * carry the same value since experiment keys/labels are already strings.
 */
export interface TurChatAnalyticsScorecardRow {
  bucket: string;
  bucketLabel?: string | null;
  sessions: number;
  avgDurationMs?: number | null;
  goalAchievedRate?: number | null;
  negativeRate?: number | null;
  topIntent?: string | null;
}

export interface TurChatAnalyticsScorecardParams {
  from?: string;
  to?: string;
  /** Defaults to "agentId" on the server. */
  dimension?: TurChatAnalyticsScorecardDimension;
  /** Defaults to 20 on the server, capped at 100. */
  limit?: number;
  /**
   * T74 cohort filters — narrow the population BEFORE grouping so a variant
   * can be evaluated within a sub-population ("does variant X win on mobile?").
   * Blank/undefined values are ignored by the server.
   */
  deviceType?: TurChatDeviceType | "";
  locale?: string;
  timezone?: string;
  experimentKey?: string;
  variantLabel?: string;
}

/** Also surfaced on each session row (T74). */
export interface TurChatAnalyticsCohortColumns {
  timezone?: string | null;
  deviceType?: TurChatDeviceType | string | null;
}

/**
 * One row of the {@code /system/chat-analytics/tool-latency} response (T88).
 * Per-tool latency percentiles pooled across every session in the window,
 * sorted by {@link p95Ms} descending so the outlier is first. Disaggregates
 * the session-level {@code avg_tool_latency_ms} timeseries metric — that one
 * averages across all tools and hides a single slow tool.
 *
 * @since 2026.3.1
 */
export interface TurChatAnalyticsToolLatencyRow {
  /** Sanitized tool name (Spring AI {@code ToolDefinition.name()}); "unknown" when blank. */
  tool: string;
  /** Total invocations of this tool in the window. */
  count: number;
  /** Subset of {@link count} that threw or returned an error envelope. */
  errors: number;
  /** errors / count × 100 (0-100). */
  errorRatePct: number;
  avgMs: number;
  minMs: number;
  p50Ms: number;
  /** The headline T88 number — the p95 latency for this tool. */
  p95Ms: number;
  p99Ms: number;
  maxMs: number;
}

export interface TurChatAnalyticsToolLatencyParams {
  from?: string;
  to?: string;
  /** Optional — narrow to one agent's tool mix. */
  agentId?: string;
  /** Top-N tools by p95. Defaults to 50 on the server, capped at 200. */
  limit?: number;
}

/**
 * Which phase of the chat-flow router cascade picked the winner (T89).
 * Mirrors the backend {@code TurChatFlowRouterMethod} enum.
 *
 * @since 2026.3.1
 */
export type TurChatAnalyticsRouterMethod = "PROCEDURAL" | "LLM" | "LLM_CACHE" | "NONE";

/** One flow that competed in a router decision (T89). */
export interface TurChatAnalyticsRouterCandidate {
  flowId: string;
  flowName: string | null;
  /** Keyword (MoreLikeThis) score; null when the procedural pass didn't score it. */
  score: number | null;
  winner: boolean;
}

/**
 * One chat-flow router decision (T89 / §VII.10.f) — the candidate flows that
 * competed, the keyword score each earned, the winner, and the mechanism that
 * picked it. Answers "why did B2B lose to B2C on this message?" when triaging
 * a complaint. Read from the in-memory ring at
 * {@code /system/chat-analytics/router-decisions}.
 *
 * @since 2026.3.1
 */
export interface TurChatAnalyticsRouterDecision {
  epochMillis: number;
  agentId: string;
  conversationId: string;
  /** The (≤280-char truncated) user message that triggered the routing pass. */
  userMessage: string | null;
  method: TurChatAnalyticsRouterMethod;
  winnerFlowId: string | null;
  winnerFlowName: string | null;
  candidates: TurChatAnalyticsRouterCandidate[];
  /** Procedural-pass numbers — null for an LLM pick that ran no keyword scoring. */
  bestScore: number | null;
  secondScore: number | null;
  dominanceRatio: number | null;
  /** True when the keyword pass produced scores (best/second/ratio populated). */
  procedural: boolean;
  detail: string | null;
}

/**
 * One open slot-stream SSE channel (T90 / §VII.10.g) — a
 * {@code (conversationId, mode)} pair with the count of live connections
 * subscribed to it. The server-side view of what the vanilla SDK's
 * {@code _slotsSseOpenChannelCount()} tracks per browser.
 *
 * @since 2026.3.1
 */
export interface TurChatSlotSseChannel {
  conversationId: string;
  /** "snapshot" (full map) or "delta" (T63 incremental stream). */
  mode: "snapshot" | "delta";
  /** Number of live SSE connections on this channel. */
  refcount: number;
  firstOpenedEpochMillis: number;
  lastChangeEpochMillis: number;
}

/**
 * Live snapshot of the open slot-stream SSE channels on the node (T90). Read
 * from {@code /system/chat-analytics/slot-sse-channels}. Per-process and
 * ephemeral — counts only this node and resets on restart.
 *
 * @since 2026.3.1
 */
export interface TurChatSlotSseChannels {
  /** Distinct (conversationId, mode) channels open. */
  openChannels: number;
  /** Sum of every channel's refcount — total live connections. */
  openSubscribers: number;
  /** Distinct conversation ids across both modes. */
  distinctConversations: number;
  channels: TurChatSlotSseChannel[];
}
