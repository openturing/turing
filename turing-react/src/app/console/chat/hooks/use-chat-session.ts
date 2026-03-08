import { useCallback, useState } from "react"
import { type ChatSession, saveSession, getSession, getAllSessions, deleteSession } from "@/services/chat/chat-session.service"
import { TurChatService } from "@/services/chat/chat.service"
import { type ChatMessage, generateId, generateFallbackTitle, TITLE_TIMEOUT_MS } from "../chat.types"

export function useChatSession(turChatService: TurChatService, getLlmTitle: (llmId: string) => { title?: string; modelName?: string } | undefined) {
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null)

  const loadSessions = useCallback(async () => {
    try {
      const all = await getAllSessions()
      setSessions(all)
    } catch (err) {
      console.warn("Failed to load sessions:", err)
    }
  }, [])

  const generateAITitle = useCallback(async (msgs: ChatMessage[], llmId: string): Promise<string> => {
    const text = msgs.slice(0, 4).map((m) => `${m.role}: ${m.content}`).join("\n")
    return new Promise<string>((resolve) => {
      let title = ""
      const timeout = setTimeout(() => resolve(generateFallbackTitle(msgs)), TITLE_TIMEOUT_MS)

      turChatService.sendStream(
        llmId,
        [{ role: "user", content: `Generate a very short title (max 6 words, no quotes, no punctuation at end) for this conversation. Reply ONLY with the title, nothing else.\n\n${text}` }],
        (token) => { title += token },
        () => {
          clearTimeout(timeout)
          const cleaned = title.trim().replace(/^["']|["']$/g, "").trim()
          resolve(cleaned || generateFallbackTitle(msgs))
        },
        () => {
          clearTimeout(timeout)
          resolve(generateFallbackTitle(msgs))
        },
      )
    })
  }, [turChatService])

  const saveAfterResponse = useCallback(async (tab: "chat" | "semantic", msgs: ChatMessage[], llmId: string) => {
    if (msgs.length === 0) return

    const instance = getLlmTitle(llmId)
    const isNewSession = !activeSessionId
    const sessionId = activeSessionId ?? generateId()

    if (isNewSession) {
      const session: ChatSession = {
        id: sessionId,
        title: generateFallbackTitle(msgs),
        tab,
        llmId,
        llmTitle: instance?.title,
        modelName: instance?.modelName,
        messages: msgs,
        createdAt: Date.now(),
        updatedAt: Date.now(),
      }
      await saveSession(session)
      setActiveSessionId(sessionId)
      await loadSessions()

      generateAITitle(msgs, llmId).then(async (aiTitle) => {
        const updated: ChatSession = { ...session, title: aiTitle }
        await saveSession(updated)
        await loadSessions()
      }).catch(() => { /* fallback title already saved */ })
    } else {
      const existing = await getSession(sessionId)
      const session: ChatSession = {
        id: sessionId,
        title: existing?.title ?? generateFallbackTitle(msgs),
        tab,
        llmId,
        llmTitle: instance?.title,
        modelName: instance?.modelName,
        messages: msgs,
        createdAt: existing?.createdAt ?? Date.now(),
        updatedAt: Date.now(),
      }
      await saveSession(session)
      await loadSessions()
    }
  }, [activeSessionId, generateAITitle, loadSessions, getLlmTitle])

  const handleDeleteSession = useCallback(async (e: React.MouseEvent, id: string) => {
    e.stopPropagation()
    await deleteSession(id)
    if (activeSessionId === id) setActiveSessionId(null)
    await loadSessions()
  }, [activeSessionId, loadSessions])

  const resetSession = useCallback(() => {
    setActiveSessionId(null)
  }, [])

  return {
    sessions,
    activeSessionId,
    setActiveSessionId,
    loadSessions,
    saveAfterResponse,
    handleDeleteSession,
    resetSession,
  }
}
