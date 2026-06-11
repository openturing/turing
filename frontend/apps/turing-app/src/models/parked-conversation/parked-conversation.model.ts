/**
 * T122 / §IX.6.b — one suspended conversation in the parked-conversations
 * dashboard. Mirrors the Spring DTO {@code TurParkedConversationDto}.
 *
 * @since 2026.3.1
 */
export interface TurParkedConversation {
  conversationId: string;
  agentId: string | null;
  agentTitle: string | null;
  flowId: string;
  flowName: string;
  nodeId: string;
  reason: string;
  /** ISO-8601 local date-time the conversation entered the suspend node. */
  since: string | null;
  /** Seconds the conversation has been blocked. */
  waitingSeconds: number;
}

/** Response of the admin unblock action. */
export interface TurParkedConversationUnblockResponse {
  resumed: number;
  wasParked: boolean;
  error: string | null;
}
