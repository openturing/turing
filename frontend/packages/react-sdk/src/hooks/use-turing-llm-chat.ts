import { useCallback } from "react";
import { postLlmChat } from "../core/api";
import type { TurChatConversationMessage } from "../core/types";
import {
  newId,
  useStreamingChatCore,
  type ChatMessage,
  type ChatStatus,
} from "./streaming-chat-core";

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

/**
 * Plain-LLM chat hook backed by {@code POST /v2/llm/{id}/chat} — no agent,
 * no site, no chat-flow context. Used by admin console UIs that need a
 * direct "talk to the raw model" channel (sandbox mode, title generator,
 * etc.); the public search chat should stay on {@link useTuringChat}.
 *
 * <p>The streaming/bubble/abort state machine lives in
 * {@link useStreamingChatCore} (T243), shared with {@code useTuringChat};
 * this hook only adds the plain-LLM transport and its file-attachment
 * override.
 *
 * <p>Shape mirrors {@code UseTuringChatReturn} minus the fields that have
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

  const core = useStreamingChatCore({ maxMessages });
  const {
    messages,
    status,
    error,
    messagesRef,
    beginSend,
    commitMessages,
    openAssistantBubble,
    appendToken,
    patchAssistant,
    isStale,
    handleSendError,
    setStatus,
    stop,
    reset,
  } = core;

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

      const { requestId, controller } = beginSend();
      const nextMessages = commitMessages([...messagesRef.current, userMsg]);

      try {
        const wire: TurChatConversationMessage[] = nextMessages.map((m) => ({
          role: m.role,
          content: m.content,
        }));

        const assistantId = openAssistantBubble();

        const res = await postLlmChat(llmInstanceId, wire, {
          onToken: (token) => appendToken(assistantId, requestId, token),
          signal: controller.signal,
          files: overrides?.files,
        });

        if (isStale(requestId)) return;

        patchAssistant(assistantId, (m) => ({
          ...m,
          content: res?.content ?? m.content,
        }));
        setStatus("success");
      } catch (err) {
        handleSendError(err, requestId);
      }
    },
    [
      llmInstanceId,
      beginSend,
      commitMessages,
      messagesRef,
      openAssistantBubble,
      appendToken,
      patchAssistant,
      isStale,
      handleSendError,
      setStatus,
    ],
  );

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
