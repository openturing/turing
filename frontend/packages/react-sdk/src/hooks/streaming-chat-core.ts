import { useCallback, useRef, useState } from "react";
import type {
  TurChatCitation,
  TurChatConversationMessage,
  TurChatForm,
  TurChatGrounding,
  TurChatSecondOpinion,
  TurChatSource,
  TurChatToolCall,
  TurSearchSuggestions,
} from "../core/types";

/**
 * Streaming-chat lifecycle status, shared by every chat hook. {@code loading}
 * spans the whole turn (request open → tokens streaming → final patch).
 */
export type ChatStatus = "idle" | "loading" | "success" | "error";

export interface ChatMessage extends TurChatConversationMessage {
  /** Stable id for React keys (auto-generated) */
  id: string;
  /** Unix ms when the message was created */
  timestamp: number;
  /**
   * Suggested chip labels for the next user turn (only present on assistant
   * messages, only when the chat-flow engine emitted an `"options"` event
   * for the turn). The host renders them as buttons below the bubble;
   * picking one is equivalent to sending that label as the next user
   * message. Empty array (or missing) means free text only.
   *
   * @since 2026.2.17
   */
  options?: string[];
  /**
   * Native multi-field form attached to this assistant message (T107). The
   * host renders it as a form and calls {@code submitForm} with the collected
   * values. Cleared once submitted.
   *
   * @since 2026.3.1
   */
  form?: TurChatForm;
  /**
   * RAG provenance behind this assistant answer (T292), emitted by the chat
   * pipeline as a `"sources"` SSE event. The host renders source chips below
   * the bubble; each entry maps to a retrieved chunk.
   *
   * @since 2026.3.1
   */
  sources?: TurChatSource[];
  /**
   * Per-sentence Anthropic citations behind this assistant answer (T152/T154),
   * emitted as a `"citations"` SSE event. Each carries the answer-span offsets
   * (`answerStart`/`answerEnd`) so the host can underline the cited claim and
   * show the source quote + deep link in a popover.
   *
   * @since 2026.3.4
   */
  citations?: TurChatCitation[];
  /**
   * Google Search Suggestion chips behind this assistant answer (T490),
   * emitted as a `"searchSuggestions"` SSE event when a Gemini turn used the
   * `google_search` grounding tool. Google's ToS require rendering these chips
   * (`renderedContent`) whenever the grounded answer is shown.
   *
   * @since 2026.3.4
   */
  searchSuggestions?: TurSearchSuggestions;
  /**
   * Live tool-call activity behind this assistant answer (T436), emitted as
   * `"tool_call"` SSE events and merged by `callId`. Populated incrementally
   * while the tool loop runs (so the host can show "calling
   * search_knowledge_base…") and finalized when the turn completes. Present
   * only when the agent has `toolCallEventsEnabled`.
   *
   * @since 2026.3.4
   */
  toolCalls?: TurChatToolCall[];
  /**
   * The OpenAI reasoning model's "Why this answer" summary behind this
   * assistant answer (T178), emitted as a `"reasoning"` SSE event. Present only
   * when the agent opted into the `reasoning-summary` request option and the
   * instance runs a reasoning model. The host renders it as a collapsible panel.
   *
   * @since 2026.3.4
   */
  reasoning?: string;
  /**
   * Answer-grounding guardrail verdict behind this assistant answer (T516),
   * emitted as a `"grounding"` SSE event only when the guardrail is enabled and
   * flagged the answer (ungrounded / unsafe / PII). The host renders a
   * confidence badge beside the bubble; a clean answer leaves this undefined.
   *
   * @since 2026.3.6
   */
  grounding?: TurChatGrounding;
  /**
   * Cross-vendor "second opinion" verdict behind this assistant answer (T522),
   * emitted as a `"secondOpinion"` SSE event when the check is enabled. The host
   * renders an agrees/disagrees confidence badge beside the bubble.
   *
   * @since 2026.3.6
   */
  secondOpinion?: TurChatSecondOpinion;
}

/** Auto-generated, collision-resistant id for React keys and turn tracking. */
export function newId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * Pure update over an immutable message list: locates the message by id and
 * replaces it with the result of {@code mutator}. Returns the same array
 * reference when no match (so React's setState bails on the update).
 */
export function patchMessage(
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

/**
 * `fetch` reports an aborted request as a {@link DOMException} with
 * {@code name === "AbortError"}. Some runtimes (older Node test envs) raise
 * a plain {@code Error}, so we also accept that name as a fallback.
 */
export function isAbortError(err: unknown): boolean {
  if (err instanceof DOMException && err.name === "AbortError") return true;
  return err instanceof Error && err.name === "AbortError";
}

export interface UseStreamingChatCoreOptions {
  /** Maximum number of message turns kept in memory. */
  readonly maxMessages: number;
  /** Seeds the message list at mount (restored transcript, persisted chat). */
  readonly initialMessages?: ChatMessage[];
}

/**
 * Shared streaming-chat state machine extracted from {@code useTuringChat} and
 * {@code useTuringLlmChat} (T243). Owns the message list, the
 * {@link ChatStatus}, the error string, and the cross-send guards (a request
 * counter that drops late chunks from a superseded send, an aborted flag, and
 * a per-send {@link AbortController} so {@code stop()}/{@code reset()} actually
 * cancel the in-flight fetch). Exposes the building blocks each hook composes
 * its own {@code send}/{@code submitForm} from:
 *
 * <ul>
 *   <li>{@link StreamingChatCore.beginSend} — mint a request id, abort the
 *       prior turn, open a fresh controller, flip to {@code loading}.</li>
 *   <li>{@link StreamingChatCore.commitMessages} — cap to {@code maxMessages}
 *       and write both the state and the synchronous {@code messagesRef}.</li>
 *   <li>{@link StreamingChatCore.openAssistantBubble} — append an empty
 *       assistant message and return its id.</li>
 *   <li>{@link StreamingChatCore.appendToken} — the streaming {@code onToken}
 *       patcher (no-op once the turn is stale).</li>
 *   <li>{@link StreamingChatCore.patchAssistant} — patch the assistant bubble
 *       (final content, chip options, form, sources).</li>
 *   <li>{@link StreamingChatCore.isStale} / {@link StreamingChatCore.handleSendError}
 *       / {@link StreamingChatCore.stop} / {@link StreamingChatCore.reset}.</li>
 * </ul>
 *
 * The refs are returned so a host hook can layer extra concerns on top
 * (persistence, controlled conversation ids) without re-deriving the machine.
 *
 * @since 2026.3.1
 */
export function useStreamingChatCore(options: UseStreamingChatCoreOptions) {
  const { maxMessages, initialMessages } = options;

  const [messages, setMessages] = useState<ChatMessage[]>(initialMessages ?? []);
  const [status, setStatus] = useState<ChatStatus>("idle");
  const [error, setError] = useState<string | null>(null);

  const requestRef = useRef(0);
  const abortedRef = useRef(false);
  // Per-send AbortController so `stop()` actually cancels the in-flight fetch
  // (and stops the backend from generating tokens we throw away) instead of
  // just gating state mutations on a counter. Replaced on every `beginSend`.
  const abortControllerRef = useRef<AbortController | null>(null);
  // Synchronous mirror of the latest messages so a host `send` can read the
  // newest history without depending on `messages` (which would re-create the
  // callback every turn) and without the queued setState updater fn.
  const messagesRef = useRef<ChatMessage[]>(messages);

  /** A turn is stale once superseded by a newer send or an explicit abort. */
  const isStale = useCallback(
    (requestId: number) => requestId !== requestRef.current || abortedRef.current,
    [],
  );

  // Drop the result when a newer send is in flight, swallow AbortError as an
  // intentional stop, and surface everything else as an error.
  const handleSendError = useCallback(
    (err: unknown, requestId: number) => {
      if (isStale(requestId)) return;
      if (isAbortError(err)) {
        setStatus("idle");
        return;
      }
      setError(err instanceof Error ? err.message : "Chat request failed");
      setStatus("error");
    },
    [isStale],
  );

  /** Cap to {@code maxMessages} and commit to both the ref and state. */
  const commitMessages = useCallback(
    (next: ChatMessage[]): ChatMessage[] => {
      const capped = next.length > maxMessages ? next.slice(-maxMessages) : next;
      messagesRef.current = capped;
      setMessages(capped);
      return capped;
    },
    [maxMessages],
  );

  /** Mint a request id, abort the prior turn, open a fresh controller, load. */
  const beginSend = useCallback((): { requestId: number; controller: AbortController } => {
    const requestId = ++requestRef.current;
    abortedRef.current = false;
    // Drop any prior in-flight request — `stop()`/`reset()` should have
    // already aborted, but a fast caller can chain `send → send` without
    // touching either. Mint a fresh controller per send.
    abortControllerRef.current?.abort();
    const controller = new AbortController();
    abortControllerRef.current = controller;
    setError(null);
    setStatus("loading");
    return { requestId, controller };
  }, []);

  /** Append an empty assistant bubble (capped) and return its id. */
  const openAssistantBubble = useCallback((): string => {
    const assistantId = newId();
    commitMessages([
      ...messagesRef.current,
      { id: assistantId, role: "assistant", content: "", timestamp: Date.now() },
    ]);
    return assistantId;
  }, [commitMessages]);

  /** Streaming token patcher — appends to the bubble unless the turn is stale. */
  const appendToken = useCallback(
    (assistantId: string, requestId: number, token: string) => {
      if (isStale(requestId)) return;
      setMessages((prev) =>
        patchMessage(prev, assistantId, (m) => ({ ...m, content: m.content + token })),
      );
    },
    [isStale],
  );

  /** Patch the assistant bubble (final content, options, form, sources). */
  const patchAssistant = useCallback(
    (assistantId: string, mutator: (m: ChatMessage) => ChatMessage) => {
      setMessages((prev) => patchMessage(prev, assistantId, mutator));
    },
    [],
  );

  const stop = useCallback(() => {
    abortedRef.current = true;
    requestRef.current++;
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    setStatus("idle");
  }, []);

  /**
   * Clear all messages and reset error/status. {@code extra} runs at the end
   * for host-specific cleanup (e.g. clearing a pending native form).
   */
  const reset = useCallback((extra?: () => void) => {
    abortedRef.current = true;
    requestRef.current++;
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    messagesRef.current = [];
    setMessages([]);
    setError(null);
    setStatus("idle");
    extra?.();
  }, []);

  return {
    messages,
    status,
    error,
    setMessages,
    setStatus,
    setError,
    requestRef,
    abortedRef,
    abortControllerRef,
    messagesRef,
    isStale,
    handleSendError,
    commitMessages,
    beginSend,
    openAssistantBubble,
    appendToken,
    patchAssistant,
    stop,
    reset,
  };
}

export type StreamingChatCore = ReturnType<typeof useStreamingChatCore>;
