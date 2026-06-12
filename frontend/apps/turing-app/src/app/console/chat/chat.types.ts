import type {
  TurChatConversationMessage,
  TurChatSource,
  ChatMessage as TuringChatMessage,
} from "@viglet/turing-react-sdk"

export const DEFAULT_CONTEXT_WINDOW = 128000
export const LLM_STORAGE_KEY = "turing-chat-selected-llm"
export const CHAT_INITIAL_PROMPT_KEY = "turing-chat-initial-prompt"
export const CHAT_INITIAL_AGENT_KEY = "turing-chat-initial-agent"
export const TITLE_TIMEOUT_MS = 8000

export interface ChatAttachment {
  name: string
  type: string
  size: number
}

export interface ChatMessage extends TurChatConversationMessage {
  id: string
  attachments?: ChatAttachment[]
  /**
   * Suggested chip labels surfaced alongside the assistant turn. When present, the message
   * list renders one button per label below the bubble; clicking sends that label as the
   * next user message. Free-text input stays available in parallel.
   *
   * @since 2026.2.7
   */
  suggestedOptions?: string[]
  /**
   * RAG provenance behind the assistant answer (T292/T293). When present, the
   * message list renders source chips below the bubble with a one-click
   * "why did you say this?" expansion to the cited passages.
   *
   * @since 2026.3.1
   */
  sources?: TurChatSource[]
}

export const generateId = () => crypto.randomUUID()

/**
 * Admin session transcript → SDK `useTuringChat` message shape. Chip labels
 * live under `suggestedOptions` in the admin model and `options` in the SDK;
 * `timestamp` is synthesized (the admin store doesn't persist it).
 */
export function toTuringMessages(msgs: ChatMessage[]): TuringChatMessage[] {
  return msgs.map((m) => ({
    id: m.id,
    role: m.role === "assistant" ? "assistant" : "user",
    content: m.content,
    timestamp: Date.now(),
    options: m.suggestedOptions,
    sources: m.sources,
  }))
}

/** SDK `useTuringChat` message shape → admin transcript shape (for the store). */
export function toAdminMessages(msgs: TuringChatMessage[]): ChatMessage[] {
  return msgs.map((m) => ({
    id: m.id,
    role: m.role,
    content: m.content,
    suggestedOptions: m.options,
    sources: m.sources,
  }))
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function estimateTokens(text: string): number {
  return Math.ceil(text.length / 4)
}

export function formatTokenCount(tokens: number): string {
  if (tokens >= 1000) return `${(tokens / 1000).toFixed(1)}k`
  return `${tokens}`
}

export function generateFallbackTitle(messages: ChatMessage[]): string {
  const firstUser = messages.find((m) => m.role === "user")
  if (firstUser) {
    const text = firstUser.content.trim()
    return text.length > 50 ? `${text.slice(0, 47)}...` : text
  }
  return `Chat ${new Date().toLocaleString()}`
}

import type { Locale } from "date-fns"
import { formatDistanceToNow } from "date-fns"

export function formatRelativeTime(timestamp: number, locale?: Locale): string {
  return formatDistanceToNow(new Date(timestamp), { addSuffix: true, locale })
}

export function contextBarColor(percentage: number): string {
  if (percentage >= 80) return "bg-red-500"
  if (percentage >= 60) return "bg-yellow-500"
  return "bg-blue-500"
}
