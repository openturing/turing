"use client"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  useTuringChat,
  type ChatMessage as TuringChatMessage,
} from "@viglet/turing-react-sdk"
import type { TurPersona } from "@/models/persona/persona.model"
import { estimateTokens, toAdminMessages } from "../chat.types"
import { ChatEmptyState } from "./chat-empty-state"
import { ChatInput } from "./chat-input"
import { ChatMessageList } from "./chat-message-list"
import { ContextBar } from "./context-bar"

/** Subset of the LLM instance the empty-state label reads. */
interface DisplayInstance {
  title: string
  modelName?: string
  turLLMVendor?: { id: string } | null
}

interface PersonaChatTabProps {
  persona: TurPersona
  /** Resolved LLM for this tab — any enabled instance (personas have no allow-list). */
  llmInstanceId: string
  selectedInstance?: DisplayInstance
  modelLabel: string
  contextWindow: number
  /** Controlled backend conversation id (== the host's IndexedDB session id). */
  conversationId?: string
  /** Seed transcript (restored session / post-compaction summary), SDK shape. */
  initialMessages: TuringChatMessage[]
  // ── Context compaction (parent orchestrates summarize + reseed) ──
  compacting: boolean
  onCompactRequested: (messages: TuringChatMessage[]) => void
  // ── Persistence: parent saves the turn to its session store ──
  onResponseComplete: (messages: TuringChatMessage[]) => void
  /** Lifts a coarse "has any message" signal so the header can show "New chat". */
  onHasMessagesChange?: (hasMessages: boolean) => void
  // ── Initial prompt forwarded from the home page (auto-send once) ──
  autoSendPrompt?: string | null
  onAutoSendConsumed?: () => void
}

/**
 * Block AI / §XXXII.4 (T581) — one persona tab's chat lifecycle. The
 * persona-mode peer of {@link AgentChatTab}: it reuses the exact presentational
 * stack ({@link ChatEmptyState} / {@link ChatInput} / {@link ChatMessageList} /
 * {@link ContextBar}) but talks to the persona endpoint via the T579
 * {@code useTuringChat({ persona })} subject. Deliberately drops the
 * agent-coupled surfaces — no flow / skill selectors, no per-agent pre-flight
 * cost gate, no feedback / rephrase (those key off an agent id the persona
 * chat has no equivalent of). Persona turns are stateless on the backend; the
 * conversation id here is purely for the local session store.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function PersonaChatTab({
  persona,
  llmInstanceId,
  selectedInstance,
  modelLabel,
  contextWindow,
  conversationId,
  initialMessages,
  compacting,
  onCompactRequested,
  onResponseComplete,
  onHasMessagesChange,
  autoSendPrompt,
  onAutoSendConsumed,
}: Readonly<PersonaChatTabProps>) {
  const { t } = useTranslation()
  const [input, setInput] = useState("")
  const endRef = useRef<HTMLDivElement>(null)

  const { messages, isStreaming, status, send } = useTuringChat({
    persona: { id: persona.id, llmInstanceId },
    conversationId,
    initialMessages,
  })

  // Synchronous mirror so the turn-complete effect saves the final transcript
  // without re-subscribing on every token.
  const messagesRef = useRef(messages)
  messagesRef.current = messages

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [messages])

  useEffect(() => {
    onHasMessagesChange?.(messages.length > 0)
  }, [messages.length, onHasMessagesChange])

  // Persist on the loading→success edge (mirrors AgentChatTab).
  const prevStatusRef = useRef(status)
  useEffect(() => {
    const prev = prevStatusRef.current
    prevStatusRef.current = status
    if (status === "success" && prev !== "success") {
      onResponseComplete(messagesRef.current)
    }
  }, [status, onResponseComplete])

  const handleSend = useCallback(() => {
    const text = input.trim()
    if (!text || isStreaming || !llmInstanceId) return
    setInput("")
    void send(text)
  }, [input, isStreaming, llmInstanceId, send])

  const handleOptionClick = useCallback(
    (label: string) => {
      if (isStreaming || !llmInstanceId) return
      void send(label)
    },
    [isStreaming, llmInstanceId, send],
  )

  // Auto-send the prompt forwarded from the home page, once, after the LLM resolves.
  const autoSentRef = useRef(false)
  useEffect(() => {
    if (autoSentRef.current || !autoSendPrompt || !llmInstanceId) return
    autoSentRef.current = true
    void send(autoSendPrompt)
    onAutoSendConsumed?.()
  }, [autoSendPrompt, llmInstanceId, send, onAutoSendConsumed])

  const adminMessages = useMemo(() => toAdminMessages(messages), [messages])

  const tokens = useMemo(
    () => estimateTokens(messages.map((m) => m.content).join("")),
    [messages],
  )
  const percentage = Math.min(Math.round((tokens / contextWindow) * 100), 100)
  const canCompact = !isStreaming && messages.length >= 4

  return (
    <>
      <div className="flex-1 overflow-y-auto">
        {messages.length === 0 ? (
          <ChatEmptyState
            variant="persona"
            selectedInstance={selectedInstance}
            personaName={persona.name}
          />
        ) : (
          <ChatMessageList
            messages={adminMessages}
            loading={isStreaming}
            assistantName={persona.name}
            endRef={endRef}
            onOptionClick={handleOptionClick}
          />
        )}
      </div>
      <div className="shrink-0 border-t bg-background">
        <div className="max-w-3xl mx-auto px-4 py-4">
          <ChatInput
            value={input}
            onChange={setInput}
            onSend={handleSend}
            loading={isStreaming}
            disabled={!llmInstanceId}
            placeholder={
              llmInstanceId
                ? t("chat.persona.ask", {
                    persona: persona.name,
                    defaultValue: `Talk to ${persona.name}`,
                  })
                : t("chat.selectModelToStart")
            }
            accentColor="fuchsia"
          />
          <div className="flex items-center justify-between mt-2 gap-4">
            <span className="text-xs text-muted-foreground shrink-0">{modelLabel}</span>
            {messages.length > 0 && (
              <ContextBar
                tokens={tokens}
                percentage={percentage}
                contextWindow={contextWindow}
                compacting={compacting}
                canCompact={canCompact}
                onCompact={() => onCompactRequested(messages)}
              />
            )}
          </div>
        </div>
      </div>
    </>
  )
}
