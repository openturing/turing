"use client"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { toast } from "sonner"
import "./chat-highlight.css"

import { ROUTES } from "@/app/routes.const"
import { BlankSlate } from "@/components/blank-slate"
import { PageHeader } from "@/components/page-header"
import type { ChatSession } from "@/services/chat/chat-session.service"
import type { ChatMessageItem } from "@/services/chat/chat.service"
import { IconMessageChatbot } from "@tabler/icons-react"
import { type ChatMessage, estimateTokens, generateId } from "./chat.types"
import { ChatEmptyState } from "./components/chat-empty-state"
import { ChatHeader } from "./components/chat-header"
import { ChatInput } from "./components/chat-input"
import { ChatMessageList } from "./components/chat-message-list"
import { ChatSessionSidebar } from "./components/chat-session-sidebar"
import { ContextBar } from "./components/context-bar"
import { useChatSession } from "./hooks/use-chat-session"
import { useLlmInstances } from "./hooks/use-llm-instances"

export default function ChatPage() {
  const [activeTab, setActiveTab] = useState<"chat" | "semantic">("chat")
  const [sidebarOpen, setSidebarOpen] = useState(false)

  // Chat tab state
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([])
  const [chatInput, setChatInput] = useState("")
  const [chatLoading, setChatLoading] = useState(false)
  const [chatCompacting, setChatCompacting] = useState(false)
  const [attachedFiles, setAttachedFiles] = useState<File[]>([])
  const chatEndRef = useRef<HTMLDivElement>(null)

  // Semantic tab state
  const [semMessages, setSemMessages] = useState<ChatMessage[]>([])
  const [semInput, setSemInput] = useState("")
  const [semLoading, setSemLoading] = useState(false)
  const [semCompacting, setSemCompacting] = useState(false)
  const semEndRef = useRef<HTMLDivElement>(null)

  // Refs for latest messages (used in async callbacks)
  const chatMessagesRef = useRef(chatMessages)
  chatMessagesRef.current = chatMessages
  const semMessagesRef = useRef(semMessages)
  semMessagesRef.current = semMessages

  // Hooks
  const { llmInstances, loaded, selectedLlmId, selectedInstance, contextWindow, selectLlm, turChatService } = useLlmInstances()

  const getLlmTitle = useCallback((llmId: string) => {
    const inst = llmInstances.find((i) => i.id === llmId)
    return inst ? { title: inst.title, modelName: inst.modelName } : undefined
  }, [llmInstances])

  const { sessions, activeSessionId, setActiveSessionId, loadSessions, saveAfterResponse, handleDeleteSession, resetSession } = useChatSession(turChatService, getLlmTitle)

  useEffect(() => { loadSessions() }, [loadSessions])

  // Auto-scroll
  useEffect(() => {
    if (activeTab === "chat") chatEndRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [chatMessages, activeTab])
  useEffect(() => {
    if (activeTab === "semantic") semEndRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [semMessages, activeTab])

  // Context usage
  const chatContextUsage = useMemo(() => {
    const tokens = estimateTokens(chatMessages.map((m) => m.content).join(""))
    return { tokens, percentage: Math.min(Math.round((tokens / contextWindow) * 100), 100) }
  }, [chatMessages, contextWindow])

  const semContextUsage = useMemo(() => {
    const tokens = estimateTokens(semMessages.map((m) => m.content).join(""))
    return { tokens, percentage: Math.min(Math.round((tokens / contextWindow) * 100), 100) }
  }, [semMessages, contextWindow])

  // ── Chat tab handlers ──

  const addFiles = (newFiles: FileList | File[]) => setAttachedFiles((prev) => [...prev, ...Array.from(newFiles)])
  const removeFile = (index: number) => setAttachedFiles((prev) => prev.filter((_, i) => i !== index))

  const handleChatSend = async () => {
    const trimmed = chatInput.trim()
    if ((!trimmed && attachedFiles.length === 0) || chatLoading || !selectedLlmId) return

    const filesToSend = [...attachedFiles]
    const userMessage: ChatMessage = {
      id: generateId(),
      role: "user",
      content: trimmed || (filesToSend.length > 0 ? `[${filesToSend.map((f) => f.name).join(", ")}]` : ""),
      attachments: filesToSend.map((f) => ({ name: f.name, type: f.type, size: f.size })),
    }

    const assistantId = generateId()
    const updated = [...chatMessages, userMessage]
    setChatMessages([...updated, { id: assistantId, role: "assistant", content: "" }])
    setChatInput("")
    setAttachedFiles([])
    setChatLoading(true)

    const apiMessages: ChatMessageItem[] = updated.map(({ role, content }) => ({ role, content }))
    const llmId = selectedLlmId

    await turChatService.sendStream(
      llmId,
      apiMessages,
      (token) => setChatMessages((prev) => prev.map((msg) => msg.id === assistantId ? { ...msg, content: msg.content + token } : msg)),
      () => {
        setChatLoading(false)
        setTimeout(() => saveAfterResponse("chat", chatMessagesRef.current, llmId), 0)
      },
      (error) => {
        console.error(error)
        toast.error("Failed to get a response. Please check your LLM configuration.")
        setChatMessages((prev) => prev.filter((msg) => msg.id !== assistantId || msg.content !== ""))
        setChatLoading(false)
      },
      filesToSend.length > 0 ? filesToSend : undefined,
    )
  }

  const handleChatCompact = async () => {
    if (chatLoading || chatCompacting || chatMessages.length < 4 || !selectedLlmId) return
    setChatCompacting(true)
    const text = chatMessages.map((m) => `${m.role}: ${m.content}`).join("\n\n")
    let summary = ""
    await turChatService.sendStream(
      selectedLlmId,
      [{ role: "user", content: `Summarize the following conversation concisely, preserving key facts, decisions, and context needed to continue the conversation. Write the summary in the same language as the conversation.\n\n${text}` }],
      (token) => { summary += token },
      () => {
        setChatMessages([{ id: generateId(), role: "assistant", content: `**[Context compacted]**\n\n${summary}` }])
        setChatCompacting(false)
        toast.success("Context compacted successfully.")
      },
      (error) => { console.error(error); toast.error("Failed to compact context."); setChatCompacting(false) },
    )
  }

  // ── Semantic tab handlers ──

  const handleSemSend = async () => {
    const trimmed = semInput.trim()
    if (!trimmed || semLoading || !selectedLlmId) return

    const userMessage: ChatMessage = { id: generateId(), role: "user", content: trimmed }
    const assistantId = generateId()
    const updated = [...semMessages, userMessage]
    setSemMessages([...updated, { id: assistantId, role: "assistant", content: "" }])
    setSemInput("")
    setSemLoading(true)

    const apiMessages: ChatMessageItem[] = updated.map(({ role, content }) => ({ role, content }))
    const llmId = selectedLlmId

    await turChatService.sendSemanticStream(
      llmId,
      apiMessages,
      (token) => setSemMessages((prev) => prev.map((msg) => msg.id === assistantId ? { ...msg, content: msg.content + token } : msg)),
      () => {
        setSemLoading(false)
        setTimeout(() => saveAfterResponse("semantic", semMessagesRef.current, llmId), 0)
      },
      (error) => {
        console.error(error)
        toast.error("Failed to get a response. Please check your LLM configuration.")
        setSemMessages((prev) => prev.filter((msg) => msg.id !== assistantId || msg.content !== ""))
        setSemLoading(false)
      },
    )
  }

  const handleSemCompact = async () => {
    if (semLoading || semCompacting || semMessages.length < 4 || !selectedLlmId) return
    setSemCompacting(true)
    const text = semMessages.map((m) => `${m.role}: ${m.content}`).join("\n\n")
    let summary = ""
    await turChatService.sendSemanticStream(
      selectedLlmId,
      [{ role: "user", content: `Summarize the following conversation concisely, preserving key facts, decisions, and context needed to continue the conversation. Write the summary in the same language as the conversation.\n\n${text}` }],
      (token) => { summary += token },
      () => {
        setSemMessages([{ id: generateId(), role: "assistant", content: `**[Context compacted]**\n\n${summary}` }])
        setSemCompacting(false)
        toast.success("Context compacted successfully.")
      },
      (error) => { console.error(error); toast.error("Failed to compact context."); setSemCompacting(false) },
    )
  }

  // ── Shared handlers ──

  const handleNewChat = () => {
    if (activeTab === "chat") {
      setChatMessages([]); setChatInput(""); setAttachedFiles([])
    } else {
      setSemMessages([]); setSemInput("")
    }
    resetSession()
    setSidebarOpen(true)
  }

  const handleModelChange = (v: string) => {
    selectLlm(v)
    setChatMessages([]); setChatInput(""); setAttachedFiles([])
    setSemMessages([]); setSemInput("")
    resetSession()
  }

  const handleRestoreSession = (session: ChatSession) => {
    setActiveTab(session.tab)
    setActiveSessionId(session.id)
    if (session.tab === "chat") {
      setChatMessages(session.messages as ChatMessage[])
      setChatInput("")
      setAttachedFiles([])
    } else {
      setSemMessages(session.messages as ChatMessage[])
      setSemInput("")
    }
    if (session.llmId && llmInstances.some((i) => i.id === session.llmId)) {
      selectLlm(session.llmId)
    }
    setSidebarOpen(false)
  }

  // Derived
  const messages = activeTab === "chat" ? chatMessages : semMessages
  const isEmpty = messages.length === 0
  const assistantName = selectedInstance?.title ?? "Assistant"
  const modelLabel = selectedInstance ? `${selectedInstance.turLLMVendor?.id ?? ""} · ${selectedInstance.modelName ?? ""}` : "No model selected"

  if (loaded && llmInstances.length === 0) {
    return (
      <>
        <PageHeader turIcon={IconMessageChatbot} title={"Chat"} />
        <BlankSlate
          icon={IconMessageChatbot}
          title="No Language Model Available"
          description="To use the chat, create and enable at least one LLM instance."
          buttonText="Create LLM Instance"
          urlNew={ROUTES.LLM_INSTANCE + "/new"}
        />
      </>
    )
  }

  return (
    <div className="flex h-[calc(100vh-2rem)] max-h-screen">
      <ChatSessionSidebar
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        sessions={sessions}
        activeSessionId={activeSessionId}
        onRestore={handleRestoreSession}
        onDelete={handleDeleteSession}
      />

      <div className="flex flex-col flex-1 min-w-0">
        <ChatHeader
          activeTab={activeTab}
          onTabChange={setActiveTab}
          llmInstances={llmInstances}
          selectedLlmId={selectedLlmId}
          onModelChange={handleModelChange}
          onToggleSidebar={() => setSidebarOpen(!sidebarOpen)}
          hasMessages={!isEmpty}
          onNewChat={handleNewChat}
        />

        {/* Chat Tab */}
        {activeTab === "chat" && (
          <>
            <div className="flex-1 overflow-y-auto">
              {isEmpty
                ? <ChatEmptyState variant="chat" selectedInstance={selectedInstance} />
                : <ChatMessageList messages={chatMessages} loading={chatLoading} assistantName={assistantName} endRef={chatEndRef} />
              }
            </div>
            <div className="shrink-0 border-t bg-background">
              <div className="max-w-3xl mx-auto px-4 py-4">
                <ChatInput
                  value={chatInput}
                  onChange={setChatInput}
                  onSend={handleChatSend}
                  loading={chatLoading}
                  disabled={!selectedLlmId}
                  placeholder={selectedLlmId ? "Send a message..." : "Select a model to start..."}
                  accentColor="blue"
                  attachments={{ files: attachedFiles, onAdd: addFiles, onRemove: removeFile }}
                />
                <div className="flex items-center justify-between mt-2 gap-4">
                  <span className="text-xs text-muted-foreground shrink-0">{modelLabel}</span>
                  {!isEmpty && (
                    <ContextBar
                      tokens={chatContextUsage.tokens}
                      percentage={chatContextUsage.percentage}
                      contextWindow={contextWindow}
                      compacting={chatCompacting}
                      canCompact={!chatLoading && chatMessages.length >= 4}
                      onCompact={handleChatCompact}
                    />
                  )}
                </div>
              </div>
            </div>
          </>
        )}

        {/* Semantic Tab */}
        {activeTab === "semantic" && (
          <>
            <div className="flex-1 overflow-y-auto">
              {semMessages.length === 0
                ? <ChatEmptyState variant="semantic" selectedInstance={selectedInstance} />
                : <ChatMessageList messages={semMessages} loading={semLoading} assistantName={assistantName} endRef={semEndRef} />
              }
            </div>
            <div className="shrink-0 border-t bg-background">
              <div className="max-w-3xl mx-auto px-4 py-4">
                <ChatInput
                  value={semInput}
                  onChange={setSemInput}
                  onSend={handleSemSend}
                  loading={semLoading}
                  disabled={!selectedLlmId}
                  placeholder={selectedLlmId ? "Ask about your semantic navigation content..." : "Select a model to start..."}
                  accentColor="emerald"
                />
                <div className="flex items-center justify-between mt-2 gap-4">
                  <span className="text-xs text-muted-foreground shrink-0">{modelLabel}</span>
                  {semMessages.length > 0 && (
                    <ContextBar
                      tokens={semContextUsage.tokens}
                      percentage={semContextUsage.percentage}
                      contextWindow={contextWindow}
                      compacting={semCompacting}
                      canCompact={!semLoading && semMessages.length >= 4}
                      onCompact={handleSemCompact}
                    />
                  )}
                </div>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
