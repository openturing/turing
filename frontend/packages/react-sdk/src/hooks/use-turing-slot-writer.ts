import { useCallback, useState } from "react";
import { postSiteChatSlot, type TurChatSlotWriteResponse } from "../core/api";
import { readTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../core/session";
import { useTuringContext } from "../core/use-turing-context";

export type SlotWriteStatus = "idle" | "writing" | "success" | "error";

export interface UseTuringSlotWriterOptions {
  /**
   * Forces the conversation id to write against. When omitted, the hook
   * reads the {@code TUR_SESSION} cookie set by {@link useTuringChat} —
   * matching the conversation the visitor is currently chatting on.
   */
  readonly conversationId?: string;
  /**
   * Cookie name used to read the conversation id when {@code conversationId}
   * is not passed. Defaults to {@link TUR_SESSION_DEFAULT_COOKIE_NAME}.
   */
  readonly sessionCookieName?: string;
}

export interface UseTuringSlotWriterReturn {
  /**
   * Writes a single slot. Returns the server's response (with
   * {@code updatedStates} count). Rejects on network/HTTP error so the
   * caller can {@code try/catch} around an awaited call.
   */
  readonly write: (name: string, value: string) => Promise<TurChatSlotWriteResponse>;
  /** Status of the most recent {@link write} call. */
  readonly status: SlotWriteStatus;
  /** Error message from the last failed call. */
  readonly error: string | null;
  /** Number of state rows the last successful write updated. */
  readonly lastUpdatedStates: number;
}

/**
 * Imperatively writes a slot on the current chat conversation — the inverse
 * of {@link useTuringSlots}. Use to skip a chat-flow question when the user
 * picks an option via UI (button, dropdown, autocomplete) instead of typing
 * it into the chat: the next assistant turn sees the slot already filled
 * and the flow advances past the matching {@code slot} node.
 *
 * The hook is fire-by-call (no auto-trigger on mount). Returns a stable
 * {@link UseTuringSlotWriterReturn.write} function so it can be passed to
 * event handlers as-is.
 *
 * @example
 * ```tsx
 * const { write, status } = useTuringSlotWriter();
 *
 * <button onClick={() => write("area_interesse", "Liderança")}>
 *   Liderança e Gestão
 * </button>
 * ```
 *
 * @since 2026.2.7
 */
export function useTuringSlotWriter(
  options: UseTuringSlotWriterOptions = {},
): UseTuringSlotWriterReturn {
  const { config } = useTuringContext();
  const {
    conversationId: explicitConversationId,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
  } = options;

  const [status, setStatus] = useState<SlotWriteStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [lastUpdatedStates, setLastUpdatedStates] = useState(0);

  const write = useCallback(
    async (name: string, value: string): Promise<TurChatSlotWriteResponse> => {
      // Resolve conversation id at call time, not at mount: the cookie is
      // minted on the first chat turn, which may happen after this hook
      // mounts. Mirrors the lazy resolution in `useTuringSlots`.
      const conversationId =
        explicitConversationId ?? readTurSession(sessionCookieName);
      if (!conversationId) {
        const err = "No active conversation — send a chat message first.";
        setError(err);
        setStatus("error");
        throw new Error(err);
      }
      setStatus("writing");
      setError(null);
      try {
        const res = await postSiteChatSlot(config.site, conversationId, name, value);
        setLastUpdatedStates(res.updatedStates);
        setStatus("success");
        return res;
      } catch (err) {
        const msg = err instanceof Error ? err.message : "Failed to write slot";
        setError(msg);
        setStatus("error");
        throw err;
      }
    },
    [config.site, explicitConversationId, sessionCookieName],
  );

  return { write, status, error, lastUpdatedStates };
}
