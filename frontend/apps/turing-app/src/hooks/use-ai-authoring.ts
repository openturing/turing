import { useCallback, useRef, useState } from "react";

import type {
  AiAuthoringEndpoint,
  AiAuthoringMessage,
} from "@/models/ai-authoring/ai-authoring.model";

/**
 * Reusable AI Authoring chat hook. Owns the conversation history, the
 * request lifecycle, and the bridging back to the consumer's form.
 *
 * Each entity-specific page wires it up by passing:
 * - {@code endpoint} — service call that posts the chat turn to the backend
 * - {@code getCurrentState} — reads the form snapshot to send each turn
 * - {@code applyState} — writes the LLM-returned snapshot back to the form
 *
 * The form stays the source of truth between turns; the LLM only sees what
 * it's told (current state) and only mutates what it returns.
 *
 * @since 2026.2.5
 */

export interface UseAiAuthoringOptions<T> {
  /** Service call that dispatches one turn to the backend. */
  endpoint: AiAuthoringEndpoint<T>;
  /** Reads the live form snapshot to send to the LLM each turn. */
  getCurrentState: () => T | null;
  /** Writes the LLM-returned snapshot into the form. */
  applyState: (state: T) => void;
}

export interface UseAiAuthoringReturn {
  messages: AiAuthoringMessage[];
  sending: boolean;
  error: string | null;
  send: (content: string) => Promise<void>;
  reset: () => void;
}

export function useAiAuthoring<T>({
  endpoint,
  getCurrentState,
  applyState,
}: UseAiAuthoringOptions<T>): UseAiAuthoringReturn {
  const [messages, setMessages] = useState<AiAuthoringMessage[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Latch sending state across awaits so concurrent sends are dropped even
  // before React has flushed the state update.
  const inFlight = useRef(false);

  const send = useCallback(
    async (content: string) => {
      const trimmed = content.trim();
      if (!trimmed || inFlight.current) return;
      inFlight.current = true;
      setSending(true);
      setError(null);

      const userMessage: AiAuthoringMessage = { role: "user", content: trimmed };
      const nextHistory = [...messages, userMessage];
      setMessages(nextHistory);

      try {
        const response = await endpoint({
          messages: nextHistory,
          currentState: getCurrentState(),
        });
        if (response?.state) {
          applyState(response.state);
        }
        const assistantMessage: AiAuthoringMessage = {
          role: "assistant",
          content: response?.message ?? "",
        };
        setMessages((prev) => [...prev, assistantMessage]);
      } catch (err) {
        console.error("AI authoring chat error", err);
        setError(err instanceof Error ? err.message : String(err));
      } finally {
        setSending(false);
        inFlight.current = false;
      }
    },
    [messages, endpoint, getCurrentState, applyState],
  );

  const reset = useCallback(() => {
    setMessages([]);
    setError(null);
  }, []);

  return { messages, sending, error, send, reset };
}
