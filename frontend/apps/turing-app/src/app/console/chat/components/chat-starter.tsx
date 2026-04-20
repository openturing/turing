"use client"
import { ROUTES } from "@/app/routes.const"
import { IconMessageChatbot } from "@tabler/icons-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { LLM_STORAGE_KEY } from "../chat.types"
import { ChatInput } from "./chat-input"
import { ChatSuggestions } from "./chat-suggestions"

interface ChatStarterProps {
  /** LLM instance ID to use. If provided, sets it as selected before navigating. */
  readonly defaultLlmId?: string
  /** Model label to show below the input */
  readonly modelLabel?: string
  /** Whether to show the icon and heading */
  readonly showHeader?: boolean
  /**
   * If provided, sends the prompt in-place (for chat page empty state).
   * If not provided, navigates to /admin/chat with the prompt in sessionStorage.
   */
  readonly onSendInPlace?: (prompt: string) => void
  /** File attachment handlers (for chat page) */
  readonly attachments?: {
    files: File[]
    onAdd: (files: FileList | File[]) => void
    onRemove: (index: number) => void
  }
}

/**
 * Reusable centered chat input with intent suggestions.
 * Supports two modes:
 * - Navigate mode (home page): stores prompt in sessionStorage and navigates to chat
 * - In-place mode (chat page): calls onSendInPlace directly
 */
export function ChatStarter({ defaultLlmId, modelLabel, showHeader = true, onSendInPlace, attachments }: ChatStarterProps) {
  const [input, setInput] = useState("")
  const { t } = useTranslation()
  const navigate = useNavigate()

  function dispatchPrompt(prompt: string) {
    if (onSendInPlace) {
      onSendInPlace(prompt)
      return
    }
    if (defaultLlmId) {
      localStorage.setItem(LLM_STORAGE_KEY, defaultLlmId)
    }
    sessionStorage.setItem("turing-chat-initial-prompt", prompt)
    navigate(ROUTES.CHAT_ROOT)
  }

  function handleSend() {
    const trimmed = input.trim()
    if (!trimmed) return
    dispatchPrompt(trimmed)
  }

  return (
    <div className="flex flex-col items-center">
      {showHeader && (
        <>
          <div className="rounded-full bg-gradient-to-br from-blue-600 to-indigo-600 p-4 dark:from-blue-500 dark:to-indigo-500 mb-4">
            <IconMessageChatbot className="size-8 text-white" />
          </div>
          <h2 className="text-2xl font-semibold mb-1">{t("chat.howCanIHelp")}</h2>
        </>
      )}
      {modelLabel && (
        <p className="text-muted-foreground text-sm mb-6">{modelLabel}</p>
      )}
      <div className="w-full max-w-2xl">
        <ChatInput
          value={input}
          onChange={setInput}
          onSend={handleSend}
          loading={false}
          disabled={!defaultLlmId}
          placeholder={defaultLlmId ? t("chat.askMeAnything") : t("chat.noDefaultLlm")}
          accentColor="blue"
          attachments={attachments}
        />
      </div>
      <ChatSuggestions onSelectPrompt={dispatchPrompt} />
    </div>
  )
}
