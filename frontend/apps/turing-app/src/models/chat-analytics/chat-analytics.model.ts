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
  // Enrichment columns — null until the scheduled enricher classifies the session.
  intentLabel?: string | null;
  intentConfidence?: number | null;
  goalSummary?: string | null;
  goalAchieved?: string | null;
  sentiment?: string | null;
  keyTerms?: string[] | null;
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
