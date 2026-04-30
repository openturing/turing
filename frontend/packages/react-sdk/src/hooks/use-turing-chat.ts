import { useCallback, useEffect, useRef, useState } from "react";
import { fetchChatEnabled, postChatConversation } from "../core/api";
import { useTuringContext } from "../core/use-turing-context";
import type { TurChatConversationMessage } from "../core/types";

export type ChatStatus = "idle" | "loading" | "success" | "error";

export interface ChatMessage extends TurChatConversationMessage {
  /** Stable id for React keys (auto-generated) */
  id: string;
  /** Unix ms when the message was created */
  timestamp: number;
}

export interface UseTuringChatOptions {
  /**
   * Persist the conversation in `sessionStorage`, scoped by site name.
   * Pass a string to use a custom key suffix.
   * Default: `false` (no persistence).
   */
  readonly persist?: boolean | string;
  /**
   * Maximum number of message turns kept in memory.
   * Default: `50`.
   */
  readonly maxMessages?: number;
}

export interface UseTuringChatReturn {
  /** Whether the site has GenAI/RAG enabled. `null` while unknown. */
  enabled: boolean | null;
  /** Current conversation messages (oldest first) */
  messages: ChatMessage[];
  /** Loading status of the most recent send */
  status: ChatStatus;
  /** Error message from the last failed send */
  error: string | null;
  /** Whether a turn is in progress */
  isStreaming: boolean;
  /**
   * Send a user message. Appends it to the conversation, calls the API
   * with full history, and appends the assistant reply on success.
   */
  send: (content: string) => Promise<void>;
  /** Abort the in-flight assistant turn (rolls back the pending user message). */
  stop: () => void;
  /** Clear all messages and reset error state. */
  reset: () => void;
}

const DEFAULT_MAX_MESSAGES = 50;

function newId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function storageKey(site: string, suffix: string | true): string {
  const tag = typeof suffix === "string" ? suffix : "default";
  return `turing.chat.${site}.${tag}`;
}

function loadPersisted(key: string): ChatMessage[] {
  try {
    const raw = globalThis.sessionStorage?.getItem(key);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (m): m is ChatMessage =>
        typeof m === "object" &&
        m !== null &&
        typeof (m as ChatMessage).id === "string" &&
        typeof (m as ChatMessage).content === "string" &&
        ((m as ChatMessage).role === "user" || (m as ChatMessage).role === "assistant"),
    );
  } catch {
    return [];
  }
}

function savePersisted(key: string, messages: ChatMessage[]): void {
  try {
    globalThis.sessionStorage?.setItem(key, JSON.stringify(messages));
  } catch {
    // sessionStorage may be unavailable (SSR, private mode, quota)
  }
}

/**
 * Conversational AI hook backed by the site's RAG endpoint.
 * Manages multi-turn chat state, history, abort, and optional persistence.
 *
 * @example
 * ```tsx
 * const { enabled, messages, send, status, reset } = useTuringChat({ persist: true });
 *
 * if (enabled === false) return null;
 *
 * return (
 *   <>
 *     {messages.map((m) => <Bubble key={m.id} role={m.role}>{m.content}</Bubble>)}
 *     <input onSubmit={(text) => send(text)} disabled={status === "loading"} />
 *     <button onClick={reset}>New conversation</button>
 *   </>
 * );
 * ```
 *
 * @since 2026.2.12
 */
export function useTuringChat(
  options: UseTuringChatOptions = {},
): UseTuringChatReturn {
  const { config } = useTuringContext();
  const { persist = false, maxMessages = DEFAULT_MAX_MESSAGES } = options;

  const persistKey = persist ? storageKey(config.site, persist) : null;

  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>(() =>
    persistKey ? loadPersisted(persistKey) : [],
  );
  const [status, setStatus] = useState<ChatStatus>("idle");
  const [error, setError] = useState<string | null>(null);

  const requestRef = useRef(0);
  const abortedRef = useRef(false);

  // Resolve enabled flag once per site
  useEffect(() => {
    let alive = true;
    fetchChatEnabled(config.site)
      .then((res) => {
        if (alive) setEnabled(Boolean(res?.enabled));
      })
      .catch(() => {
        if (alive) setEnabled(false);
      });
    return () => {
      alive = false;
    };
  }, [config.site]);

  // Persist on change
  useEffect(() => {
    if (persistKey) savePersisted(persistKey, messages);
  }, [persistKey, messages]);

  const send = useCallback(
    async (rawContent: string) => {
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
      setError(null);
      setStatus("loading");

      // Snapshot history with the new user message for the API call.
      // We strip id/timestamp on the wire — server only needs role+content.
      let nextMessages: ChatMessage[] = [];
      setMessages((prev) => {
        const all = [...prev, userMsg];
        nextMessages = all.length > maxMessages ? all.slice(-maxMessages) : all;
        return nextMessages;
      });

      try {
        const wire: TurChatConversationMessage[] = nextMessages.map((m) => ({
          role: m.role,
          content: m.content,
        }));

        const res = await postChatConversation(config.site, wire, config.locale);

        if (requestId !== requestRef.current || abortedRef.current) return;

        const assistantMsg: ChatMessage = {
          id: newId(),
          role: "assistant",
          content: res?.text ?? "",
          timestamp: Date.now(),
        };

        setMessages((prev) => {
          const all = [...prev, assistantMsg];
          return all.length > maxMessages ? all.slice(-maxMessages) : all;
        });
        setStatus("success");
      } catch (err) {
        if (requestId !== requestRef.current || abortedRef.current) return;
        setError(err instanceof Error ? err.message : "Chat request failed");
        setStatus("error");
      }
    },
    [config.site, config.locale, maxMessages],
  );

  const stop = useCallback(() => {
    abortedRef.current = true;
    requestRef.current++;
    setStatus("idle");
  }, []);

  const reset = useCallback(() => {
    abortedRef.current = true;
    requestRef.current++;
    setMessages([]);
    setError(null);
    setStatus("idle");
  }, []);

  return {
    enabled,
    messages,
    status,
    error,
    isStreaming: status === "loading",
    send,
    stop,
    reset,
  };
}
