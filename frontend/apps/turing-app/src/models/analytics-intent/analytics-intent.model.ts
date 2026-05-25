/**
 * T28 / §III.5 — per-agent analytics intent catalog entry. One row per
 * intent label; {@code samples} is a newline-separated bag of
 * representative user utterances scored by the classifier strategies
 * (Lucene embedded MLT and SE-backed MLT). Independent from
 * {@code TurIntent} (chat starter intents) which has a different
 * purpose (UI starters with action prompts).
 */
export interface TurAnalyticsIntent {
  id?: string;
  label: string;
  samples?: string | null;
  description?: string | null;
  enabled: number;
}

/**
 * Response shape for the reindex bootstrap endpoint
 * ({@code POST /api/ai-agent/{agentId}/analytics-intent/reindex}).
 */
export interface ReindexAnalyticsIntentResponse {
  agentId: string;
  indexed: number;
  indexName: string;
}
