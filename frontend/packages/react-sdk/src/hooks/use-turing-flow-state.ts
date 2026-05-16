import { useCallback, useEffect, useState } from "react";
import {
  fetchSiteChatState,
  type TurChatConversationState,
} from "../core/api";
import { readTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../core/session";
import { useTuringContext } from "../core/use-turing-context";

export interface UseTuringFlowStateOptions {
  readonly conversationId?: string;
  readonly sessionCookieName?: string;
  /**
   * Polling interval in milliseconds. The {@code /chat/state} endpoint
   * is a single-row read, so polling at 1-2s is cheap. Defaults to
   * {@code 0} (no polling — caller drives via {@link UseTuringFlowStateReturn.refresh}).
   */
  readonly pollInterval?: number;
  readonly enabled?: boolean;
}

export interface UseTuringFlowStateReturn {
  readonly state: TurChatConversationState | null;
  readonly conversationId: string | null;
  readonly isLoading: boolean;
  readonly error: string | null;
  readonly refresh: () => void;
}

/**
 * Reads the active chat-flow state for the visitor's conversation —
 * which flow is driving, which node is the cursor, what guardrail
 * method is enforcing, A/B experiment metadata. Complement to
 * {@link useTuringSlots}: slots = data captured; flow state = where
 * the engine is in the conversation graph.
 *
 * @example
 * ```tsx
 * const { state } = useTuringFlowState({ pollInterval: 2000 });
 * return (
 *   <div>Step: {state?.currentNodeId ?? "—"}</div>
 * );
 * ```
 *
 * @since 2026.2.7
 */
export function useTuringFlowState(
  options: UseTuringFlowStateOptions = {},
): UseTuringFlowStateReturn {
  const { config } = useTuringContext();
  const {
    conversationId: explicitConversationId,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
    pollInterval = 0,
    enabled = true,
  } = options;

  const initialConversationId =
    explicitConversationId ?? readTurSession(sessionCookieName);
  const [conversationId, setConversationId] = useState<string | null>(initialConversationId);
  const [state, setState] = useState<TurChatConversationState | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    const next = explicitConversationId ?? readTurSession(sessionCookieName);
    setConversationId((current) => (current === next ? current : next));
  }, [explicitConversationId, sessionCookieName, tick]);

  useEffect(() => {
    if (!enabled || !conversationId) return;
    let alive = true;
    setIsLoading(true);
    setError(null);
    fetchSiteChatState(config.site, conversationId)
      .then((res) => {
        if (!alive) return;
        setState(res);
        setIsLoading(false);
      })
      .catch((err) => {
        if (!alive) return;
        setError(err instanceof Error ? err.message : "Failed to load chat state");
        setIsLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [enabled, conversationId, config.site, tick]);

  useEffect(() => {
    if (!enabled || pollInterval <= 0) return;
    const handle = globalThis.setInterval(() => setTick((t) => t + 1), pollInterval);
    return () => globalThis.clearInterval(handle);
  }, [enabled, pollInterval]);

  const refresh = useCallback(() => setTick((t) => t + 1), []);

  return { state, conversationId, isLoading, error, refresh };
}
