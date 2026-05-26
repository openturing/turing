import { useCallback, useRef, useState } from "react";
import { postLlmChat } from "../core/api";
import type { TurChatConversationMessage } from "../core/types";
import type { ChatMessage, ChatStatus } from "./use-turing-chat";

/**
 * Per-call overrides accepted by {@link UseTuringLlmChatReturn#send}. Today
 * only the file attachments matter; structuring it as an options bag keeps
 * the door open for future per-turn flags without another signature change.
 *
 * @since 2026.3.1
 */
export interface LlmSendOverrides {
  /**
   * File attachments forwarded as a multipart {@code files} parts on the
   * underlying {@code POST /v2/llm/{id}/chat} call. Empty / missing keeps
   * the request as plain JSON.
   */
  readonly files?: ReadonlyArray<File>;
}

export interface UseTuringLlmChatOptions {
  /**
   * Identifier of the {@code TurLLMInstance} this hook talks to. Required —
   * the hook has no fallback resolution path (unlike {@code useTuringChat}
   * which can resolve the LLM via the site's agent).
   */
  readonly llmInstanceId: string;
  /**
   * Maximum number of message turns kept in memory. Default {@code 50}.
   */
  readonly maxMessages?: number;
}

export interface UseTuringLlmChatReturn {
  /** Current conversation messages (oldest first). */
  messages: ChatMessage[];
  /** Loading status of the most recent send. */
  status: ChatStatus;
  /** Error message from the last failed send. */
  error: string | null;
  /** Whether a turn is in progress. */
  isStreaming: boolean;
  /**
   * Send a user message. Appends it to the conversation, calls
   * {@code POST /v2/llm/{id}/chat} with the full history, and appends the
   * assistant reply on success. Pass {@code overrides.files} to attach
   * documents via the multipart variant of the endpoint.
   */
  send: (content: string, overrides?: LlmSendOverrides) => Promise<void>;
  /** Abort the in-flight assistant turn (rolls back the pending bubble). */
  stop: () => void;
  /** Clear all messages and reset error state. */
  reset: () => void;
}

const DEFAULT_MAX_MESSAGES = 50;

function newId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function patchMessage(
  messages: ChatMessage[],
  id: string,
  mutator: (msg: ChatMessage) => ChatMessage,
): ChatMessage[] {
  const idx = messages.findIndex((m) => m.id === id);
  if (idx === -1) return messages;
  const updated = [...messages];
  updated[idx] = mutator(updated[idx]);
  return updated;
}

function isAbortError(err: unknown): boolean {
  if (err instanceof DOMException && err.name === "AbortError") return true;
  return err instanceof Error && err.name === "AbortError";
}

/**
 * Plain-LLM chat hook backed by {@code POST /v2/llm/{id}/chat} — no agent,
 * no site, no chat-flow context. Used by admin console UIs that need a
 * direct "talk to the raw model" channel (sandbox mode, title generator,
 * etc.); the public search chat should stay on {@link useTuringChat}.
 *
 * <p>Shape mirrors {@link UseTuringChatReturn} minus the fields that have
 * no meaning here: {@code enabled} (no GenAI-enabled probe), {@code
 * disabledReason} (same), {@code conversationId} (the endpoint is
 * stateless), {@code resetFlow} (no flow runtime to clear).
 *
 * @example
 * ```tsx
 * const { messages, send, isStreaming, reset } = useTuringLlmChat({
 *   llmInstanceId: "gpt-4o-mini",
 * });
 * return (
 *   <>
 *     {messages.map((m) => <Bubble key={m.id} role={m.role}>{m.content}</Bubble>)}
 *     <input onSubmit={(text) => send(text)} disabled={isStreaming} />
 *     <button onClick={reset}>Clear</button>
 *   </>
 * );
 * ```
 *
 * @since 2026.3.1
 */
export function useTuringLlmChat(
  options: UseTuringLlmChatOptions,
): UseTuringLlmChatReturn {
  const { llmInstanceId, maxMessages = DEFAULT_MAX_MESSAGES } = options;

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [status, setStatus] = useState<ChatStatus>("idle");
  const [error, setError] = useState<string | null>(null);

  // Cross-send guards mirror the useTuringChat implementation: a request
  // counter to drop late chunks from a previous send, an aborted flag, and
  // a per-send AbortController so stop()/reset() actually cancel the
  // in-flight fetch instead of just gating state mutations.
  const requestRef = useRef(0);
  const abortedRef = useRef(false);
  const abortControllerRef = useRef<AbortController | null>(null);
  const messagesRef = useRef<ChatMessage[]>(messages);

  const handleSendError = useCallback((err: unknown, requestId: number) => {
    if (requestId !== requestRef.current || abortedRef.current) return;
    if (isAbortError(err)) {
      setStatus("idle");
      return;
    }
    setError(err instanceof Error ? err.message : "Chat request failed");
    setStatus("error");
  }, []);

  const send = useCallback(
    async (rawContent: string, overrides?: LlmSendOverrides) => {
      const content = rawContent.trim();
      if (!content) return;

      const userMsg: ChatMessage = {
        id: newId(),
        role: "user",
        content,
        timestamp: Date.now(),
      };

      const requestId = ++requestRef.current;
      abortedRef.current = false;
      abortControllerRef.current?.abort();
      const controller = new AbortController();
      abortControllerRef.current = controller;
      setError(null);
      setStatus("loading");

      const all = [...messagesRef.current, userMsg];
      const nextMessages =
        all.length > maxMessages ? all.slice(-maxMessages) : all;
      messagesRef.current = nextMessages;
      setMessages(nextMessages);

      try {
        const wire: TurChatConversationMessage[] = nextMessages.map((m) => ({
          role: m.role,
          content: m.content,
        }));

        const assistantId = newId();
        const assistantMsgInitial: ChatMessage = {
          id: assistantId,
          role: "assistant",
          content: "",
          timestamp: Date.now(),
        };
        const streamingHistory = [...messagesRef.current, assistantMsgInitial];
        const initialWithReply =
          streamingHistory.length > maxMessages
            ? streamingHistory.slice(-maxMessages)
            : streamingHistory;
        messagesRef.current = initialWithReply;
        setMessages(initialWithReply);

        const onToken = (token: string) => {
          if (requestId !== requestRef.current || abortedRef.current) return;
          setMessages((prev) =>
            patchMessage(prev, assistantId, (m) => ({
              ...m,
              content: m.content + token,
            })),
          );
        };

        const res = await postLlmChat(llmInstanceId, wire, {
          onToken,
          signal: controller.signal,
          files: overrides?.files,
        });

        if (requestId !== requestRef.current || abortedRef.current) return;

        setMessages((prev) =>
          patchMessage(prev, assistantId, (m) => ({
            ...m,
            content: res?.content ?? m.content,
          })),
        );
        setStatus("success");
      } catch (err) {
        handleSendError(err, requestId);
      }
    },
    [llmInstanceId, maxMessages, handleSendError],
  );

  const stop = useCallback(() => {
    abortedRef.current = true;
    requestRef.current++;
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    setStatus("idle");
  }, []);

  const reset = useCallback(() => {
    abortedRef.current = true;
    requestRef.current++;
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    messagesRef.current = [];
    setMessages([]);
    setError(null);
    setStatus("idle");
  }, []);

  return {
    messages,
    status,
    error,
    isStreaming: status === "loading",
    send,
    stop,
    reset,
  };
}
