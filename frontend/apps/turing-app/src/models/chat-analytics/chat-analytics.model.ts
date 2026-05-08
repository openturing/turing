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
