import type { TuringClient } from "../client";
import {
  deleteAgentFlowState,
  deleteSiteConversationState,
  fetchChatEnabled,
  postAgentChat,
  postChatConversation,
  type ChatDisabledReason,
} from "../api";
import {
  TUR_SESSION_DEFAULT_COOKIE_NAME,
  TUR_SESSION_DEFAULT_TTL_SECONDS,
  getOrCreateTurSession,
} from "../session";
import { createStore, type Store } from "../store";
import type { TurChatConversationMessage } from "../types";

/**
 * Vanilla chat controller — the framework-agnostic equivalent of the React
 * SDK's `useTuringChat`. Manages multi-turn conversation state, token-by-token
 * streaming, abort, optional `sessionStorage` persistence, and the
 * site-vs-agent endpoint split, all over an observable {@link Store}.
 *
 * <p>Two modes:
 * - <b>site mode</b> (default): hits the site's RAG endpoint; gated by a
 *   `/chat/enabled` probe. Requires {@code options.site}.
 * - <b>agent mode</b>: pass {@code options.agent}; hits
 *   `POST /v2/ai-agent/{id}/chat` with an explicit `llmInstanceId`.
 *
 * @example
 * ```js
 * const chat = createChatController(client, { site: "my-site" });
 * chat.subscribe((s) => render(s.messages));
 * await chat.send("Hello");
 * ```
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

export type ChatStatus = "idle" | "loading" | "success" | "error";

export interface ChatMessage extends TurChatConversationMessage {
  /** Stable id (auto-generated). */
  id: string;
  /** Unix ms when the message was created. */
  timestamp: number;
  /** Suggested chip labels for the next user turn (assistant messages only). */
  options?: string[];
}

export interface ChatControllerAgent {
  readonly id: string;
  readonly llmInstanceId: string;
  readonly allowOverride?: boolean;
}

export interface SendOverrides {
  /** Routes this turn through a different LLM instance (agent mode only). */
  readonly llmInstanceId?: string;
}

export interface ChatControllerOptions {
  /** SN site name. Required in site mode; ignored in agent mode. */
  readonly site?: string;
  /** Default locale forwarded to the site RAG endpoint. */
  readonly locale?: string;
  /** Persist conversation in `sessionStorage`. Pass a string for a custom key suffix. */
  readonly persist?: boolean | string;
  /** Max message turns kept in memory. Default 50. */
  readonly maxMessages?: number;
  /** Force a specific chat flow for every turn. */
  readonly flowId?: string;
  /** Opt into agent mode. */
  readonly agent?: ChatControllerAgent;
  /** Controlled conversation id (host owns the session lifecycle). */
  readonly conversationId?: string;
  /** Seed the message list at construction. */
  readonly initialMessages?: ChatMessage[];
}

export interface ChatControllerState {
  /** Whether the site has GenAI/RAG enabled. `null` while unknown. */
  enabled: boolean | null;
  /** Diagnostic code from the `/chat/enabled` probe. `null` while loading/agent mode. */
  disabledReason: ChatDisabledReason | null;
  messages: ChatMessage[];
  status: ChatStatus;
  error: string | null;
  isStreaming: boolean;
  conversationId: string | null;
}

export interface ChatController extends Store<ChatControllerState> {
  /** Send a user message (streams the assistant reply). */
  send(content: string, overrides?: SendOverrides): Promise<void>;
  /** Abort the in-flight assistant turn. */
  stop(): void;
  /** Clear all messages and reset error state. */
  reset(): void;
  /** Drop server-side flow state for the current conversation. */
  resetFlow(): Promise<boolean>;
  /** Abort any in-flight request and release resources. */
  destroy(): void;
}

const DEFAULT_MAX_MESSAGES = 50;

function newId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function isAbortError(err: unknown): boolean {
  if (typeof DOMException !== "undefined" && err instanceof DOMException && err.name === "AbortError") {
    return true;
  }
  return err instanceof Error && err.name === "AbortError";
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

export function createChatController(
  client: TuringClient,
  options: ChatControllerOptions = {},
): ChatController {
  const {
    site = "",
    locale,
    persist = false,
    maxMessages = DEFAULT_MAX_MESSAGES,
    flowId,
    agent,
    conversationId: controlledConversationId,
    initialMessages,
  } = options;

  const agentId = agent?.id;
  const agentLlmInstanceId = agent?.llmInstanceId;
  const isAgentMode = Boolean(agentId && agentLlmInstanceId);

  if (!site && !isAgentMode) {
    throw new Error(
      "createChatController in site mode requires `options.site`. " +
        "Either pass it or pass `options.agent` to use agent mode.",
    );
  }

  const persistKey = persist && site ? storageKey(site, persist) : null;
  const initialPersisted = persistKey
    ? loadPersisted(persistKey)
    : { conversationId: controlledConversationId ?? null, messages: initialMessages ?? [] };

  const store = createStore<ChatControllerState>({
    enabled: isAgentMode ? true : null,
    disabledReason: null,
    messages: initialPersisted.messages,
    status: "idle",
    error: null,
    isStreaming: false,
    conversationId: initialPersisted.conversationId,
  });

  // Server-driven session cookie config (`turing.chat.session.*`).
  let sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME;
  let sessionTtlSeconds = TUR_SESSION_DEFAULT_TTL_SECONDS;

  // Closure mirrors of refs from the React hook.
  let latestRequestId = 0;
  let aborted = false;
  let abortController: AbortController | null = null;
  let messages = initialPersisted.messages;
  let conversationId: string | null = initialPersisted.conversationId;

  function commitMessages(next: ChatMessage[], status?: ChatStatus): void {
    messages = next;
    if (persistKey) savePersisted(persistKey, { conversationId, messages: next });
    store.setState(
      status
        ? { messages: next, status, isStreaming: status === "loading" }
        : { messages: next },
    );
  }

  function setConversationId(id: string | null): void {
    conversationId = id;
    if (persistKey) savePersisted(persistKey, { conversationId: id, messages });
    store.setState({ conversationId: id });
  }

  // Resolve enabled flag once (skipped in agent mode). Fire-and-forget.
  if (isAgentMode || !site) {
    store.setState({ enabled: true });
  } else {
    fetchChatEnabled(client, site)
      .then((res) => {
        const isEnabled = Boolean(res?.enabled);
        if (res?.sessionCookieName) sessionCookieName = res.sessionCookieName;
        if (typeof res?.sessionTtlSeconds === "number" && res.sessionTtlSeconds > 0) {
          sessionTtlSeconds = res.sessionTtlSeconds;
        }
        store.setState({
          enabled: isEnabled,
          disabledReason: isEnabled ? null : (res?.reason ?? null),
        });
      })
      .catch(() => {
        store.setState({ enabled: false, disabledReason: null });
      });
  }

  function handleSendError(err: unknown, requestId: number): void {
    if (requestId !== latestRequestId || aborted) return;
    if (isAbortError(err)) {
      store.setState({ status: "idle", isStreaming: false });
      return;
    }
    store.setState({
      error: err instanceof Error ? err.message : "Chat request failed",
      status: "error",
      isStreaming: false,
    });
  }

  async function send(rawContent: string, overrides?: SendOverrides): Promise<void> {
    const content = rawContent.trim();
    if (!content) return;

    const effectiveLlmInstanceId =
      overrides?.llmInstanceId && agentId ? overrides.llmInstanceId : agentLlmInstanceId;

    const userMsg: ChatMessage = {
      id: newId(),
      role: "user",
      content,
      timestamp: Date.now(),
    };

    const requestId = ++latestRequestId;
    aborted = false;
    abortController?.abort();
    const controller = new AbortController();
    abortController = controller;
    store.setState({ error: null, status: "loading", isStreaming: true });

    const all = [...messages, userMsg];
    const nextMessages = all.length > maxMessages ? all.slice(-maxMessages) : all;
    commitMessages(nextMessages);

    // conversationId is the cross-domain TUR_SESSION cookie (stable across
    // reloads). A controlled id always wins; otherwise mint/read the cookie.
    const activeConversationId =
      controlledConversationId ??
      getOrCreateTurSession({ name: sessionCookieName, ttlSeconds: sessionTtlSeconds }) ??
      conversationId ??
      newId();
    if (activeConversationId !== conversationId) {
      setConversationId(activeConversationId);
    }

    try {
      const wire: TurChatConversationMessage[] = nextMessages.map((m) => ({
        role: m.role,
        content: m.content,
      }));

      // Pre-commit an empty assistant bubble so a UI can render a typing
      // animation immediately, then patch it in place as tokens arrive.
      const assistantId = newId();
      const assistantMsgInitial: ChatMessage = {
        id: assistantId,
        role: "assistant",
        content: "",
        timestamp: Date.now(),
      };
      const streamingHistory = [...messages, assistantMsgInitial];
      const initialWithReply =
        streamingHistory.length > maxMessages
          ? streamingHistory.slice(-maxMessages)
          : streamingHistory;
      commitMessages(initialWithReply);

      const onToken = (token: string) => {
        if (requestId !== latestRequestId || aborted) return;
        commitMessages(
          patchMessage(messages, assistantId, (m) => ({ ...m, content: m.content + token })),
        );
      };

      const res =
        agentId && effectiveLlmInstanceId
          ? await postAgentChat(client, agentId, effectiveLlmInstanceId, wire, {
              conversationId: activeConversationId,
              flowId,
              onToken,
              signal: controller.signal,
            })
          : await postChatConversation(client, site, wire, locale, {
              conversationId: activeConversationId,
              flowId,
              onToken,
              signal: controller.signal,
            });

      if (requestId !== latestRequestId || aborted) return;

      const finalOptions = res?.options && res.options.length > 0 ? res.options : undefined;
      commitMessages(
        patchMessage(messages, assistantId, (m) => ({
          ...m,
          content: res?.content ?? m.content,
          options: finalOptions,
        })),
        "success",
      );
    } catch (err) {
      handleSendError(err, requestId);
    }
  }

  function stop(): void {
    aborted = true;
    latestRequestId++;
    abortController?.abort();
    abortController = null;
    store.setState({ status: "idle", isStreaming: false });
  }

  function reset(): void {
    aborted = true;
    latestRequestId++;
    abortController?.abort();
    abortController = null;
    commitMessages([]);
    store.setState({ error: null, status: "idle", isStreaming: false });
  }

  async function resetFlow(): Promise<boolean> {
    const cid = conversationId;
    if (!cid) return false;
    try {
      if (agentId && agentLlmInstanceId) {
        if (!flowId) return false;
        return await deleteAgentFlowState(client, agentId, flowId, cid);
      }
      if (!site) return false;
      const deleted = await deleteSiteConversationState(client, site, cid);
      return deleted > 0;
    } catch {
      return false;
    }
  }

  function destroy(): void {
    aborted = true;
    latestRequestId++;
    abortController?.abort();
    abortController = null;
  }

  return {
    getState: store.getState,
    setState: store.setState,
    subscribe: store.subscribe,
    send,
    stop,
    reset,
    resetFlow,
    destroy,
  };
}
