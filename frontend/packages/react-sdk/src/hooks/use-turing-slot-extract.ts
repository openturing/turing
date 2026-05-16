import { useCallback, useState } from "react";
import {
  postSiteSlotExtract,
  type TurChatSlotExtractResponse,
} from "../core/api";
import { readTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../core/session";
import { useTuringContext } from "../core/use-turing-context";

export type SlotExtractStatus = "idle" | "uploading" | "success" | "error";

export interface UseTuringSlotExtractOptions {
  /** Forces a specific conversation id; otherwise read from {@code TUR_SESSION}. */
  readonly conversationId?: string;
  readonly sessionCookieName?: string;
  /**
   * Specific slot names to target. When omitted, the server uses the
   * agent's full slot catalog. Pass a tight list to keep LLM cost +
   * latency low for known use cases (e.g. CV upload → 4 slots only).
   */
  readonly slotNames?: ReadonlyArray<string>;
}

export interface UseTuringSlotExtractReturn {
  readonly extract: (file: File) => Promise<TurChatSlotExtractResponse>;
  readonly status: SlotExtractStatus;
  readonly error: string | null;
  /** Last successful extraction's result, kept for the UI to render. */
  readonly lastResult: TurChatSlotExtractResponse | null;
}

/**
 * Imperatively uploads a document and extracts chat-flow slot values via
 * the agent's LLM. Pairs with {@link useTuringSlots} — the extracted
 * fields land as actual slot writes, so subscribers refresh
 * automatically over SSE.
 *
 * @example
 * ```tsx
 * const { extract, status } = useTuringSlotExtract({
 *   slotNames: ["name", "cargo_atual", "objetivo", "area"],
 * });
 *
 * <input
 *   type="file"
 *   accept=".pdf,.docx,.txt"
 *   onChange={async (e) => {
 *     const file = e.target.files?.[0];
 *     if (!file) return;
 *     const result = await extract(file);
 *     console.log("Skipped %d questions", result.slotsWritten);
 *   }}
 * />
 * ```
 *
 * @since 2026.2.7
 */
export function useTuringSlotExtract(
  options: UseTuringSlotExtractOptions = {},
): UseTuringSlotExtractReturn {
  const { config } = useTuringContext();
  const {
    conversationId: explicitConversationId,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
    slotNames,
  } = options;

  const [status, setStatus] = useState<SlotExtractStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<TurChatSlotExtractResponse | null>(null);

  const extract = useCallback(
    async (file: File): Promise<TurChatSlotExtractResponse> => {
      const conversationId =
        explicitConversationId ?? readTurSession(sessionCookieName);
      if (!conversationId) {
        const msg = "No active conversation — send a chat message first.";
        setError(msg);
        setStatus("error");
        throw new Error(msg);
      }
      setStatus("uploading");
      setError(null);
      try {
        const result = await postSiteSlotExtract(
          config.site,
          file,
          conversationId,
          slotNames,
        );
        // The server may short-circuit and surface a soft error in the
        // response body (e.g. "No AI agent configured"). Treat that as
        // a failure so the caller can render a user-facing message.
        if (result.error) {
          setError(result.error);
          setStatus("error");
          return result;
        }
        setLastResult(result);
        setStatus("success");
        return result;
      } catch (err) {
        const msg = err instanceof Error ? err.message : "Slot extraction failed";
        setError(msg);
        setStatus("error");
        throw err;
      }
    },
    [config.site, explicitConversationId, sessionCookieName, slotNames],
  );

  return { extract, status, error, lastResult };
}
