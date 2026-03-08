import type { ChatMessageItem } from "@/services/chat/chat.service"

export const DEFAULT_CONTEXT_WINDOW = 128000
export const LLM_STORAGE_KEY = "turing-chat-selected-llm"
export const TITLE_TIMEOUT_MS = 8000

export interface ChatAttachment {
  name: string
  type: string
  size: number
}

export interface ChatMessage extends ChatMessageItem {
  id: string
  attachments?: ChatAttachment[]
}

export const generateId = () => crypto.randomUUID()

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

export function formatRelativeTime(timestamp: number): string {
  const now = Date.now()
  const diff = now - timestamp
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return "just now"
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}d ago`
  return new Date(timestamp).toLocaleDateString()
}

export function contextBarColor(percentage: number): string {
  if (percentage >= 80) return "bg-red-500"
  if (percentage >= 60) return "bg-yellow-500"
  return "bg-blue-500"
}
