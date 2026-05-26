import { useCallback, useEffect, useRef, useState } from "react"
import { postLlmChat } from "@viglet/turing-react-sdk"
import { type ChatSession, saveSession, getSession, getAllSessions, deleteSession } from "@/services/chat/chat-session.service"
import { type ChatMessage, generateId, generateFallbackTitle, TITLE_TIMEOUT_MS } from "../chat.types"

export function useChatSession(getLlmTitle: (llmId: string) => { title?: string; modelName?: string } | undefined) {
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null)
  // Synchronous mirrors so `ensureSessionId` and `saveAfterResponse` can read
  // the latest values without depending on a re-render cycle.
  const activeSessionIdRef = useRef<string | null>(null)
  const pendingFirstSaveRef = useRef(false)

  useEffect(() => {
    activeSessionIdRef.current = activeSessionId
  }, [activeSessionId])

  const loadSessions = useCallback(async () => {
    try {
      const all = await getAllSessions()
      setSessions(all)
    } catch (err) {
      console.warn("Failed to load sessions:", err)
    }
  }, [])

  // Returns the active session id, generating one synchronously when none
  // exists. The caller must invoke this *before* the first request of a new
  // chat so the backend receives a stable `conversationId` from turn 1 — the
  // chat-flow router skips the turn entirely without it.
  const ensureSessionId = useCallback((): string => {
    if (activeSessionIdRef.current) return activeSessionIdRef.current
    const id = generateId()
    activeSessionIdRef.current = id
    pendingFirstSaveRef.current = true
    setActiveSessionId(id)
    return id
  }, [])

  const generateAITitle = useCallback(async (msgs: ChatMessage[], llmId: string): Promise<string> => {
    const text = msgs.slice(0, 4).map((m) => `${m.role}: ${m.content}`).join("\n")
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), TITLE_TIMEOUT_MS)
    try {
      const res = await postLlmChat(
        llmId,
        [{ role: "user", content: `Generate a very short title (max 6 words, no quotes, no punctuation at end) for this conversation. Reply ONLY with the title, nothing else.\n\n${text}` }],
        { signal: controller.signal },
      )
      const cleaned = res.content.trim().replace(/^["']|["']$/g, "").trim()
      return cleaned || generateFallbackTitle(msgs)
    } catch {
      // timeout, abort, network error — every branch falls back to a heuristic title
      return generateFallbackTitle(msgs)
    } finally {
      clearTimeout(timeout)
    }
  }, [])

  const saveAfterResponse = useCallback(async (tab: string, msgs: ChatMessage[], llmId: string) => {
    if (msgs.length === 0) return

    const instance = getLlmTitle(llmId)
    // The session id may have been minted up-front by `ensureSessionId` (so the
    // backend got it from turn 1). Read from the ref to bypass stale closures
    // and consume `pendingFirstSaveRef` to know whether this save is the first
    // one for the session — that branch persists initial metadata and kicks
    // off async title generation.
    const sessionId = activeSessionIdRef.current ?? generateId()
    const isNewSession = pendingFirstSaveRef.current || activeSessionIdRef.current !== sessionId
    pendingFirstSaveRef.current = false

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
      if (activeSessionIdRef.current !== sessionId) {
        activeSessionIdRef.current = sessionId
        setActiveSessionId(sessionId)
      }
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
  }, [generateAITitle, loadSessions, getLlmTitle])

  const handleDeleteSession = useCallback(async (e: React.MouseEvent, id: string) => {
    e.stopPropagation()
    await deleteSession(id)
    if (activeSessionId === id) {
      activeSessionIdRef.current = null
      pendingFirstSaveRef.current = false
      setActiveSessionId(null)
    }
    await loadSessions()
  }, [activeSessionId, loadSessions])

  const resetSession = useCallback(() => {
    activeSessionIdRef.current = null
    pendingFirstSaveRef.current = false
    setActiveSessionId(null)
  }, [])

  return {
    sessions,
    activeSessionId,
    setActiveSessionId,
    ensureSessionId,
    loadSessions,
    saveAfterResponse,
    handleDeleteSession,
    resetSession,
  }
}
