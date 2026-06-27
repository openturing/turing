"use client"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { toast } from "@viglet/viglet-design-system"
import { useTranslation } from "react-i18next"
import { IconLoader2, IconRefresh } from "@tabler/icons-react"
import {
  useTuringChat,
  useTuringScheduleAgentWaiting,
  type ChatMessage as TuringChatMessage,
} from "@viglet/turing-react-sdk"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import {
  fetchPreflightCost,
  type TurPreflightQuote,
} from "@/services/agent/agent-preflight-cost.service"
import type { TurAIAgent } from "@/models/agent/ai-agent.model"
import type { TurChatFlow } from "@/models/agent/chat-flow.model"
import type { TurSkillSummary } from "@/models/skill/skill.model"
import { estimateTokens, toAdminMessages } from "../chat.types"
import { ChatEmptyState } from "./chat-empty-state"
import { ChatInput } from "./chat-input"
import { ChatMessageList } from "./chat-message-list"
import { ContextBar } from "./context-bar"

/** Sentinel: dropdown option that lets the backend's LLM router decide. */
export const FLOW_AUTO = "__auto__"

/** Sentinel (T325): "no skill mode" — offer all skills via progressive disclosure. */
export const SKILL_NONE = "__none__"

/** Subset of the LLM instance these views read for the empty-state label. */
interface DisplayInstance {
  title: string
  modelName?: string
  turLLMVendor?: { id: string } | null
}

interface AgentChatTabProps {
  agent: TurAIAgent
  /** Resolved LLM for this tab — guaranteed valid for the agent by the parent. */
  llmInstanceId: string
  selectedInstance?: DisplayInstance
  modelLabel: string
  contextWindow: number
  /** Controlled backend conversation id (== the host's IndexedDB session id). */
  conversationId?: string
  /** Seed transcript (restored session / post-compaction summary), SDK shape. */
  initialMessages: TuringChatMessage[]
  /** Forced flow id, or undefined for the auto-router. */
  flowId?: string
  // ── Flow selector ──
  activeFlows: TurChatFlow[]
  selectedFlowId: string
  onFlowChange: (value: string) => void
  // ── Skill-mode selector (T325) — all optional; absent = legacy all-skills ──
  /** Pinned skill id sent on each turn, or undefined for all-skills mode. */
  selectedSkillIdForSend?: string
  /** Whether the skill-mode selector should render (agent.skillsEnabled + catalog non-empty). */
  skillModeEnabled?: boolean
  /** Enabled skills the user can pin as a distinct conversation mode. */
  skills?: TurSkillSummary[]
  /** Currently selected skill id, or {@link SKILL_NONE}. */
  selectedSkillId?: string
  onSkillChange?: (value: string) => void
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
 * The disabled placeholder shown when the active agent has no usable LLM —
 * `useTuringChat` can't mount in agent mode without a valid LLM id, so this
 * sibling renders in {@link AgentChatTab}'s place (no chat hook).
 *
 * @since 2026.3.1
 */
export function AgentNoLlmFallback({
  agent,
  selectedInstance,
}: Readonly<{ agent: TurAIAgent; selectedInstance?: DisplayInstance }>) {
  const { t } = useTranslation()
  return (
    <>
      <div className="flex-1 overflow-y-auto">
        <ChatEmptyState
          variant="agent"
          selectedInstance={selectedInstance}
          agentTitle={agent.title}
          agentIcon={agent.icon}
        />
      </div>
      <div className="shrink-0 border-t bg-background">
        <div className="max-w-3xl mx-auto px-4 py-4">
          <ChatInput
            value=""
            onChange={() => {}}
            onSend={() => {}}
            loading={false}
            disabled
            placeholder={t("chat.selectModelToStart")}
            accentColor="violet"
          />
        </div>
      </div>
    </>
  )
}

/**
 * Owns one agent tab's chat lifecycle through the SDK hook trio —
 * {@code useTuringChat} (agent mode) for the conversation, plus
 * {@code useTuringScheduleAgentWaiting} (which wraps {@code useTuringSlots})
 * for the "Aguardando rotina…" banner. Replaces the former page-level
 * imperative agent streaming: messages, loading, abort, and chip options all
 * come from the hook. The parent keeps session persistence, the LLM/flow
 * pickers, and compaction orchestration. Remounting on a key change
 * (conversation id / epoch) re-seeds {@link initialMessages} for restore +
 * post-compaction summary.
 *
 * @since 2026.3.1
 */
export function AgentChatTab({
  agent,
  llmInstanceId,
  selectedInstance,
  modelLabel,
  contextWindow,
  conversationId,
  initialMessages,
  flowId,
  activeFlows,
  selectedFlowId,
  onFlowChange,
  selectedSkillIdForSend,
  skillModeEnabled = false,
  skills = [],
  selectedSkillId = SKILL_NONE,
  onSkillChange,
  compacting,
  onCompactRequested,
  onResponseComplete,
  onHasMessagesChange,
  autoSendPrompt,
  onAutoSendConsumed,
}: Readonly<AgentChatTabProps>) {
  const { t } = useTranslation()
  const [input, setInput] = useState("")
  const endRef = useRef<HTMLDivElement>(null)

  const { messages, isStreaming, status, send, resetFlow } = useTuringChat({
    agent: { id: agent.id, llmInstanceId },
    flowId,
    selectedSkillId: selectedSkillIdForSend,
    conversationId,
    initialMessages,
  })

  // T48 — poll the agent's slot snapshot while a conversation is live so the
  // input area can render an "Aguardando rotina…" banner whenever the engine
  // parks on a scheduleAgent node. Polling (2s) because the agent-side slot
  // stream isn't wired into the console yet.
  const routineWaiting = useTuringScheduleAgentWaiting({
    agentId: agent.id,
    conversationId,
  })

  // Keep a synchronous mirror so the turn-complete effect saves the final
  // transcript without re-subscribing on every token.
  const messagesRef = useRef(messages)
  messagesRef.current = messages

  // Auto-scroll to the latest message.
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [messages])

  // Surface "has messages" to the parent (drives the header's New chat button).
  useEffect(() => {
    onHasMessagesChange?.(messages.length > 0)
  }, [messages.length, onHasMessagesChange])

  // Persist on completed turn — fire only on the loading→success edge so a
  // later identity change of `onResponseComplete` (while status is still
  // "success") doesn't trigger a duplicate save.
  const prevStatusRef = useRef(status)
  useEffect(() => {
    const prev = prevStatusRef.current
    prevStatusRef.current = status
    if (status === "success" && prev !== "success") {
      onResponseComplete(messagesRef.current)
    }
  }, [status, onResponseComplete])

  // T162 / §X.8.g — pre-flight cost gate. Holds the pending text + quote while
  // the user confirms a turn projected to breach the agent's per-turn soft cap.
  const [costGate, setCostGate] = useState<{ text: string; quote: TurPreflightQuote } | null>(null)

  const dispatchSend = useCallback(
    (text: string) => {
      setInput("")
      void send(text)
    },
    [send],
  )

  const handleSend = useCallback(() => {
    const text = input.trim()
    if (!text || isStreaming || !llmInstanceId) return
    // Quote first; only gate when the projected cost breaches the soft cap.
    void fetchPreflightCost(agent.id, text).then((quote) => {
      if (quote.enabled && quote.overSoftCap) {
        setCostGate({ text, quote })
      } else {
        dispatchSend(text)
      }
    })
  }, [input, isStreaming, llmInstanceId, agent.id, dispatchSend])

  const confirmCostGate = useCallback(() => {
    if (costGate) {
      dispatchSend(costGate.text)
      setCostGate(null)
    }
  }, [costGate, dispatchSend])

  const handleOptionClick = useCallback(
    (label: string) => {
      if (isStreaming || !llmInstanceId) return
      void send(label)
    },
    [isStreaming, llmInstanceId, send],
  )

  // Auto-send the prompt forwarded from the home page, once, after the LLM
  // resolves. `autoSentRef` guards against a double-fire on re-render; the
  // parent clears `autoSendPrompt` via `onAutoSendConsumed` to guard remounts.
  const autoSentRef = useRef(false)
  useEffect(() => {
    if (autoSentRef.current || !autoSendPrompt || !llmInstanceId) return
    autoSentRef.current = true
    void send(autoSendPrompt)
    onAutoSendConsumed?.()
  }, [autoSendPrompt, llmInstanceId, send, onAutoSendConsumed])

  const canResetFlow = Boolean(flowId) && Boolean(conversationId)
  const handleResetFlow = useCallback(async () => {
    try {
      await resetFlow()
      toast.success(t("chatFlow.reset.done"))
    } catch (error) {
      console.error("Reset flow state failed", error)
      toast.error(t("chatFlow.reset.failed"))
    }
  }, [resetFlow, t])

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
            variant="agent"
            selectedInstance={selectedInstance}
            agentTitle={agent.title}
            agentIcon={agent.icon}
          />
        ) : (
          <ChatMessageList
            messages={adminMessages}
            loading={isStreaming}
            assistantName={agent.title ?? "Agent"}
            assistantIcon={agent.icon}
            endRef={endRef}
            onOptionClick={handleOptionClick}
          />
        )}
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
            value={input}
            onChange={setInput}
            onSend={handleSend}
            loading={isStreaming}
            disabled={!llmInstanceId}
            placeholder={
              llmInstanceId
                ? t("chat.askAgent", { agent: agent.title ?? t("home.features.aiAgent.title") })
                : t("chat.selectModelToStart")
            }
            accentColor="violet"
          />
          <div className="flex items-center justify-between mt-2 gap-4">
            <div className="flex items-center gap-3 min-w-0">
              <span className="text-xs text-muted-foreground shrink-0">{modelLabel}</span>
              {activeFlows.length > 0 && (
                <>
                  <Select value={selectedFlowId} onValueChange={onFlowChange}>
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
              {skillModeEnabled && (
                <Select value={selectedSkillId} onValueChange={onSkillChange ?? (() => {})}>
                  <SelectTrigger className="h-7 text-xs w-44">
                    <SelectValue placeholder={t("chat.skill.selector.placeholder")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={SKILL_NONE}>{t("chat.skill.selector.auto")}</SelectItem>
                    {skills.map((skill) => (
                      <SelectItem key={skill.id} value={skill.id}>
                        {skill.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>
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

      {/* T162 / §X.8.g — pre-flight cost confirmation gate. */}
      <Dialog open={costGate !== null} onOpenChange={(open) => !open && setCostGate(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("chat.costGate.title", { defaultValue: "Confirm turn cost" })}</DialogTitle>
            <DialogDescription>
              {t("chat.costGate.body", {
                defaultValue:
                  "This turn is projected to cost about ${{cost}} ({{tokens}} input tokens){{approx}}, above the {{cap}} per-turn limit. Send anyway?",
                cost: costGate ? costGate.quote.estimatedCostUsd.toFixed(2) : "0.00",
                tokens: costGate ? costGate.quote.inputTokens : 0,
                approx: costGate && !costGate.quote.exact ? " (estimated)" : "",
                cap: costGate?.quote.softCapUsd != null ? `$${costGate.quote.softCapUsd.toFixed(2)}` : "",
              })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCostGate(null)}>
              {t("common.cancel", { defaultValue: "Cancel" })}
            </Button>
            <Button onClick={confirmCostGate}>
              {t("chat.costGate.confirm", { defaultValue: "Send anyway" })}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
