import type { TuringClient } from "../client";
import {
  fetchAgentChatSlots,
  fetchSiteChatSlots,
  type TurChatSessionSlots,
} from "../api";
import { readTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../session";
import { subscribeSlotsSse, type SlotsSseSubscription } from "../slots-sse";
import { createStore, type Store } from "../store";

/**
 * Vanilla slots controller — the framework-agnostic equivalent of the React
 * SDK's `useTuringSlots`. Reads the slots captured during the visitor's chat
 * session and exposes them as a flat map over an observable {@link Store}.
 * Pairs with {@link createChatController}: both key off the same
 * {@code TUR_SESSION} cookie.
 *
 * <p>Transports: {@code "polling"} (default) refetches on a timer;
 * {@code "sse"} (site mode only) opens a shared EventSource via the
 * {@link subscribeSlotsSse} multiplexer and falls back to polling on a fatal
 * channel close.
 *
 * @example
 * ```js
 * const slots = createSlotsController(client, { site: "my-site" }, { pollInterval: 3000 });
 * slots.subscribe((s) => { if (s.slots.email) form.email.value = s.slots.email; });
 * ```
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

export type SlotsStatus = "idle" | "loading" | "success" | "error";

export interface SlotsControllerScope {
  /** SN site name. Required unless {@code agentId} is set. */
  readonly site?: string;
  /** Opt into agent mode (reads `GET /v2/ai-agent/{id}/chat-slots`). */
  readonly agentId?: string;
}

export interface SlotsControllerOptions {
  /** Forces the conversation id. When omitted, read from the session cookie. */
  readonly conversationId?: string;
  /** Cookie name used to read the conversation id. Defaults to {@code TUR_SESSION}. */
  readonly sessionCookieName?: string;
  /** Polling interval in ms. {@code 0} (default) disables the timer. */
  readonly pollInterval?: number;
  /** Disable the controller entirely. Defaults to {@code true}. */
  readonly enabled?: boolean;
  /** Transport for updates. {@code "sse"} is site-mode only. */
  readonly transport?: "polling" | "sse";
  /**
   * When {@code transport: "sse"}, consume the T63 delta stream
   * ({@code /chat/slots/stream/delta}) instead of full snapshots. Reconstructed
   * transparently — the controller's state is identical, only the wire shrinks
   * (~10× fewer bytes on conversations with many slots). Defaults to
   * {@code false}. @since 2026.3.1
   */
  readonly sseDelta?: boolean;
}

export interface SlotsControllerState {
  slots: Readonly<Record<string, string>>;
  conversationId: string | null;
  status: SlotsStatus;
  error: string | null;
}

export interface SlotsController extends Store<SlotsControllerState> {
  /** Force a refetch / re-resolve of the conversation id right now. */
  refresh(): void;
  /** Stop timers and close any SSE subscription. */
  destroy(): void;
}

const EMPTY_SLOTS: Readonly<Record<string, string>> = Object.freeze({});

export function createSlotsController(
  client: TuringClient,
  scope: SlotsControllerScope,
  options: SlotsControllerOptions = {},
): SlotsController {
  const {
    conversationId: explicitConversationId,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
    pollInterval = 0,
    enabled = true,
    transport = "polling",
    sseDelta = false,
  } = options;

  const { site, agentId } = scope;
  if (!site && !agentId) {
    throw new Error("createSlotsController requires `scope.site` or `scope.agentId`.");
  }
  // SSE is site-mode only — agent mode always polls.
  const useSse =
    transport === "sse" && !agentId && typeof globalThis.EventSource !== "undefined";

  const store = createStore<SlotsControllerState>({
    slots: EMPTY_SLOTS,
    conversationId: explicitConversationId ?? readTurSession(sessionCookieName),
    status: "idle",
    error: null,
  });

  let lastFetchedConversation: string | null = null;
  let sseDisabled = false;
  let subscription: SlotsSseSubscription | null = null;
  let pollTimer: ReturnType<typeof setInterval> | undefined;
  let destroyed = false;

  function resolveConversationId(): string | null {
    return explicitConversationId ?? readTurSession(sessionCookieName);
  }

  function applySnapshot(payload: TurChatSessionSlots | null, forConversation: string): void {
    lastFetchedConversation = forConversation;
    store.setState({ slots: Object.freeze({ ...(payload?.slots ?? {}) }), status: "success" });
  }

  function pollOnce(conversationId: string): void {
    store.setState({ status: "loading", error: null });
    const fetcher = agentId
      ? fetchAgentChatSlots(client, agentId, conversationId)
      : fetchSiteChatSlots(client, site as string, conversationId);
    fetcher
      .then((res) => {
        if (destroyed || store.getState().conversationId !== conversationId) return;
        applySnapshot(res, conversationId);
      })
      .catch((err) => {
        if (destroyed || store.getState().conversationId !== conversationId) return;
        store.setState({
          error: err instanceof Error ? err.message : "Failed to load chat slots",
          status: "error",
        });
      });
  }

  function openSse(conversationId: string): void {
    store.setState({ status: "loading", error: null });
    subscription = subscribeSlotsSse(client.baseURL, site as string, conversationId, {
      onMessage: (payload) => {
        if (destroyed) return;
        applySnapshot(payload, conversationId);
      },
      onClosed: () => {
        // Server severed the connection — fall back to polling.
        sseDisabled = true;
        subscription = null;
        store.setState({ error: "SSE channel closed; falling back to polling." });
        sync();
      },
    }, { delta: sseDelta });
  }

  /**
   * Reconciles transport + conversation id with current state. Called on
   * construction, on every poll tick, and on SSE fallback. Idempotent.
   */
  function sync(): void {
    if (destroyed || !enabled) return;

    const next = resolveConversationId();
    if (next !== store.getState().conversationId) {
      // Conversation changed (e.g. cookie minted after first chat turn, or a
      // login reset). Drop stale slots and tear down any open SSE channel.
      if (lastFetchedConversation && lastFetchedConversation !== next) {
        lastFetchedConversation = null;
        store.setState({ slots: EMPTY_SLOTS });
      }
      subscription?.close();
      subscription = null;
      store.setState({ conversationId: next });
    }

    const conversationId = store.getState().conversationId;
    if (!conversationId) return;

    const sseActive = useSse && !sseDisabled;
    if (sseActive) {
      if (!subscription) openSse(conversationId);
      return;
    }
    pollOnce(conversationId);
  }

  // Polling timer. When SSE is the active transport AND we already have a
  // conversation id, the timer still runs only to re-resolve the cookie when
  // the id is still null (mirrors the React hook's tick rationale).
  if (enabled && pollInterval > 0) {
    pollTimer = globalThis.setInterval(() => {
      const sseActive = useSse && !sseDisabled;
      if (sseActive && store.getState().conversationId) return;
      sync();
    }, pollInterval);
  }

  // Kick off immediately.
  sync();

  function refresh(): void {
    sync();
  }

  function destroy(): void {
    destroyed = true;
    if (pollTimer) globalThis.clearInterval(pollTimer);
    subscription?.close();
    subscription = null;
  }

  return {
    getState: store.getState,
    setState: store.setState,
    subscribe: store.subscribe,
    refresh,
    destroy,
  };
}
