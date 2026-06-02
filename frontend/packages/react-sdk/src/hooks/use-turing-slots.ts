import { useCallback, useEffect, useRef, useState } from "react";
import axios from "axios";
import {
  fetchAgentChatSlots,
  fetchSiteChatSlots,
  type TurChatSessionSlots,
} from "../core/api";
import { readTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../core/session";
import { subscribeSlotsSse } from "../core/slots-sse-multiplexer";
import { useOptionalTuringContext } from "../core/use-turing-context";

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
  /**
   * Transport for slot updates. {@code "polling"} (default) refetches on
   * the {@link UseTuringSlotsOptions#pollInterval} timer — works everywhere
   * but trades latency for compatibility. {@code "sse"} opens a single
   * EventSource against {@code GET /sn/{site}/chat/slots/stream}; the
   * server pushes the full slot map on every write (latency &lt;100 ms).
   *
   * <p>SSE is opt-in for now so existing apps don't change behavior on
   * upgrade. When SSE fails (network drop, server returns non-200, the
   * conversation cookie isn't set yet) the hook silently falls back to
   * the polling timer.
   *
   * <p>SSE is currently site-mode only — agent mode keeps polling
   * regardless of this option.
   *
   * @since 2026.2.7
   */
  readonly transport?: "polling" | "sse";
  /**
   * When {@code transport: "sse"}, consume the T63 delta stream
   * ({@code /chat/slots/stream/delta}) instead of full snapshots. The hook's
   * returned {@code slots} are identical — the multiplexer reconstructs the
   * map from deltas — only the wire bytes shrink (~10× on slot-heavy
   * conversations). Defaults to {@code false}.
   *
   * @since 2026.3.1
   */
  readonly sseDelta?: boolean;
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
  // Site mode requires the provider for `config.site`; agent mode never
  // reads from it. Use the optional context so an admin console that has
  // no real "site" can still mount the agent-mode variant of this hook
  // without wrapping the whole app in a placeholder <TuringProvider>.
  const ctx = useOptionalTuringContext();
  const {
    conversationId: explicitConversationId,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
    agent,
    pollInterval = 0,
    enabled = true,
    transport = "polling",
    sseDelta = false,
  } = options;
  const agentId = agent?.id;
  if (!ctx && !agentId) {
    throw new Error(
      "useTuringSlots in site mode requires a <TuringProvider> ancestor. " +
      "Either mount the provider or pass `options.agent` to use agent mode.",
    );
  }
  const siteName = ctx?.config.site;
  // SSE only works on site mode for now — agent mode falls back to polling
  // even when the caller asks for "sse" so behavior stays predictable.
  const useSse = transport === "sse" && !agentId;

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

  // Local state so the SSE branch can toggle off after a fatal error,
  // forcing the polling fallback to take over.
  const [sseDisabled, setSseDisabled] = useState(false);
  const sseActive = useSse && !sseDisabled;

  useEffect(() => {
    if (!enabled || !conversationId || sseActive) {
      return;
    }

    let alive = true;
    setStatus("loading");
    setError(null);

    const fetcher: Promise<TurChatSessionSlots> = agentId
      ? fetchAgentChatSlots(agentId, conversationId)
      : fetchSiteChatSlots(siteName as string, conversationId);

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
  }, [enabled, conversationId, agentId, siteName, tick, sseActive]);

  // SSE branch: subscribe via the module-level multiplexer keyed on
  // (baseURL, site, conversationId). Multiple useTuringSlots instances
  // on the same page share ONE EventSource — was N before. The server
  // pushes the full merged map so we never have to diff. The first
  // onMessage after subscribe is either the live initial snapshot (first
  // subscriber for this conversation) or the cached snapshot replayed
  // via queueMicrotask (late subscriber joining an existing channel).
  useEffect(() => {
    if (!enabled || !sseActive || !conversationId) return;
    if (globalThis.EventSource === undefined) {
      // No browser support (rare — IE only). Fall back to polling silently.
      setSseDisabled(true);
      return;
    }
    setStatus("loading");
    setError(null);
    const baseURL = axios.defaults.baseURL ?? "";
    const subscription = subscribeSlotsSse(baseURL, siteName as string, conversationId, {
      onMessage: (payload) => {
        lastFetchedConversationRef.current = conversationId;
        setSlots(Object.freeze({ ...(payload?.slots ?? {}) }));
        setStatus("success");
      },
      onClosed: () => {
        // Server severed the connection (readyState=CLOSED). Fall back
        // to polling — the polling effect re-runs when sseActive flips.
        setSseDisabled(true);
        setError("SSE channel closed; falling back to polling.");
      },
    }, { delta: sseDelta });
    return () => {
      subscription.close();
    };
  }, [enabled, sseActive, conversationId, siteName, sseDelta]);

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

  // Polling tick: drives BOTH the slot-fetcher effect (transport=polling)
  // AND the cookie-sync effect (which depends on `tick`). The latter is
  // load-bearing even when SSE is the chosen transport — without it, a
  // hook that mounted BEFORE the chat's first turn (header / FlowOutputs
  // render with the page; visitor types later) never detects the
  // TUR_SESSION cookie being minted, leaves `conversationId` stuck at
  // null, and the SSE subscription effect early-returns forever.
  // Symptom: slot-driven panels only appear after a manual refresh.
  //
  // Guard logic:
  //  • SSE off → always poll (existing behavior, drives the fetcher).
  //  • SSE on + conversationId null → poll just to bump tick so the
  //    cookie-sync effect re-runs and catches the freshly-minted cookie.
  //  • SSE on + conversationId set → stop polling; SSE provides live
  //    updates and we don't need to bump tick anymore.
  useEffect(() => {
    if (!enabled || pollInterval <= 0) {
      return;
    }
    if (sseActive && conversationId) {
      return;
    }
    const handle = globalThis.setInterval(() => {
      setTick((t) => t + 1);
    }, pollInterval);
    return () => {
      globalThis.clearInterval(handle);
    };
  }, [enabled, pollInterval, sseActive, conversationId]);

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
