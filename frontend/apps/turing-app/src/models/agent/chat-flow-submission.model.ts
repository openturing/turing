/**
 * One finished run of a chat flow, as exposed by
 * `GET /api/ai-agent/{agentId}/chat-flow/{flowId}/submissions`.
 *
 * @since 2026.2.5
 */
export interface TurChatFlowSubmission {
  conversationId: string;
  flowId: string;
  /** ISO-8601 timestamp from the backend (LocalDateTime). */
  completedAt: string;
  /** Variables captured during the run, keyed by `outputVariable` name. */
  variables: Record<string, string>;
  /** Authenticated user that ran the flow, when available. */
  userId?: string | null;
  /** Terminal node id — real `end` node id, or `__abandoned__` for quits. */
  endNodeId: string;
}
