/**
 * T120 — Spectator + Co-pilot mode types. Mirrors the conversation-scoped
 * spectator endpoints on the backend {@code TurChatSessionAPI}
 * ({@code /api/chat/sessions/{id}/spectate/...} + {@code /manual-turn}).
 *
 * @since 2026.3.4
 */

/** One message on the live spectator stream (or the transcript snapshot). */
export interface TurSpectatorMessage {
  conversationId: string;
  role: "user" | "assistant";
  content: string;
  /** true when an operator authored this assistant turn (co-pilot). */
  manual: boolean;
  /** server emit timestamp; 0 for snapshot rows. */
  epochMillis: number;
}

/** Current flow/cursor state — powers the spectator header. */
export interface TurConversationState {
  conversationId: string;
  flowId: string | null;
  flowName: string | null;
  currentNodeId: string | null;
  guardrailMethod: string | null;
  experimentKey: string | null;
  variantLabel: string | null;
  /** non-null when the cursor parks on a suspend/humanApproval node. */
  suspendedReason: string | null;
}

/** Merged slot snapshot for the conversation. */
export interface TurSpectatorSlots {
  conversationId: string;
  slots: Record<string, string>;
}

/** Workspace blob mutation metadata (live relay, no bytes). */
export interface TurSpectatorWorkspaceEvent {
  conversationId: string;
  /** "put" | "delete". */
  event: string;
  key: string;
  contentType: string | null;
  size: number;
  signedUrl: string | null;
}

/** Co-pilot "take the wheel" request body. */
export interface TurManualTurnRequest {
  agentId: string;
  flowId?: string;
  /** the visitor message being answered; omit for a pure interjection. */
  userMessage?: string;
  assistantMessage: string;
}

/** Co-pilot manual-turn result. */
export interface TurManualTurnResult {
  ok: boolean;
  assistantMessage: string | null;
  currentNodeId: string | null;
  suggestedOptions: string[];
  error: string | null;
}
