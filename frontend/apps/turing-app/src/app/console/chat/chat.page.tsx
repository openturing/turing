"use client"
import {
  cloneElement,
  isValidElement,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactElement,
  type ReactNode,
} from "react"
import { toast } from "@viglet/viglet-design-system"
import "./chat-highlight.css"

import { ROUTES } from "@/app/routes.const"
import { BlankSlate } from "@/components/blank-slate"
import { GradientButton } from "@/components/ui/gradient-button"
import { PageHeader } from "@/components/page-header"
import type { TurAIAgent } from "@/models/agent/ai-agent.model"
import type { TurChatFlow } from "@/models/agent/chat-flow.model"
import type { TurSkillSummary } from "@/models/skill/skill.model"
import { TurAIAgentService } from "@/services/agent/ai-agent.service"
import { TurChatFlowService } from "@/services/agent/chat-flow.service"
import type { ChatSession } from "@/services/chat/chat-session.service"
import { TurSkillService } from "@/services/skill/skill.service"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import { IconHistory, IconMessageChatbot, IconPlus, IconRobot } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import {
  postAgentChat,
  useTuringLlmInstances,
  type ChatMessage as TuringChatMessage,
} from "@viglet/turing-react-sdk"
import {
  CHAT_INITIAL_PROMPT_KEY,
  DEFAULT_CONTEXT_WINDOW,
  LLM_STORAGE_KEY,
  type ChatMessage,
  generateId,
  toAdminMessages,
  toTuringMessages,
} from "./chat.types"
import { ChatSessionInfoSheet } from "./components/chat-session-info-sheet"
import { ChatSessionSidebar } from "./components/chat-session-sidebar"
import { AgentChatTab, AgentNoLlmFallback, FLOW_AUTO, SKILL_NONE } from "./components/agent-chat-tab"
import { useChatSession } from "./hooks/use-chat-session"

const turAIAgentService = new TurAIAgentService()
const turGlobalSettingsService = new TurGlobalSettingsService()
const turChatFlowService = new TurChatFlowService()
const turSkillService = new TurSkillService()

/**
 * @param emptyStateHeader Optional page header rendered above the surface in
 * every state — the "no LLM" / "no agent" blank slates and the live chat alike
 * — so the chat matches the other Bento pages (a {@link BentoHero} in normal
 * flow, then a sized body below). Defaults to the console {@link PageHeader};
 * the Bento shell (T563) passes its own frosted {@code BentoHero} so the reused
 * surface never pulls the console {@code SidebarTrigger} into a shell that has
 * no SidebarProvider.
 * @param initialAgentId Block AI / §XXXII.3 (T580) — the agent to open on
 * mount, resolved from the deterministic `/bento/chat/agent/:agentId` route
 * (replaces the retired `CHAT_INITIAL_AGENT_KEY` sessionStorage hand-off). When
 * absent, the global default (or first) agent is selected as before.
 */
export default function ChatPage({
  emptyStateHeader,
  initialAgentId,
}: Readonly<{ emptyStateHeader?: ReactNode; initialAgentId?: string }> = {}) {
  const { t } = useTranslation()
  // Empty until agents load — then becomes `agent:<id>` of the default or first agent.
  const [activeTab, setActiveTab] = useState<string>("")
  const [sidebarOpen, setSidebarOpen] = useState(false)

  // AI Agents
  const [agents, setAgents] = useState<TurAIAgent[]>([])
  const [agentsLoaded, setAgentsLoaded] = useState(false)
  const [defaultAgentId, setDefaultAgentId] = useState<string>("")

  // Per-agent: list of flows fetched on demand + the currently selected flow.
  const [flowsByAgent, setFlowsByAgent] = useState<Record<string, TurChatFlow[]>>({})
  const [selectedFlowByAgent, setSelectedFlowByAgent] = useState<Record<string, string>>({})

  // T325 — enabled skills (catalog-wide) + the per-agent skill-mode pin. An
  // empty/SKILL_NONE pin means the agent offers all skills via progressive
  // disclosure (legacy); picking one runs that single skill as a distinct mode.
  const [skills, setSkills] = useState<TurSkillSummary[]>([])
  const [selectedSkillByAgent, setSelectedSkillByAgent] = useState<Record<string, string>>({})

  // Per-tab chat hosting. `chatEpoch` keys the AgentChatTab so a new chat /
  // session restore / post-compaction summary remounts it with a fresh seed.
  // `seedMessages` is the transcript handed to the child at mount.
  const [chatEpoch, setChatEpoch] = useState(0)
  const [seedMessages, setSeedMessages] = useState<TuringChatMessage[]>([])
  const [hasMessages, setHasMessages] = useState(false)
  const [compacting, setCompacting] = useState(false)
  const [pendingAutoSend, setPendingAutoSend] = useState<string | null>(null)

  // Hooks
  const {
    instances: llmInstances,
    loaded,
    selectedId: selectedLlmId,
    selectedInstance,
    contextWindow,
    select: selectLlm,
  } = useTuringLlmInstances({
    persistKey: LLM_STORAGE_KEY,
    defaultContextWindow: DEFAULT_CONTEXT_WINDOW,
  })

  // Active agent (if on agent tab)
  const activeAgentId = activeTab.startsWith("agent:") ? activeTab.slice(6) : null
  const activeAgent = activeAgentId ? agents.find((a) => a.id === activeAgentId) : null

  // LLM instances filtered by active agent
  const visibleLlmInstances = useMemo(() => {
    if (!activeAgent) return llmInstances
    const agentLlmIds = new Set(activeAgent.llmInstances.map((l) => l.id))
    return llmInstances.filter((i) => agentLlmIds.has(i.id))
  }, [activeAgent, llmInstances])

  // The LLM the active tab will send through: the user's selection when it
  // belongs to this agent, otherwise the agent's first LLM. Empty when the
  // agent has no usable LLM — the tab then renders a disabled fallback (the
  // chat hook can't mount in agent mode without a valid LLM id).
  const effectiveLlmId = useMemo(() => {
    if (visibleLlmInstances.some((i) => i.id === selectedLlmId)) return selectedLlmId
    return visibleLlmInstances[0]?.id ?? ""
  }, [visibleLlmInstances, selectedLlmId])

  const getLlmTitle = useCallback((llmId: string) => {
    const inst = llmInstances.find((i) => i.id === llmId)
    return inst ? { title: inst.title, modelName: inst.modelName } : undefined
  }, [llmInstances])

  const {
    sessions,
    activeSessionId,
    setActiveSessionId,
    ensureSessionId,
    loadSessions,
    saveAfterResponse,
    handleDeleteSession,
    resetSession,
  } = useChatSession(getLlmTitle)

  // Flow selection for the active agent. AUTO lets the backend router pick.
  const activeFlows = activeAgentId ? flowsByAgent[activeAgentId] ?? [] : []
  const selectedFlowId = activeAgentId ? selectedFlowByAgent[activeAgentId] ?? FLOW_AUTO : FLOW_AUTO
  const flowIdForSend = selectedFlowId === FLOW_AUTO ? undefined : selectedFlowId

  // T325 — skill-mode selection for the active agent. Only meaningful when the
  // agent has skills enabled and the catalog has any enabled skill. SKILL_NONE
  // (the default) keeps the legacy all-skills progressive-disclosure behaviour.
  const skillModeEnabled = Boolean(activeAgent?.skillsEnabled) && skills.length > 0
  const selectedSkillId =
    activeAgentId ? selectedSkillByAgent[activeAgentId] ?? SKILL_NONE : SKILL_NONE
  const selectedSkillIdForSend =
    skillModeEnabled && selectedSkillId !== SKILL_NONE ? selectedSkillId : undefined

  // ── Handlers ──

  // Save the completed turn to the session store. The conversation id the
  // child sends under is `activeSessionId` (controlled), so the persisted
  // session keys to the same id `saveAfterResponse` reads from its ref.
  const handleResponseComplete = useCallback(
    (msgs: TuringChatMessage[]) => {
      if (!activeAgentId) return
      void saveAfterResponse(`agent:${activeAgentId}`, toAdminMessages(msgs), effectiveLlmId)
    },
    [activeAgentId, effectiveLlmId, saveAfterResponse],
  )

  // Compaction: summarize the current transcript via a one-off agent call,
  // then reseed the tab with the summary as a single assistant message
  // (remount on a bumped epoch, keeping the same conversation id).
  const handleCompactRequested = useCallback(
    async (current: TuringChatMessage[]) => {
      if (!activeAgentId || !effectiveLlmId || compacting || current.length < 4) return
      setCompacting(true)
      const text = current.map((m) => `${m.role}: ${m.content}`).join("\n\n")
      let summary = ""
      try {
        await postAgentChat(
          activeAgentId,
          effectiveLlmId,
          [
            {
              role: "user",
              content: `Summarize the following conversation concisely, preserving key facts, decisions, and context needed to continue the conversation. Write the summary in the same language as the conversation.\n\n${text}`,
            },
          ],
          { onToken: (token) => { summary += token } },
        )
        const summaryMsg: TuringChatMessage = {
          id: generateId(),
          role: "assistant",
          content: `${t("chat.contextCompactedLabel")}\n\n${summary}`,
          timestamp: Date.now(),
        }
        setSeedMessages([summaryMsg])
        setChatEpoch((e) => e + 1)
        toast.success(t("chat.contextCompacted"))
      } catch (error) {
        console.error(error)
        toast.error(t("chat.failedCompact"))
      } finally {
        setCompacting(false)
      }
    },
    [activeAgentId, effectiveLlmId, compacting, t],
  )

  // Start a fresh chat on the active tab.
  const startFreshChat = useCallback(() => {
    setSeedMessages([])
    setHasMessages(false)
    setChatEpoch((e) => e + 1)
    resetSession()
  }, [resetSession])

  const handleNewChat = useCallback(() => {
    startFreshChat()
    setSidebarOpen(true)
  }, [startFreshChat])

  const handleModelChange = useCallback(
    (v: string) => {
      selectLlm(v)
      startFreshChat()
    },
    [selectLlm, startFreshChat],
  )

  const handleRestoreSession = useCallback(
    (session: ChatSession) => {
      if (!session.tab.startsWith("agent:")) {
        // Old chat/semantic sessions are no longer rendered — ignore.
        return
      }
      setActiveTab(session.tab)
      setActiveSessionId(session.id)
      setSeedMessages(toTuringMessages(session.messages as ChatMessage[]))
      setHasMessages(session.messages.length > 0)
      setChatEpoch((e) => e + 1)
      if (session.llmId && llmInstances.some((i) => i.id === session.llmId)) {
        selectLlm(session.llmId)
      }
      setSidebarOpen(false)
    },
    [setActiveSessionId, llmInstances, selectLlm],
  )

  const handleFlowChange = useCallback(
    (value: string) => {
      if (!activeAgentId) return
      setSelectedFlowByAgent((prev) => ({ ...prev, [activeAgentId]: value }))
    },
    [activeAgentId],
  )

  const handleSkillChange = useCallback(
    (value: string) => {
      if (!activeAgentId) return
      setSelectedSkillByAgent((prev) => ({ ...prev, [activeAgentId]: value }))
    },
    [activeAgentId],
  )

  const handleAutoSendConsumed = useCallback(() => setPendingAutoSend(null), [])

  // ── Effects ──

  // Load AI Agents + global default agent. Default agent appears first.
  useEffect(() => {
    Promise.all([
      turAIAgentService.query().catch(() => [] as TurAIAgent[]),
      turGlobalSettingsService.query().catch(() => null),
    ]).then(([all, settings]) => {
      const enabled = all.filter((a) => a.enabled === 1)
      const defaultId = settings?.defaultAiAgentId ?? ""
      setDefaultAgentId(defaultId)
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

  // T325 — load the enabled skill catalog once. Degrades to [] when storage is
  // disabled or the endpoint errors, so the skill-mode selector simply hides.
  useEffect(() => {
    turSkillService
      .query()
      .then((list) => setSkills(list.filter((s) => s.enabled)))
      .catch(() => setSkills([]))
  }, [])

  // Once agents have loaded, pick the initial active tab.
  // Priority: agent from the URL (T580) → global default → first agent.
  useEffect(() => {
    if (!agentsLoaded || activeTab) return
    if (agents.length === 0) return
    const routedAgent = initialAgentId ? agents.find((a) => a.id === initialAgentId) : null
    const target = routedAgent ?? agents.find((a) => a.id === defaultAgentId) ?? agents[0]
    setActiveTab(`agent:${target.id}`)
  }, [agentsLoaded, agents, activeTab, defaultAgentId, initialAgentId])

  // Capture the initial prompt forwarded from the home page ChatStarter. The
  // active tab's AgentChatTab auto-sends it once the LLM resolves.
  useEffect(() => {
    const prompt = sessionStorage.getItem(CHAT_INITIAL_PROMPT_KEY)
    if (prompt) {
      sessionStorage.removeItem(CHAT_INITIAL_PROMPT_KEY)
      setPendingAutoSend(prompt)
    }
  }, [])

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

  // Eagerly mint a session id when an agent tab becomes active so the child's
  // controlled `conversationId` is stable from its first turn (the chat-flow
  // router skips a turn entirely without a conversationId).
  useEffect(() => {
    if (activeAgentId && !activeSessionId) {
      ensureSessionId()
    }
  }, [activeAgentId, activeSessionId, ensureSessionId])

  // Auto-select first valid LLM when switching to an agent tab.
  useEffect(() => {
    if (activeAgent && visibleLlmInstances.length > 0) {
      const currentValid = visibleLlmInstances.some((i) => i.id === selectedLlmId)
      if (!currentValid) {
        selectLlm(visibleLlmInstances[0].id)
      }
    }
  }, [activeAgent, visibleLlmInstances, selectedLlmId, selectLlm])

  useEffect(() => { loadSessions() }, [loadSessions])

  // ── Early returns ──

  const blankSlateHeader = emptyStateHeader ?? (
    <PageHeader turIcon={IconMessageChatbot} title={t("chat.chatTab")} />
  )

  // Chat controls (session history · session info · new chat) — hosted in the
  // BentoHero's `trailing` slot instead of a dedicated header bar, to free the
  // vertical space that bar used to take. Injected into the passed hero via
  // `cloneElement` so the wrapper still owns the hero's title/icon/back-link.
  const headerActions = (
    <>
      <button
        type="button"
        onClick={() => setSidebarOpen((open) => !open)}
        title={t("chat.sessions")}
        aria-label={t("chat.sessions")}
        className="rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
      >
        <IconHistory className="size-5" />
      </button>
      <ChatSessionInfoSheet conversationId={activeSessionId} agentId={activeAgentId} />
      {hasMessages && (
        <GradientButton variant="outline" size="sm" onClick={handleNewChat} className="shrink-0">
          <IconPlus className="size-4 md:mr-1" />
          <span className="hidden md:inline">{t("chat.newChat")}</span>
        </GradientButton>
      )}
    </>
  )

  const headerWithActions = isValidElement(emptyStateHeader)
    ? cloneElement(emptyStateHeader as ReactElement<{ trailing?: ReactNode }>, {
        trailing: headerActions,
      })
    : emptyStateHeader

  if (loaded && llmInstances.length === 0) {
    return (
      <>
        {blankSlateHeader}
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
        {blankSlateHeader}
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
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="shrink-0">{headerWithActions}</div>
      <div className="flex min-h-0 flex-1">
        <ChatSessionSidebar
          open={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
          sessions={sessions}
          activeSessionId={activeSessionId}
          onRestore={handleRestoreSession}
          onDelete={handleDeleteSession}
        />

        <div className="flex flex-col flex-1 min-w-0">
          {/* Agent chat surface */}
          {activeAgentId && activeAgent && (
            effectiveLlmId ? (
              <AgentChatTab
                key={`${activeAgentId}:${chatEpoch}`}
                agent={activeAgent}
                llmInstanceId={effectiveLlmId}
                selectedInstance={selectedInstance}
                contextWindow={contextWindow}
                llmInstances={visibleLlmInstances}
                selectedLlmId={selectedLlmId}
                onModelChange={handleModelChange}
                conversationId={activeSessionId ?? undefined}
                initialMessages={seedMessages}
                flowId={flowIdForSend}
                activeFlows={activeFlows}
                selectedFlowId={selectedFlowId}
                onFlowChange={handleFlowChange}
                selectedSkillIdForSend={selectedSkillIdForSend}
                skillModeEnabled={skillModeEnabled}
                skills={skills}
                selectedSkillId={selectedSkillId}
                onSkillChange={handleSkillChange}
                compacting={compacting}
                onCompactRequested={handleCompactRequested}
                onResponseComplete={handleResponseComplete}
                onHasMessagesChange={setHasMessages}
                autoSendPrompt={pendingAutoSend}
                onAutoSendConsumed={handleAutoSendConsumed}
              />
            ) : (
              <AgentNoLlmFallback agent={activeAgent} selectedInstance={selectedInstance} />
            )
          )}
        </div>
      </div>
    </div>
  )
}
