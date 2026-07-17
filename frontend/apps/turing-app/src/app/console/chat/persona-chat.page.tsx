"use client"
import {
  cloneElement,
  isValidElement,
  useCallback,
  useEffect,
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
import type { TurPersona } from "@/models/persona/persona.model"
import { TurPersonaService } from "@/services/persona/persona.service"
import type { ChatSession } from "@/services/chat/chat-session.service"
import { IconHistory, IconMasksTheater, IconMessageChatbot, IconPlus } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import {
  postPersonaChat,
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
import { PersonaChatTab } from "./components/persona-chat-tab"
import { useChatSession } from "./hooks/use-chat-session"

const turPersonaService = new TurPersonaService()

/**
 * Block AI / §XXXII.4 (T581) — the persona chat workspace. The persona-mode
 * peer of {@link ChatPage}: it reuses the same session sidebar, model picker
 * header and presentational chat stack, but scopes the conversation to a single
 * {@link TurPersona} via {@link PersonaChatTab} (which posts to the T578 persona
 * endpoint). Any enabled LLM instance is offered — personas have no per-entity
 * LLM allow-list. Reached from `/bento/chat/persona/:personaId` (T580).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function PersonaChatPage({
  personaId,
  emptyStateHeader,
}: Readonly<{ personaId: string; emptyStateHeader?: ReactNode }>) {
  const { t } = useTranslation()
  const tabId = `persona:${personaId}`

  const [persona, setPersona] = useState<TurPersona | null>(null)
  const [personaLoaded, setPersonaLoaded] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)

  // Per-tab chat hosting — same remount-on-epoch pattern as ChatPage.
  const [chatEpoch, setChatEpoch] = useState(0)
  const [seedMessages, setSeedMessages] = useState<TuringChatMessage[]>([])
  const [hasMessages, setHasMessages] = useState(false)
  const [compacting, setCompacting] = useState(false)
  const [pendingAutoSend, setPendingAutoSend] = useState<string | null>(null)

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

  // Persona chat runs on any enabled instance; the resolved id is the user's
  // selection, or the first instance until one is picked.
  const effectiveLlmId = selectedLlmId || llmInstances[0]?.id || ""

  const getLlmTitle = useCallback(
    (llmId: string) => {
      const inst = llmInstances.find((i) => i.id === llmId)
      return inst ? { title: inst.title, modelName: inst.modelName } : undefined
    },
    [llmInstances],
  )

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

  // ── Handlers ──

  const handleResponseComplete = useCallback(
    (msgs: TuringChatMessage[]) => {
      void saveAfterResponse(tabId, toAdminMessages(msgs), effectiveLlmId)
    },
    [tabId, effectiveLlmId, saveAfterResponse],
  )

  // Compaction: summarize via a one-off persona call, then reseed with the
  // summary as a single assistant message (bumped epoch, same conversation id).
  const handleCompactRequested = useCallback(
    async (current: TuringChatMessage[]) => {
      if (!effectiveLlmId || compacting || current.length < 4) return
      setCompacting(true)
      const text = current.map((m) => `${m.role}: ${m.content}`).join("\n\n")
      let summary = ""
      try {
        await postPersonaChat(
          personaId,
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
    [personaId, effectiveLlmId, compacting, t],
  )

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
      if (session.tab !== tabId) return // only this persona's sessions
      setActiveSessionId(session.id)
      setSeedMessages(toTuringMessages(session.messages as ChatMessage[]))
      setHasMessages(session.messages.length > 0)
      setChatEpoch((e) => e + 1)
      if (session.llmId && llmInstances.some((i) => i.id === session.llmId)) {
        selectLlm(session.llmId)
      }
      setSidebarOpen(false)
    },
    [tabId, setActiveSessionId, llmInstances, selectLlm],
  )

  const handleAutoSendConsumed = useCallback(() => setPendingAutoSend(null), [])

  // ── Effects ──

  // Load the persona. A remount (key on personaId) re-runs this cleanly.
  useEffect(() => {
    let alive = true
    turPersonaService
      .get(personaId)
      .then((p) => { if (alive) setPersona(p) })
      .catch(() => { if (alive) setPersona(null) })
      .finally(() => { if (alive) setPersonaLoaded(true) })
    return () => { alive = false }
  }, [personaId])

  // Capture the initial prompt forwarded from a launch surface (auto-send once).
  useEffect(() => {
    const prompt = sessionStorage.getItem(CHAT_INITIAL_PROMPT_KEY)
    if (prompt) {
      sessionStorage.removeItem(CHAT_INITIAL_PROMPT_KEY)
      setPendingAutoSend(prompt)
    }
  }, [])

  // Eagerly mint a session id so the child's controlled conversationId is
  // stable for the local session store from the first turn.
  useEffect(() => {
    if (persona && !activeSessionId) {
      ensureSessionId()
    }
  }, [persona, activeSessionId, ensureSessionId])

  useEffect(() => { loadSessions() }, [loadSessions])

  // ── Early returns ──

  const blankSlateHeader = emptyStateHeader ?? (
    <PageHeader turIcon={IconMasksTheater} title={t("persona.title", { defaultValue: "Persona" })} />
  )

  // Chat controls (session history · session info · new chat) hosted in the
  // BentoHero's `trailing` slot instead of a header bar, to free vertical space
  // (injected via `cloneElement` so the wrapper keeps owning the hero).
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
      <ChatSessionInfoSheet conversationId={activeSessionId} agentId={null} />
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

  if (personaLoaded && !persona) {
    return (
      <>
        {blankSlateHeader}
        <BlankSlate
          icon={IconMasksTheater}
          title={t("persona.notFound.title", { defaultValue: "Persona not found" })}
          description={t("persona.notFound.description", {
            defaultValue: "This persona no longer exists or is not available.",
          })}
          buttonText={t("persona.title", { defaultValue: "Personas" })}
          urlNew={ROUTES.BENTO_PERSONA_INSTANCE}
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
          sessions={sessions.filter((s) => s.tab === tabId)}
          activeSessionId={activeSessionId}
          onRestore={handleRestoreSession}
          onDelete={handleDeleteSession}
        />

        <div className="flex flex-col flex-1 min-w-0">
          {persona && effectiveLlmId && (
            <PersonaChatTab
              key={`${personaId}:${chatEpoch}`}
              persona={persona}
              llmInstanceId={effectiveLlmId}
              selectedInstance={selectedInstance}
              contextWindow={contextWindow}
              llmInstances={llmInstances}
              selectedLlmId={selectedLlmId}
              onModelChange={handleModelChange}
              conversationId={activeSessionId ?? undefined}
              initialMessages={seedMessages}
              compacting={compacting}
              onCompactRequested={handleCompactRequested}
              onResponseComplete={handleResponseComplete}
              onHasMessagesChange={setHasMessages}
              autoSendPrompt={pendingAutoSend}
              onAutoSendConsumed={handleAutoSendConsumed}
            />
          )}
        </div>
      </div>
    </div>
  )
}
