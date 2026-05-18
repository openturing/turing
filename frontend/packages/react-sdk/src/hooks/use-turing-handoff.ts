import { useCallback, useState } from "react";
import {
  postSiteHandoff,
  type TurChatHandoffChannel,
  type TurChatHandoffResponse,
} from "../core/api";
import { readTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../core/session";
import { useTuringContext } from "../core/use-turing-context";

export type HandoffStatus = "idle" | "building" | "success" | "error";

export interface UseTuringHandoffOptions {
  readonly conversationId?: string;
  readonly sessionCookieName?: string;
  /** Default channel — overridable per {@link UseTuringHandoffReturn.handoff} call. */
  readonly channel?: TurChatHandoffChannel;
  /**
   * Default destination (phone digits / email / phone for SMS). The hook
   * call can override but most apps want a single configured destination,
   * so threading it through options keeps the call site minimal.
   */
  readonly destination?: string;
  /**
   * Default intro text rendered above the slot transcript in the link
   * body. Localize per app (e.g. "Olá! Vim do site de Education.").
   */
  readonly intro?: string;
  /**
   * Default slot whitelist for the transcript. When omitted, the server
   * picks a safe default (human-readable slots, skips internal flags).
   */
  readonly slotsToInclude?: ReadonlyArray<string>;
  /**
   * When true (default), automatically opens the resulting URL via
   * {@code window.open(url, "_blank", "noopener,noreferrer")}. Set to
   * false to take manual control of when the redirect happens (e.g.
   * confirmation modal showing the transcript before launch).
   */
  readonly autoOpen?: boolean;
}

export interface UseTuringHandoffReturn {
  /**
   * Builds the handoff URL and (when {@code autoOpen=true}) opens it.
   * Options passed here override the hook-level defaults — useful when
   * a single page surfaces multiple channels (WhatsApp + Email buttons).
   */
  readonly handoff: (
    overrides?: Partial<
      Pick<UseTuringHandoffOptions, "channel" | "destination" | "intro" | "slotsToInclude">
    >,
  ) => Promise<TurChatHandoffResponse>;
  readonly status: HandoffStatus;
  readonly error: string | null;
  /** Most recent successful response; preserved across calls for UI preview. */
  readonly lastResult: TurChatHandoffResponse | null;
}

/**
 * Imperatively launches a channel-specific handoff (WhatsApp / email /
 * SMS) carrying the conversation context. The server composes the deep
 * link from the captured slots; the hook either redirects to it or
 * returns it for the caller to handle.
 *
 * @example
 * ```tsx
 * const { handoff, status } = useTuringHandoff({
 *   channel: "whatsapp",
 *   destination: "5511999999999",
 *   intro: "Olá! Vim do site de Executive Education.",
 *   slotsToInclude: ["name", "cargo_atual", "area_label", "objetivo"],
 * });
 *
 * <button onClick={() => handoff()} disabled={status === "building"}>
 *   Continuar no WhatsApp
 * </button>
 * ```
 *
 * @since 2026.2.7
 */
export function useTuringHandoff(
  options: UseTuringHandoffOptions = {},
): UseTuringHandoffReturn {
  const { config } = useTuringContext();
  const {
    conversationId: explicitConversationId,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
    channel: defaultChannel = "whatsapp",
    destination: defaultDestination,
    intro: defaultIntro,
    slotsToInclude: defaultSlots,
    autoOpen = true,
  } = options;

  const [status, setStatus] = useState<HandoffStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<TurChatHandoffResponse | null>(null);

  const handoff = useCallback(
    async (
      overrides?: Partial<
        Pick<UseTuringHandoffOptions, "channel" | "destination" | "intro" | "slotsToInclude">
      >,
    ): Promise<TurChatHandoffResponse> => {
      const conversationId =
        explicitConversationId ?? readTurSession(sessionCookieName);
      if (!conversationId) {
        const msg = "No active conversation — send a chat message first.";
        setError(msg);
        setStatus("error");
        throw new Error(msg);
      }
      const channel = overrides?.channel ?? defaultChannel;
      const destination = overrides?.destination ?? defaultDestination;
      if (!destination) {
        const msg = "Handoff destination is required (phone / email).";
        setError(msg);
        setStatus("error");
        throw new Error(msg);
      }
      setStatus("building");
      setError(null);
      try {
        const result = await postSiteHandoff(config.site, {
          conversationId,
          channel,
          destination,
          intro: overrides?.intro ?? defaultIntro,
          slotsToInclude: overrides?.slotsToInclude ?? defaultSlots,
        });
        if (result.error || !result.url) {
          setError(result.error ?? "Handoff build returned no URL");
          setStatus("error");
          return result;
        }
        setLastResult(result);
        setStatus("success");
        if (autoOpen) {
          // noopener,noreferrer hardens against opener-tab takeover
          // attacks via window.opener — recommended for any link opened
          // to an external destination.
          globalThis.open(result.url, "_blank", "noopener,noreferrer");
        }
        return result;
      } catch (err) {
        const msg = err instanceof Error ? err.message : "Handoff failed";
        setError(msg);
        setStatus("error");
        throw err;
      }
    },
    [
      config.site,
      explicitConversationId,
      sessionCookieName,
      defaultChannel,
      defaultDestination,
      defaultIntro,
      defaultSlots,
      autoOpen,
    ],
  );

  return { handoff, status, error, lastResult };
}
