import axios from "axios";

import type {
  TurConversationState,
  TurManualTurnRequest,
  TurManualTurnResult,
  TurSpectatorMessage,
  TurSpectatorSlots,
  TurSpectatorWorkspaceEvent,
} from "@/models/spectator/spectator.model";

/**
 * T120 — Spectator + Co-pilot mode client. Read paths are conversation-scoped
 * SSE streams consumed via native {@link EventSource} (cookie-authenticated,
 * GET only); the co-pilot "take the wheel" write is a CSRF-protected axios POST.
 *
 * <p>The SSE endpoints live under the same {@code /api} base axios uses, but
 * {@code EventSource} cannot reuse the axios interceptors, so URLs are built
 * from {@code VITE_API_URL} directly. {@code withCredentials} carries the admin
 * session cookie; the tenant header cannot ride on an EventSource (acceptable —
 * the streams are conversation-keyed and admin-scoped).
 *
 * Paired with {@code TurChatSessionAPI}.
 *
 * @since 2026.3.4
 */
const API_BASE = `${import.meta.env.VITE_API_URL}/api`;

function sessionUrl(conversationId: string, suffix: string): string {
  return `${API_BASE}/chat/sessions/${encodeURIComponent(conversationId)}${suffix}`;
}

/** Opens an EventSource and forwards each parsed JSON payload to {@code onEvent}. */
function openStream<T>(
  url: string,
  onEvent: (event: T) => void,
  onError?: (err: Event) => void,
): EventSource {
  const source = new EventSource(url, { withCredentials: true });
  source.onmessage = (e: MessageEvent<string>) => {
    if (!e.data) return;
    try {
      onEvent(JSON.parse(e.data) as T);
    } catch {
      // Ignore malformed frames (e.g. a stray heartbeat that slipped through).
    }
  };
  if (onError) source.onerror = onError;
  return source;
}

export class TurSpectatorService {
  /** Current flow/cursor state for the spectator header. */
  async state(conversationId: string): Promise<TurConversationState> {
    const response = await axios.get<TurConversationState>(
      `/chat/sessions/${encodeURIComponent(conversationId)}/state`,
    );
    return response.data;
  }

  /** Live message stream: transcript snapshot, then one event per turn. */
  openMessages(
    conversationId: string,
    onMessage: (event: TurSpectatorMessage) => void,
    onError?: (err: Event) => void,
  ): EventSource {
    return openStream(sessionUrl(conversationId, "/spectate/stream"), onMessage, onError);
  }

  /** Live slot stream: snapshot, then one event per slot write. */
  openSlots(
    conversationId: string,
    onSlots: (event: TurSpectatorSlots) => void,
    onError?: (err: Event) => void,
  ): EventSource {
    return openStream(sessionUrl(conversationId, "/spectate/slots/stream"), onSlots, onError);
  }

  /** Live workspace stream: relays blob put/delete metadata (no snapshot). */
  openWorkspace(
    conversationId: string,
    onEvent: (event: TurSpectatorWorkspaceEvent) => void,
    onError?: (err: Event) => void,
  ): EventSource {
    return openStream(sessionUrl(conversationId, "/spectate/workspace/stream"), onEvent, onError);
  }

  /** Co-pilot "take the wheel": inject an operator-authored assistant turn. */
  async manualTurn(
    conversationId: string,
    request: TurManualTurnRequest,
  ): Promise<TurManualTurnResult> {
    const response = await axios.post<TurManualTurnResult>(
      `/chat/sessions/${encodeURIComponent(conversationId)}/manual-turn`,
      request,
    );
    return response.data;
  }
}
