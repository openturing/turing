"use client"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { toast } from "@viglet/viglet-design-system"
import "./chat-highlight.css"

import { ROUTES } from "@/app/routes.const"
import { BlankSlate } from "@/components/blank-slate"
import { PageHeader } from "@/components/page-header"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import type { TurAIAgent } from "@/models/agent/ai-agent.model"
import type { TurChatFlow } from "@/models/agent/chat-flow.model"
import { TurAIAgentService } from "@/services/agent/ai-agent.service"
import { TurChatFlowService } from "@/services/agent/chat-flow.service"
import type { ChatSession } from "@/services/chat/chat-session.service"
import type { ChatMessageItem } from "@/services/chat/chat.service"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import { IconLoader2, IconMessageChatbot, IconRefresh, IconRobot } from "@tabler/icons-react"
import { Button } from "@/components/ui/button"
import { useTranslation } from "react-i18next"
import { CHAT_INITIAL_AGENT_KEY, CHAT_INITIAL_PROMPT_KEY, type ChatMessage, estimateTokens, generateId } from "./chat.types"
import { ChatEmptyState } from "./components/chat-empty-state"
import { ChatHeader } from "./components/chat-header"
import { ChatInput } from "./components/chat-input"
import { ChatMessageList } from "./components/chat-message-list"
import { ChatSessionSidebar } from "./components/chat-session-sidebar"
import { ContextBar } from "./components/context-bar"
import { useChatSession } from "./hooks/use-chat-session"
import { useLlmInstances } from "./hooks/use-llm-instances"
import { useScheduleAgentWaiting } from "./hooks/use-schedule-agent-waiting"

const turAIAgentService = new TurAIAgentService()
const turGlobalSettingsService = new TurGlobalSettingsService()
const turChatFlowService = new TurChatFlowService()

/** Sentinel: dropdown option that lets the backend's LLM router decide.
 *  Selecting a specific flow forces it; there's no "no flow" override —
 *  Auto-detect already falls through to free chat when no trigger matches. */
const FLOW_AUTO = "__auto__"

interface AgentTabState {
  messages: ChatMessage[]
  input: string
  loading: boolean
  compacting: boolean
}

export default function ChatPage() {
  const { t } = useTranslation()
  // Empty until agents load — then becomes `agent:<id>` of the default or first agent.
  const [activeTab, setActiveTab] = useState<string>("")
  const [sidebarOpen, setSidebarOpen] = useState(false)

  // AI Agents
  const [agents, setAgents] = useState<TurAIAgent[]>([])
  const [agentsLoaded, setAgentsLoaded] = useState(false)
  const [defaultAgentId, setDefaultAgentId] = useState<string>("")
  const [agentStates, setAgentStates] = useState<Record<string, AgentTabState>>({})

  // Per-agent: list of flows fetched on demand + the currently selected flow.
  const [flowsByAgent, setFlowsByAgent] = useState<Record<string, TurChatFlow[]>>({})
  const [selectedFlowByAgent, setSelectedFlowByAgent] = useState<Record<string, string>>({})

  // Agent tab ref
  const agentEndRef = useRef<HTMLDivElement>(null)

  // Ref for latest agent messages (used in async callbacks)
  const agentStatesRef = useRef(agentStates)
  agentStatesRef.current = agentStates

  // Hooks
  const { llmInstances, loaded, selectedLlmId, selectedInstance, contextWindow, selectLlm, turChatService } = useLlmInstances()

  // Load AI Agents + global default agent. Sort enabled agents so the default
  // appears first; this is what powers the tab order in the header.
  useEffect(() => {
    Promise.all([
      turAIAgentService.query().catch(() => [] as TurAIAgent[]),
      turGlobalSettingsService.query().catch(() => null),
    ]).then(([all, settings]) => {
      const enabled = all.filter((a) => a.enabled === 1)
      const defaultId = settings?.defaultAiAgentId ?? ""
      setDefaultAgentId(defaultId)

      // Default agent first, others keep relative order
      const sorted = defaultId
        ? [
            ...enabled.filter((a) => a.id === defaultId),
            ...enabled.filter((a) => a.id !== defaultId),
          ]
        : enabled
      setAgents(sorted)
      setAgentsLoaded(true)
    })
  }, [])

  // Active agent (if on agent tab)
  const activeAgentId = activeTab.startsWith("agent:") ? activeTab.slice(6) : null
  const activeAgent = activeAgentId ? agents.find((a) => a.id === activeAgentId) : null

  // Lazily fetch the agent's flows the first time it becomes active.
  useEffect(() => {
    if (!activeAgentId || flowsByAgent[activeAgentId] !== undefined) return
    turChatFlowService
      .query(activeAgentId)
      .then((list) => {
        setFlowsByAgent((prev) => ({ ...prev, [activeAgentId]: list.filter((f) => f.enabled === 1) }))
      })
      .catch(() => {
        setFlowsByAgent((prev) => ({ ...prev, [activeAgentId]: [] }))
      })
  }, [activeAgentId, flowsByAgent])

  const activeFlows = activeAgentId ? flowsByAgent[activeAgentId] ?? [] : []
  // Default to AUTO so the backend router picks a flow when its trigger
  // description matches the user message. Users can override to a specific
  // flow (force) or to NONE (skip the router entirely).
  const selectedFlowId = activeAgentId ? selectedFlowByAgent[activeAgentId] ?? FLOW_AUTO : FLOW_AUTO

  // Once agents have loaded, pick the initial active tab.
  // Priority: agent forwarded from home page (sessionStorage) → global default → first agent.
  useEffect(() => {
    if (!agentsLoaded || activeTab) return
    if (agents.length === 0) return
    const forwardedAgentId = sessionStorage.getItem(CHAT_INITIAL_AGENT_KEY)
    const forwardedAgent = forwardedAgentId ? agents.find((a) => a.id === forwardedAgentId) : null
    const target = forwardedAgent ?? agents.find((a) => a.id === defaultAgentId) ?? agents[0]
    setActiveTab(`agent:${target.id}`)
  }, [agentsLoaded, agents, activeTab, defaultAgentId])

  // LLM instances filtered by active agent
  const visibleLlmInstances = useMemo(() => {
    if (!activeAgent) return llmInstances
    const agentLlmIds = new Set(activeAgent.llmInstances.map((l) => l.id))
    return llmInstances.filter((i) => agentLlmIds.has(i.id))
  }, [activeAgent, llmInstances])

  // Auto-select first valid LLM when switching to an agent tab
  useEffect(() => {
    if (activeAgent && visibleLlmInstances.length > 0) {
      const currentValid = visibleLlmInstances.some((i) => i.id === selectedLlmId)
      if (!currentValid) {
        selectLlm(visibleLlmInstances[0].id)
      }
    }
  }, [activeAgent, visibleLlmInstances, selectedLlmId, selectLlm])

  // Get or init agent state
  const getAgentState = useCallback((agentId: string): AgentTabState => {
    return agentStates[agentId] ?? { messages: [], input: "", loading: false, compacting: false }
  }, [agentStates])

  const updateAgentState = useCallback((agentId: string, update: Partial<AgentTabState>) => {
    setAgentStates((prev) => ({
      ...prev,
      [agentId]: { ...prev[agentId] ?? { messages: [], input: "", loading: false, compacting: false }, ...update },
    }))
  }, [])

  const getLlmTitle = useCallback((llmId: string) => {
    const inst = llmInstances.find((i) => i.id === llmId)
    return inst ? { title: inst.title, modelName: inst.modelName } : undefined
  }, [llmInstances])

  const { sessions, activeSessionId, setActiveSessionId, ensureSessionId, loadSessions, saveAfterResponse, handleDeleteSession, resetSession } = useChatSession(turChatService, getLlmTitle)

  // T48 — poll the agent's slot snapshot while a conversation is live so the
  // input area can render an "Aguardando rotina…" banner whenever the engine
  // parks on a scheduleAgent node. Polling (vs SSE) because the agent-side
  // slot stream isn't wired in the console yet; a 2s cadence is plenty for
  // routine durations measured in seconds-to-minutes.
  const routineWaiting = useScheduleAgentWaiting({
    agentId: activeAgentId ?? undefined,
    conversationId: activeSessionId ?? undefined,
    enabled: !!activeAgentId,
  })

  // Reset the runtime state of the currently selected flow for this
  // conversation. Only meaningful when a *specific* flow is forced (not Auto)
  // and a session is active. Declared *after* useChatSession so
  // `activeSessionId` is in scope.
  const isSpecificFlowSelected = selectedFlowId !== FLOW_AUTO
  const canResetFlow = !!activeAgentId && isSpecificFlowSelected && !!activeSessionId

  const handleResetFlow = useCallback(async () => {
    if (!activeAgentId || !isSpecificFlowSelected || !activeSessionId) return
    try {
      await turChatService.resetAgentFlowState(activeAgentId, selectedFlowId, activeSessionId)
      toast.success(t("chatFlow.reset.done"))
    } catch (error) {
      console.error("Reset flow state failed", error)
      toast.error(t("chatFlow.reset.failed"))
    }
  }, [activeAgentId, isSpecificFlowSelected, selectedFlowId, activeSessionId, turChatService, t])

  useEffect(() => { loadSessions() }, [loadSessions])

  // Auto-scroll
  useEffect(() => {
    if (activeAgentId) agentEndRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [agentStates, activeAgentId, activeTab])

  // Context usage
  const agentContextUsage = useMemo(() => {
    if (!activeAgentId) return { tokens: 0, percentage: 0 }
    const state = getAgentState(activeAgentId)
    const tokens = estimateTokens(state.messages.map((m) => m.content).join(""))
    return { tokens, percentage: Math.min(Math.round((tokens / contextWindow) * 100), 100) }
  }, [activeAgentId, agentStates, contextWindow, getAgentState])

  // ── Agent tab handlers ──

  const sendAgentPrompt = useCallback(async (agentId: string, prompt: string) => {
    const agent = agents.find((a) => a.id === agentId)
    if (!agent) return
    const trimmed = prompt.trim()
    if (!trimmed) return
    const state = agentStatesRef.current[agentId] ?? { messages: [], input: "", loading: false, compacting: false }
    if (state.loading) return

    // Resolve the LLM: prefer the user's selection when it belongs to this
    // agent; otherwise prefer an agent LLM that is also in the enabled list,
    // and as a last resort just pick the agent's first LLM. The backend is
    // the final authority on whether the LLM is usable.
    const allowedLlms = agent.llmInstances ?? []
    if (allowedLlms.length === 0) {
      toast.error(t("chat.agentHasNoLlm", {
        agent: agent.title,
        defaultValue: `Agent "{{agent}}" has no LLM configured. Open the agent and attach at least one LLM.`,
      }))
      return
    }
    const allowedIds = new Set(allowedLlms.map((l) => l.id))
    let llmId = selectedLlmId
    if (!llmId || !allowedIds.has(llmId)) {
      const fallback = allowedLlms.find((l) =>
        llmInstances.some((i) => i.id === l.id),
      ) ?? allowedLlms[0]
      llmId = fallback.id
      selectLlm(llmId)
    }

    const userMessage: ChatMessage = { id: generateId(), role: "user", content: trimmed }
    const assistantId = generateId()
    const updated = [...state.messages, userMessage]

    updateAgentState(agentId, {
      messages: [...updated, { id: assistantId, role: "assistant", content: "" }],
      input: "",
      loading: true,
    })

    const apiMessages: ChatMessageItem[] = updated.map(({ role, content }) => ({ role, content }))
    const agentTitle = agent.title
    // Translate the dropdown sentinel into what the backend expects:
    //   AUTO  → omit flowId so the LLM router decides
    //   uuid  → forced selection
    const flowSelection = selectedFlowByAgent[agentId] ?? FLOW_AUTO
    const flowIdForSend = flowSelection === FLOW_AUTO ? undefined : flowSelection
    // Mint the session id BEFORE the request — the chat-flow router on the
    // backend skips the turn entirely without a conversationId, so deferring
    // generation to `saveAfterResponse` would lose flow routing on turn 1.
    const conversationId = ensureSessionId()

    await turChatService.sendAgentStream(
      agentId,
      llmId,
      apiMessages,
      (token) => {
        setAgentStates((prev) => {
          const s = prev[agentId] ?? { messages: [], input: "", loading: true, compacting: false }
          return {
            ...prev,
            [agentId]: {
              ...s,
              messages: s.messages.map((msg) =>
                msg.id === assistantId ? { ...msg, content: msg.content + token } : msg
              ),
            },
          }
        })
      },
      () => {
        setAgentStates((prev) => {
          const s = prev[agentId]
          return s ? { ...prev, [agentId]: { ...s, loading: false } } : prev
        })
        setTimeout(() => {
          const currentMsgs = agentStatesRef.current[agentId]?.messages ?? []
          saveAfterResponse(`agent:${agentId}`, currentMsgs, llmId)
        }, 0)
      },
      (error) => {
        console.error(error)
        toast.error(t("chat.failedAgentResponse", { agent: agentTitle ?? t("home.features.aiAgent.title") }))
        setAgentStates((prev) => {
          const s = prev[agentId]
          if (!s) return prev
          return {
            ...prev,
            [agentId]: {
              ...s,
              loading: false,
              messages: s.messages.filter((msg) => msg.id !== assistantId || msg.content !== ""),
            },
          }
        })
      },
      {
        conversationId,
        flowId: flowIdForSend,
        onOptions: (labels) => {
          // The backend emits this AFTER the assistant turn; attach to the just-streamed
          // assistant message so the chip row renders below it.
          setAgentStates((prev) => {
            const s = prev[agentId]
            if (!s) return prev
            return {
              ...prev,
              [agentId]: {
                ...s,
                messages: s.messages.map((msg) =>
                  msg.id === assistantId ? { ...msg, suggestedOptions: labels } : msg
                ),
              },
            }
          })
        },
      },
    )
  }, [agents, llmInstances, selectedLlmId, selectLlm, t, turChatService, saveAfterResponse, updateAgentState, selectedFlowByAgent, ensureSessionId])

  const handleAgentSend = () => {
    if (!activeAgentId) return
    const state = getAgentState(activeAgentId)
    void sendAgentPrompt(activeAgentId, state.input)
  }

  // Auto-send initial prompt from sessionStorage (set by home page ChatStarter).
  // Fires once an active agent is in scope; sendAgentPrompt itself resolves
  // a valid LLM, so we don't need to gate on selectedLlmId here.
  useEffect(() => {
    const prompt = sessionStorage.getItem(CHAT_INITIAL_PROMPT_KEY)
    if (!prompt || !activeAgentId || agents.length === 0) return
    sessionStorage.removeItem(CHAT_INITIAL_PROMPT_KEY)
    sessionStorage.removeItem(CHAT_INITIAL_AGENT_KEY)
    void sendAgentPrompt(activeAgentId, prompt)
  }, [activeAgentId, agents, sendAgentPrompt])

  const handleAgentCompact = async () => {
    if (!activeAgentId || !selectedLlmId) return
    const state = getAgentState(activeAgentId)
    if (state.loading || state.compacting || state.messages.length < 4) return

    updateAgentState(activeAgentId, { compacting: true })
    const text = state.messages.map((m) => `${m.role}: ${m.content}`).join("\n\n")
    let summary = ""
    const agentId = activeAgentId

    await turChatService.sendAgentStream(
      agentId,
      selectedLlmId,
      [{ role: "user", content: `Summarize the following conversation concisely, preserving key facts, decisions, and context needed to continue the conversation. Write the summary in the same language as the conversation.\n\n${text}` }],
      (token) => { summary += token },
      () => {
        updateAgentState(agentId, {
          messages: [{ id: generateId(), role: "assistant", content: `${t("chat.contextCompactedLabel")}\n\n${summary}` }],
          compacting: false,
        })
        toast.success(t("chat.contextCompacted"))
      },
      (error) => { console.error(error); toast.error(t("chat.failedCompact")); updateAgentState(agentId, { compacting: false }) },
    )
  }

  // ── Shared handlers ──

  const handleNewChat = () => {
    if (activeAgentId) {
      updateAgentState(activeAgentId, { messages: [], input: "" })
    }
    resetSession()
    setSidebarOpen(true)
  }

  const handleModelChange = (v: string) => {
    selectLlm(v)
    setAgentStates({})
    resetSession()
  }

  const handleRestoreSession = (session: ChatSession) => {
    if (!session.tab.startsWith("agent:")) {
      // Old chat/semantic sessions are no longer rendered — ignore.
      return
    }
    setActiveTab(session.tab)
    setActiveSessionId(session.id)
    const agentId = session.tab.slice(6)
    updateAgentState(agentId, { messages: session.messages as ChatMessage[], input: "" })
    if (session.llmId && llmInstances.some((i) => i.id === session.llmId)) {
      selectLlm(session.llmId)
    }
    setSidebarOpen(false)
  }

  // Derived
  const currentAgentState = activeAgentId ? getAgentState(activeAgentId) : null
  const messages = currentAgentState?.messages ?? []
  const isEmpty = messages.length === 0
  const modelLabel = selectedInstance ? `${selectedInstance.turLLMVendor?.id ?? ""} · ${selectedInstance.modelName ?? ""}` : "No model selected"

  if (loaded && llmInstances.length === 0) {
    return (
      <>
        <PageHeader turIcon={IconMessageChatbot} title={t("chat.chatTab")} />
        <BlankSlate
          icon={IconMessageChatbot}
          title={t("chat.noLlmAvailable")}
          description={t("chat.noLlmDescription")}
          buttonText={t("chat.createLlmInstance")}
          urlNew={ROUTES.LLM_INSTANCE + "/new"}
        />
      </>
    )
  }

  if (agentsLoaded && agents.length === 0) {
    return (
      <>
        <PageHeader turIcon={IconMessageChatbot} title={t("chat.chatTab")} />
        <BlankSlate
          icon={IconRobot}
          title={t("chat.noAgentAvailable")}
          description={t("chat.noAgentDescription")}
          buttonText={t("chat.createAiAgent")}
          urlNew={ROUTES.AI_AGENT_INSTANCE + "/new"}
        />
      </>
    )
  }

  return (
    <div className="flex h-[calc(100dvh-2rem)]">
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
          llmInstances={visibleLlmInstances}
          selectedLlmId={selectedLlmId}
          onModelChange={handleModelChange}
          onToggleSidebar={() => setSidebarOpen(!sidebarOpen)}
          hasMessages={!isEmpty}
          onNewChat={handleNewChat}
          agents={agents}
          activeSessionId={activeSessionId}
        />

        {/* Agent Tabs */}
        {activeAgentId && currentAgentState && (
          <>
            <div className="flex-1 overflow-y-auto">
              {currentAgentState.messages.length === 0
                ? <ChatEmptyState variant="agent" selectedInstance={selectedInstance} agentTitle={activeAgent?.title} agentIcon={activeAgent?.icon} />
                : <ChatMessageList
                    messages={currentAgentState.messages}
                    loading={currentAgentState.loading}
                    assistantName={activeAgent?.title ?? "Agent"}
                    assistantIcon={activeAgent?.icon}
                    endRef={agentEndRef}
                    onOptionClick={(label) => { void sendAgentPrompt(activeAgentId, label) }}
                  />
              }
            </div>
            <div className="shrink-0 border-t bg-background">
              <div className="max-w-3xl mx-auto px-4 py-4">
                {routineWaiting.waiting && (
                  <div className="mb-2 flex items-center gap-2 rounded-md border border-lime-500/40 bg-lime-500/10 px-3 py-2 text-xs text-lime-900 dark:text-lime-100">
                    <IconLoader2 className="size-3.5 animate-spin" />
                    <span>
                      {t("chat.scheduleAgent.waiting", {
                        count: routineWaiting.routineIds.length,
                        defaultValue:
                          routineWaiting.routineIds.length > 1
                            ? "Aguardando {{count}} rotinas…"
                            : "Aguardando rotina…",
                      })}
                    </span>
                  </div>
                )}
                <ChatInput
                  value={currentAgentState.input}
                  onChange={(v) => updateAgentState(activeAgentId, { input: v })}
                  onSend={handleAgentSend}
                  loading={currentAgentState.loading}
                  disabled={!selectedLlmId}
                  placeholder={selectedLlmId ? t("chat.askAgent", { agent: activeAgent?.title ?? t("home.features.aiAgent.title") }) : t("chat.selectModelToStart")}
                  accentColor="violet"
                />
                <div className="flex items-center justify-between mt-2 gap-4">
                  <div className="flex items-center gap-3 min-w-0">
                    <span className="text-xs text-muted-foreground shrink-0">{modelLabel}</span>
                    {activeFlows.length > 0 && (
                      <>
                        <Select
                          value={selectedFlowId}
                          onValueChange={(value) => {
                            setSelectedFlowByAgent((prev) => ({ ...prev, [activeAgentId]: value }))
                          }}
                        >
                          <SelectTrigger className="h-7 text-xs w-44">
                            <SelectValue placeholder={t("chatFlow.selector.placeholder")} />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value={FLOW_AUTO}>{t("chatFlow.selector.auto")}</SelectItem>
                            {activeFlows.map((flow) => (
                              <SelectItem key={flow.id} value={flow.id ?? ""}>
                                {flow.name}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        {canResetFlow && (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7 shrink-0"
                            title={t("chatFlow.reset.label")}
                            aria-label={t("chatFlow.reset.label")}
                            onClick={handleResetFlow}
                          >
                            <IconRefresh className="size-3.5" />
                          </Button>
                        )}
                      </>
                    )}
                  </div>
                  {currentAgentState.messages.length > 0 && (
                    <ContextBar
                      tokens={agentContextUsage.tokens}
                      percentage={agentContextUsage.percentage}
                      contextWindow={contextWindow}
                      compacting={currentAgentState.compacting}
                      canCompact={!currentAgentState.loading && currentAgentState.messages.length >= 4}
                      onCompact={handleAgentCompact}
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
