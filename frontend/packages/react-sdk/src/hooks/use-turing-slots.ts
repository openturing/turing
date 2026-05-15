import { useCallback, useEffect, useRef, useState } from "react";
import {
  fetchAgentChatSlots,
  fetchSiteChatSlots,
  type TurChatSessionSlots,
} from "../core/api";
import { readTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../core/session";
import { useTuringContext } from "../core/use-turing-context";

export type SlotsStatus = "idle" | "loading" | "success" | "error";

/**
 * Selects agent mode for {@link useTuringSlots}. When omitted, the hook
 * reads from the site's RAG endpoint (same default as {@link useTuringChat}).
 * Provide the agent id to read from the explicit agent endpoint instead.
 *
 * @since 2026.2.7
 */
export interface UseTuringSlotsAgent {
  readonly id: string;
}

export interface UseTuringSlotsOptions {
  /**
   * Forces the conversation id the request keys on. When omitted, the hook
   * reads the {@code TUR_SESSION} cookie set by {@link useTuringChat} — i.e.
   * "give me the slots collected so far for the visitor in this tab".
   */
  readonly conversationId?: string;
  /**
   * Cookie name used to read the conversation id when {@code conversationId}
   * is not passed. Defaults to {@link TUR_SESSION_DEFAULT_COOKIE_NAME} —
   * override only if the deployment renamed the cookie via
   * {@code turing.chat.session.cookie-name}.
   */
  readonly sessionCookieName?: string;
  /**
   * Opt into agent mode: hits {@code GET /v2/ai-agent/{id}/chat-slots}
   * instead of the site endpoint. Use when the consumer knows the exact
   * agent (typical for authenticated console UIs).
   */
  readonly agent?: UseTuringSlotsAgent;
  /**
   * Polling interval in milliseconds. When greater than zero the hook
   * refetches on a timer so slot fills surface without a manual refresh.
   * Defaults to {@code 0} — caller invokes {@link UseTuringSlotsReturn.refresh}
   * (or relies on the auto-refetch after {@code conversationId} changes).
   */
  readonly pollInterval?: number;
  /**
   * Disables the hook entirely when {@code false}. Useful to pause polling
   * while a slide-in panel is closed. Defaults to {@code true}.
   */
  readonly enabled?: boolean;
}

export interface UseTuringSlotsReturn {
  /** Flat map of slot name → value, merged across every flow in the conversation. */
  slots: Readonly<Record<string, string>>;
  /** Conversation id the slots were read for, or {@code null} when none is available yet. */
  conversationId: string | null;
  /** Status of the most recent request. */
  status: SlotsStatus;
  /** Error message from the last failed fetch. */
  error: string | null;
  /** Force a refetch right now (also resets {@code status} to "loading"). */
  refresh: () => void;
}

const EMPTY_SLOTS: Readonly<Record<string, string>> = Object.freeze({});

/**
 * Reads the slots captured during the visitor's chat session and exposes them
 * as a flat map. Use to reflect already-collected values elsewhere on the
 * page — e.g. pre-fill a sign-up form once the assistant has captured the
 * visitor's name and email through a flow.
 *
 * Pairs with {@link useTuringChat}: both hooks key off the same
 * {@code TUR_SESSION} cookie, so the slots returned here are the same
 * conversation the chat is sending messages on.
 *
 * @example
 * ```tsx
 * const { slots, status } = useTuringSlots({ pollInterval: 3000 });
 *
 * useEffect(() => {
 *   if (slots.email && !form.values.email) {
 *     form.setFieldValue("email", slots.email);
 *   }
 * }, [slots.email]);
 * ```
 *
 * @since 2026.2.7
 */
export function useTuringSlots(options: UseTuringSlotsOptions = {}): UseTuringSlotsReturn {
  const { config } = useTuringContext();
  const {
    conversationId: explicitConversationId,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
    agent,
    pollInterval = 0,
    enabled = true,
  } = options;
  const agentId = agent?.id;

  // Resolve the conversation id eagerly so it's stable across renders — and so
  // the hook returns the same value the chat would post on the next turn.
  const initialConversationId =
    explicitConversationId ?? readTurSession(sessionCookieName);
  const [conversationId, setConversationId] = useState<string | null>(
    initialConversationId,
  );

  const [slots, setSlots] = useState<Readonly<Record<string, string>>>(EMPTY_SLOTS);
  const [status, setStatus] = useState<SlotsStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  // Track the conversation id we last successfully fetched for, so we can
  // clear stale slots when the visitor's session changes mid-mount.
  const lastFetchedConversationRef = useRef<string | null>(null);

  // Keep `conversationId` in sync with the explicit option / cookie. The
  // cookie may be minted by the chat hook on the first `send` — re-checking
  // on every render is cheap and lets the slots hook start polling as soon
  // as the cookie shows up.
  useEffect(() => {
    const next = explicitConversationId ?? readTurSession(sessionCookieName);
    setConversationId((current) => (current === next ? current : next));
  }, [explicitConversationId, sessionCookieName, tick]);

  useEffect(() => {
    if (!enabled || !conversationId) {
      return;
    }

    let alive = true;
    setStatus("loading");
    setError(null);

    const fetcher: Promise<TurChatSessionSlots> = agentId
      ? fetchAgentChatSlots(agentId, conversationId)
      : fetchSiteChatSlots(config.site, conversationId);

    fetcher
      .then((res) => {
        if (!alive) return;
        lastFetchedConversationRef.current = conversationId;
        setSlots(Object.freeze({ ...(res?.slots ?? {}) }));
        setStatus("success");
      })
      .catch((err) => {
        if (!alive) return;
        setError(err instanceof Error ? err.message : "Failed to load chat slots");
        setStatus("error");
      });

    return () => {
      alive = false;
    };
  }, [enabled, conversationId, agentId, config.site, tick]);

  // Drop cached slots when the conversation id flips to a different visitor
  // session — otherwise stale data leaks across logins / TUR_SESSION resets.
  useEffect(() => {
    if (
      lastFetchedConversationRef.current &&
      lastFetchedConversationRef.current !== conversationId
    ) {
      lastFetchedConversationRef.current = null;
      setSlots(EMPTY_SLOTS);
    }
  }, [conversationId]);

  // Polling: re-trigger the cookie-sync + fetcher effects above on a timer.
  // The interval intentionally runs even when {@code conversationId} is still
  // null — the {@code TUR_SESSION} cookie is minted by the first chat turn,
  // which may happen long AFTER this hook mounts (header + FlowOutputs render
  // with the page; the visitor types the first message later). Without polling
  // through the null phase we'd never observe the cookie appearing and the
  // header / slot-driven panels would only fill in after a manual refresh.
  // The bump only triggers a fetch once `conversationId` is non-null
  // (the fetcher effect early-returns in the null phase), so the polling
  // cost while the cookie is still missing is just one variable bump per tick.
  useEffect(() => {
    if (!enabled || pollInterval <= 0) {
      return;
    }
    const handle = globalThis.setInterval(() => {
      setTick((t) => t + 1);
    }, pollInterval);
    return () => {
      globalThis.clearInterval(handle);
    };
  }, [enabled, pollInterval]);

  const refresh = useCallback(() => {
    setTick((t) => t + 1);
  }, []);

  return {
    slots,
    conversationId,
    status,
    error,
    refresh,
  };
}
