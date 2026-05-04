import { useCallback, useEffect, useRef, useState } from "react";
import {
  deleteAgentFlowState,
  deleteSiteConversationState,
  fetchChatEnabled,
  postAgentChat,
  postChatConversation,
} from "../core/api";
import { useTuringContext } from "../core/use-turing-context";
import type { TurChatConversationMessage } from "../core/types";

export type ChatStatus = "idle" | "loading" | "success" | "error";

export interface ChatMessage extends TurChatConversationMessage {
  /** Stable id for React keys (auto-generated) */
  id: string;
  /** Unix ms when the message was created */
  timestamp: number;
}

/**
 * Switches the hook from site mode (RAG, anonymous, default) to agent mode
 * (explicit AI agent + LLM). Pass when the consumer knows exactly which
 * agent to invoke — typical for authenticated console UIs.
 *
 * @since 2026.2.16
 */
export interface UseTuringChatAgent {
  readonly id: string;
  readonly llmInstanceId: string;
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
  /**
   * Forces a specific chat flow to govern every turn instead of letting the
   * server-side router auto-pick. Most callers should leave this unset.
   *
   * @since 2026.2.16
   */
  readonly flowId?: string;
  /**
   * Opt into agent mode: the hook hits `POST /v2/ai-agent/{id}/chat` with
   * the explicit `llmInstanceId` instead of the site's RAG endpoint. When
   * unset (default), behavior is the original site-scoped chat.
   *
   * @since 2026.2.16
   */
  readonly agent?: UseTuringChatAgent;
  /**
   * Overrides {@code TuringConfig.locale} for the chat call only. Useful
   * when the active locale is selected dynamically (e.g. a locale picker on
   * the search page) and the parent provider's config can't be threaded
   * with that runtime value.
   *
   * @since 2026.2.16
   */
  readonly locale?: string;
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
   * Stable id sent to the backend so the chat-flow router and any future
   * conversation-scoped features can group turns together. `null` until the
   * first `send` call (the id is minted lazily).
   *
   * @since 2026.2.16
   */
  conversationId: string | null;
  /**
   * Send a user message. Appends it to the conversation, calls the API
   * with full history, and appends the assistant reply on success.
   */
  send: (content: string) => Promise<void>;
  /** Abort the in-flight assistant turn (rolls back the pending user message). */
  stop: () => void;
  /** Clear all messages and reset error state. */
  reset: () => void;
  /**
   * Drops server-side flow state for the current conversation so the next
   * turn starts at the START node. In site mode it clears every flow on the
   * site's agent for this conversation; in agent mode it requires `flowId`
   * (no-op when omitted, since the agent endpoint targets one specific flow).
   * Returns `false` when nothing was deleted (no active flow, or the call
   * failed); the local message list is untouched.
   *
   * @since 2026.2.16
   */
  resetFlow: () => Promise<boolean>;
}

const DEFAULT_MAX_MESSAGES = 50;

function newId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function storageKey(site: string, suffix: string | true): string {
  const tag = typeof suffix === "string" ? suffix : "default";
  return `turing.chat.${site}.${tag}`;
}

interface PersistedChat {
  conversationId: string | null;
  messages: ChatMessage[];
}

function isChatMessage(m: unknown): m is ChatMessage {
  return (
    typeof m === "object" &&
    m !== null &&
    typeof (m as ChatMessage).id === "string" &&
    typeof (m as ChatMessage).content === "string" &&
    ((m as ChatMessage).role === "user" || (m as ChatMessage).role === "assistant")
  );
}

// Backwards compat: pre-2026.2.16 we persisted just the messages array.
// Treat that shape as legacy and migrate forward on first read.
function loadPersisted(key: string): PersistedChat {
  try {
    const raw = globalThis.sessionStorage?.getItem(key);
    if (!raw) return { conversationId: null, messages: [] };
    const parsed: unknown = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return { conversationId: null, messages: parsed.filter(isChatMessage) };
    }
    if (typeof parsed === "object" && parsed !== null) {
      const obj = parsed as Partial<PersistedChat>;
      const messages = Array.isArray(obj.messages) ? obj.messages.filter(isChatMessage) : [];
      const conversationId =
        typeof obj.conversationId === "string" && obj.conversationId
          ? obj.conversationId
          : null;
      return { conversationId, messages };
    }
    return { conversationId: null, messages: [] };
  } catch {
    return { conversationId: null, messages: [] };
  }
}

function savePersisted(key: string, value: PersistedChat): void {
  try {
    globalThis.sessionStorage?.setItem(key, JSON.stringify(value));
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
  const { persist = false, maxMessages = DEFAULT_MAX_MESSAGES, flowId, agent, locale: localeOverride } = options;
  const agentId = agent?.id;
  const agentLlmInstanceId = agent?.llmInstanceId;
  const isAgentMode = Boolean(agentId && agentLlmInstanceId);
  const effectiveLocale = localeOverride ?? config.locale;

  const persistKey = persist ? storageKey(config.site, persist) : null;

  const initialPersisted = persistKey ? loadPersisted(persistKey) : { conversationId: null, messages: [] };

  // In agent mode the site's RAG flag doesn't gate access — the consumer
  // already chose an agent — so default to enabled and skip the fetch.
  const [enabled, setEnabled] = useState<boolean | null>(isAgentMode ? true : null);
  const [messages, setMessages] = useState<ChatMessage[]>(initialPersisted.messages);
  const [conversationId, setConversationId] = useState<string | null>(initialPersisted.conversationId);
  const [status, setStatus] = useState<ChatStatus>("idle");
  const [error, setError] = useState<string | null>(null);

  const requestRef = useRef(0);
  const abortedRef = useRef(false);
  // Keep a synchronous mirror of the latest messages so `send` can read the
  // newest history without depending on `messages` (which would re-create the
  // callback on every turn) and without relying on the setState updater fn
  // (which is queued, not synchronous).
  const messagesRef = useRef<ChatMessage[]>(messages);
  // Same reasoning for `conversationId`: minted lazily inside `send`, the ref
  // lets us read the value back synchronously without a re-render.
  const conversationIdRef = useRef<string | null>(conversationId);

  // Resolve enabled flag once per site (skipped in agent mode)
  useEffect(() => {
    if (isAgentMode) {
      setEnabled(true);
      return;
    }
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
  }, [config.site, isAgentMode]);

  // Persist on change + keep the synchronous refs in sync
  useEffect(() => {
    messagesRef.current = messages;
    conversationIdRef.current = conversationId;
    if (persistKey) savePersisted(persistKey, { conversationId, messages });
  }, [persistKey, messages, conversationId]);

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

      // Build next history synchronously from the ref (always up-to-date),
      // commit it to state and the ref, then send the same array on the wire.
      const all = [...messagesRef.current, userMsg];
      const nextMessages =
        all.length > maxMessages ? all.slice(-maxMessages) : all;
      messagesRef.current = nextMessages;
      setMessages(nextMessages);

      // Mint the conversation id lazily on the first send. Without this the
      // backend skips chat-flow routing for turn 1 entirely (the executor
      // early-returns when `conversationId` is blank).
      let activeConversationId = conversationIdRef.current;
      if (!activeConversationId) {
        activeConversationId = newId();
        conversationIdRef.current = activeConversationId;
        setConversationId(activeConversationId);
      }

      try {
        const wire: TurChatConversationMessage[] = nextMessages.map((m) => ({
          role: m.role,
          content: m.content,
        }));

        const res = agentId && agentLlmInstanceId
          ? await postAgentChat(agentId, agentLlmInstanceId, wire, {
              conversationId: activeConversationId,
              flowId,
            })
          : await postChatConversation(config.site, wire, effectiveLocale, {
              conversationId: activeConversationId,
              flowId,
            });

        if (requestId !== requestRef.current || abortedRef.current) return;

        const assistantMsg: ChatMessage = {
          id: newId(),
          role: "assistant",
          content: res?.content ?? "",
          timestamp: Date.now(),
        };

        const allWithReply = [...messagesRef.current, assistantMsg];
        const nextWithReply =
          allWithReply.length > maxMessages
            ? allWithReply.slice(-maxMessages)
            : allWithReply;
        messagesRef.current = nextWithReply;
        setMessages(nextWithReply);
        setStatus("success");
      } catch (err) {
        if (requestId !== requestRef.current || abortedRef.current) return;
        setError(err instanceof Error ? err.message : "Chat request failed");
        setStatus("error");
      }
    },
    [config.site, effectiveLocale, maxMessages, flowId, agentId, agentLlmInstanceId],
  );

  const stop = useCallback(() => {
    abortedRef.current = true;
    requestRef.current++;
    setStatus("idle");
  }, []);

  const reset = useCallback(() => {
    abortedRef.current = true;
    requestRef.current++;
    messagesRef.current = [];
    conversationIdRef.current = null;
    setMessages([]);
    setConversationId(null);
    setError(null);
    setStatus("idle");
  }, []);

  // Drop server-side flow state. Site mode clears every flow attached to
  // the conversation under the site's agent (the auto-router may have
  // picked any of them); agent mode targets the specific `flowId` the
  // caller declared. Returns false when there's nothing to clear or the
  // call fails — never throws so a "New chat" button never breaks the UI.
  const resetFlow = useCallback(async (): Promise<boolean> => {
    const cid = conversationIdRef.current;
    if (!cid) return false;
    try {
      if (agentId && agentLlmInstanceId) {
        if (!flowId) return false;
        return await deleteAgentFlowState(agentId, flowId, cid);
      }
      const deleted = await deleteSiteConversationState(config.site, cid);
      return deleted > 0;
    } catch {
      return false;
    }
  }, [agentId, agentLlmInstanceId, flowId, config.site]);

  return {
    enabled,
    messages,
    status,
    error,
    isStreaming: status === "loading",
    conversationId,
    send,
    stop,
    reset,
    resetFlow,
  };
}
