import { useCallback, useState } from "react";
import { postSiteChatResume, type TurChatResumeResponse } from "../core/api";
import { readTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../core/session";
import { useTuringContext } from "../core/use-turing-context";

export type ResumeStatus = "idle" | "resuming" | "success" | "error";

export interface UseTuringResumeOptions {
  readonly conversationId?: string;
  readonly sessionCookieName?: string;
}

export interface UseTuringResumeReturn {
  /**
   * Resumes the conversation parked at a suspend node, optionally applying
   * {@code slotUpdates} before advancing.
   */
  readonly resume: (
    slotUpdates?: Readonly<Record<string, string>>,
    resumeReason?: string,
  ) => Promise<TurChatResumeResponse>;
  readonly status: ResumeStatus;
  readonly error: string | null;
  /** Most recent successful response. */
  readonly lastResult: TurChatResumeResponse | null;
}

/**
 * T401 — resumes a conversation suspended at a human-approval / wait node
 * ({@code POST /sn/{site}/chat/resume}).
 *
 * @example
 * ```tsx
 * const { resume, status } = useTuringResume();
 * <button onClick={() => resume({ approved: "true" })} disabled={status === "resuming"}>
 *   Approve & continue
 * </button>
 * ```
 *
 * @since 2026.3.4
 */
export function useTuringResume(
  options: UseTuringResumeOptions = {},
): UseTuringResumeReturn {
  const { config } = useTuringContext();
  const {
    conversationId: explicitConversationId,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
  } = options;

  const [status, setStatus] = useState<ResumeStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<TurChatResumeResponse | null>(null);

  const resume = useCallback(
    async (
      slotUpdates?: Readonly<Record<string, string>>,
      resumeReason?: string,
    ): Promise<TurChatResumeResponse> => {
      const conversationId =
        explicitConversationId ?? readTurSession(sessionCookieName);
      if (!conversationId) {
        const msg = "No active conversation — nothing to resume.";
        setError(msg);
        setStatus("error");
        throw new Error(msg);
      }
      setStatus("resuming");
      setError(null);
      try {
        const result = await postSiteChatResume(
          config.site,
          conversationId,
          slotUpdates,
          resumeReason,
        );
        if (result.error) {
          setError(result.error);
          setStatus("error");
          return result;
        }
        setLastResult(result);
        setStatus("success");
        return result;
      } catch (err) {
        const msg = err instanceof Error ? err.message : "Resume failed";
        setError(msg);
        setStatus("error");
        throw err;
      }
    },
    [config.site, explicitConversationId, sessionCookieName],
  );

  return { resume, status, error, lastResult };
}
