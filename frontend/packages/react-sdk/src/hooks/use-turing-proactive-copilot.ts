import { useCallback, useEffect, useState } from "react";
import axios from "axios";
import { readTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../core/session";
import { useOptionalTuringContext } from "../core/use-turing-context";

/**
 * T445 / §XXIII.4 — ambient / proactive copilot.
 *
 * <p>Subscribes to the server's proactive stream
 * ({@code GET /sn/{site}/chat/proactive/stream}) and surfaces the next unsolicited
 * offer the agent makes after watching the conversation's ambient interaction
 * signals (slots named {@code signal.<kind>} the host writes from clicks/dwell).
 * The stream is empty unless the agent has {@code proactiveEnabled}; the server
 * throttles to one offer per window, so this hook holds at most one pending
 * suggestion at a time. Call {@link UseTuringProactiveCopilotReturn.dismiss} to
 * clear it (e.g. after the user accepts or ignores it).
 *
 * <pre>
 *   const { suggestion, dismiss } = useTuringProactiveCopilot();
 *   if (suggestion) return (
 *     &lt;Banner onAccept={() =&gt; { chat.send(suggestion.suggestedPrompt); dismiss(); }}
 *             onClose={dismiss}&gt;{suggestion.message}&lt;/Banner&gt;
 *   );
 * </pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export interface TuringProactiveSuggestion {
  conversationId: string;
  signal: string;
  count: number;
  message: string;
  suggestedPrompt: string;
}

export interface UseTuringProactiveCopilotOptions {
  /** Conversation id; falls back to the {@code TUR_SESSION} cookie when omitted. */
  readonly conversationId?: string;
  /** Cookie name to read the conversation id from. Defaults to the SDK default. */
  readonly sessionCookieName?: string;
  /** Disable the subscription (e.g. while a panel is closed). Defaults to true. */
  readonly enabled?: boolean;
}

export interface UseTuringProactiveCopilotReturn {
  /** The current pending proactive offer, or null. */
  suggestion: TuringProactiveSuggestion | null;
  /** Clear the current suggestion. */
  dismiss: () => void;
}

export function useTuringProactiveCopilot(
  options: UseTuringProactiveCopilotOptions = {},
): UseTuringProactiveCopilotReturn {
  const ctx = useOptionalTuringContext();
  const {
    conversationId: explicitConversationId,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
    enabled = true,
  } = options;
  if (!ctx) {
    throw new Error("useTuringProactiveCopilot requires a <TuringProvider> ancestor.");
  }
  const siteName = ctx.config.site;
  const conversationId =
    explicitConversationId ?? readTurSession(sessionCookieName) ?? null;

  const [suggestion, setSuggestion] = useState<TuringProactiveSuggestion | null>(null);

  const dismiss = useCallback(() => setSuggestion(null), []);

  useEffect(() => {
    if (!enabled || !conversationId || !siteName) return;
    if (globalThis.EventSource === undefined) return;
    const baseURL = axios.defaults.baseURL ?? "";
    const url = `${baseURL}/sn/${siteName}/chat/proactive/stream?conversationId=${encodeURIComponent(
      conversationId,
    )}`;
    const es = new globalThis.EventSource(url, { withCredentials: true });
    const onSuggestion = (event: MessageEvent) => {
      try {
        const parsed = JSON.parse(event.data) as TuringProactiveSuggestion;
        if (parsed && parsed.message) setSuggestion(parsed);
      } catch {
        // Ignore malformed events (heartbeats are SSE comments, never delivered here).
      }
    };
    es.addEventListener("proactive-suggestion", onSuggestion as EventListener);
    return () => {
      es.removeEventListener("proactive-suggestion", onSuggestion as EventListener);
      es.close();
    };
  }, [enabled, conversationId, siteName]);

  return { suggestion, dismiss };
}
