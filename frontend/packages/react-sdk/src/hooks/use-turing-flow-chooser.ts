import { useCallback, useEffect, useRef, useState } from "react";
import {
  postSiteFlowSelect,
  type TurChatFlowSelectResponse,
} from "../core/api";
import {
  getOrCreateTurSession,
  readTurSession,
  TUR_SESSION_DEFAULT_COOKIE_NAME,
} from "../core/session";
import { useTuringContext } from "../core/use-turing-context";

export type FlowChooserStatus = "idle" | "selecting" | "success" | "error";

export interface UseTuringFlowChooserOptions {
  /**
   * Forces the conversation id to pin against. When omitted, the hook
   * reads the {@code TUR_SESSION} cookie set by {@link useTuringChat} —
   * matching the conversation the visitor is (or will be) chatting on.
   */
  readonly conversationId?: string;
  /**
   * Cookie name used to read/mint the conversation id when
   * {@code conversationId} is not passed. Defaults to
   * {@link TUR_SESSION_DEFAULT_COOKIE_NAME}.
   */
  readonly sessionCookieName?: string;
  /**
   * Default flow to pin — the UUID or case-insensitive name. Used by
   * {@link UseTuringFlowChooserReturn.chooseFlow} when called with no
   * argument, and by the {@link autoSelectFromParam} auto-pin when the
   * URL carries no value.
   */
  readonly flow?: string;
  /**
   * When true (default), mints a {@code TUR_SESSION} cookie if none
   * exists yet so the pin works on a deep-link landing — before the
   * visitor types anything. The flow-select endpoint pre-creates the
   * chosen flow's state for that fresh conversation, and the chat hook
   * later reuses the same cookie, so the first turn already runs the
   * pinned persona. Set to {@code false} to require an existing
   * conversation (mirrors {@link useTuringSlotWriter}'s read-only stance).
   */
  readonly mintSession?: boolean;
  /**
   * Opt-in deep-link auto-pin: the name of a URL query param to read on
   * mount (e.g. {@code "flow"} → reads {@code ?flow=in-company}). When the
   * param is present, the hook pins that flow once automatically; when it
   * is absent but {@link flow} is set, the default is pinned instead.
   * Omit (or {@code false}) to disable auto-pinning entirely — the caller
   * then drives every pin through {@link UseTuringFlowChooserReturn.chooseFlow}.
   */
  readonly autoSelectFromParam?: string | false;
}

export interface UseTuringFlowChooserReturn {
  /**
   * Pins a specific flow by UUID or case-insensitive name, overriding the
   * LLM trigger router for the rest of the conversation. Falls back to the
   * hook-level {@link UseTuringFlowChooserOptions.flow} when called without
   * an argument. Resolves with the server's response; rejects on
   * network/HTTP error so the caller can {@code try/catch} an awaited call.
   */
  readonly chooseFlow: (flow?: string) => Promise<TurChatFlowSelectResponse>;
  /** Status of the most recent {@link chooseFlow} call. */
  readonly status: FlowChooserStatus;
  /** Error message from the last failed (or unsuccessful) pin. */
  readonly error: string | null;
  /** Most recent server response; preserved across calls for UI feedback. */
  readonly lastResult: TurChatFlowSelectResponse | null;
  /** Flow id the engine locked onto in the last successful pin. */
  readonly pinnedFlowId: string | null;
  /** Flow name the engine locked onto in the last successful pin. */
  readonly pinnedFlowName: string | null;
}

function readUrlParam(name: string): string | null {
  if (typeof window === "undefined") return null;
  const value = new URLSearchParams(window.location.search).get(name);
  return value && value.trim().length > 0 ? value : null;
}

/**
 * Imperatively forces a specific chat-flow for the visitor's conversation,
 * overriding the LLM trigger router — the SDK pairing for the T92
 * {@code POST /chat/flow-select} endpoint. Use it for deep links
 * (`?flow=in-company` lands directly on the B2B persona) or for UI that
 * lets the visitor pick a track ("Talk to sales" vs "Browse courses")
 * before the conversation starts.
 *
 * The pin is sticky: once set, subsequent {@link useTuringChat} turns stay
 * on the chosen flow with no extra plumbing. Pairs with T93 cross-flow
 * slot inheritance — slots already captured carry into the pinned flow.
 *
 * @example Deep-link auto-pin
 * ```tsx
 * // URL: https://site/?flow=in-company
 * useTuringFlowChooser({ autoSelectFromParam: "flow" });
 * ```
 *
 * @example Explicit picker
 * ```tsx
 * const { chooseFlow, status } = useTuringFlowChooser();
 *
 * <button onClick={() => chooseFlow("in-company")} disabled={status === "selecting"}>
 *   Falar com Vendas (B2B)
 * </button>
 * ```
 *
 * @since 2026.3.1
 */
export function useTuringFlowChooser(
  options: UseTuringFlowChooserOptions = {},
): UseTuringFlowChooserReturn {
  const { config } = useTuringContext();
  const {
    conversationId: explicitConversationId,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
    flow: defaultFlow,
    mintSession = true,
    autoSelectFromParam,
  } = options;

  const [status, setStatus] = useState<FlowChooserStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<TurChatFlowSelectResponse | null>(null);

  const chooseFlow = useCallback(
    async (flow?: string): Promise<TurChatFlowSelectResponse> => {
      const target = flow ?? defaultFlow;
      if (!target) {
        const msg = "No flow to pin — pass a flow id/name or set options.flow.";
        setError(msg);
        setStatus("error");
        throw new Error(msg);
      }
      // Resolve the conversation id at call time, not at mount: the cookie
      // may be minted on the first chat turn. When mintSession is on we mint
      // proactively so deep-link pins work before the visitor types — the
      // chat hook later reuses the same cookie.
      const conversationId = explicitConversationId
        ?? (mintSession
          ? getOrCreateTurSession({ name: sessionCookieName })
          : readTurSession(sessionCookieName));
      if (!conversationId) {
        const msg = "No active conversation — send a chat message first.";
        setError(msg);
        setStatus("error");
        throw new Error(msg);
      }
      setStatus("selecting");
      setError(null);
      try {
        const result = await postSiteFlowSelect(config.site, conversationId, target);
        setLastResult(result);
        if (!result.success) {
          setError(result.reason ?? "Flow could not be pinned.");
          setStatus("error");
          return result;
        }
        setStatus("success");
        return result;
      } catch (err) {
        const msg = err instanceof Error ? err.message : "Failed to pin flow";
        setError(msg);
        setStatus("error");
        throw err;
      }
    },
    [config.site, explicitConversationId, sessionCookieName, defaultFlow, mintSession],
  );

  // Opt-in deep-link auto-pin. Runs once: a deep-link param landing should
  // pin a single time, not re-fire on every re-render. Re-arms only when the
  // param name, the resolved target, or the site changes.
  const autoPinnedRef = useRef(false);
  useEffect(() => {
    if (autoSelectFromParam === undefined || autoSelectFromParam === false) return;
    const target = readUrlParam(autoSelectFromParam) ?? defaultFlow;
    if (!target) return;
    if (autoPinnedRef.current) return;
    autoPinnedRef.current = true;
    void chooseFlow(target).catch(() => {
      // Swallow: state already reflects the failure via status/error. The
      // promise rejection is for imperative callers, not the auto path.
    });
  }, [autoSelectFromParam, defaultFlow, chooseFlow]);

  return {
    chooseFlow,
    status,
    error,
    lastResult,
    pinnedFlowId: lastResult?.pinnedFlowId ?? null,
    pinnedFlowName: lastResult?.pinnedFlowName ?? null,
  };
}
