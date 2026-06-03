import { Icon } from "@iconify/react"
import { ModeToggle } from "@/components/mode-toggle"
import { GradientButton } from "@/components/ui/gradient-button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { TurAIAgent } from "@/models/agent/ai-agent.model"
/** Picker-shape consumed by this header — accepts both admin's `TurLLMInstance`
 *  and the SDK's `TurLlmInstance` since only `id` and `title` are read here. */
interface PickerInstance {
  id: string
  title: string
}
import { IconHistory, IconPlus, IconRobot } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { ChatSessionInfoSheet } from "./chat-session-info-sheet"

interface ChatHeaderProps {
  activeTab: string
  onTabChange: (tab: string) => void
  llmInstances: PickerInstance[]
  selectedLlmId: string
  onModelChange: (id: string) => void
  onToggleSidebar: () => void
  hasMessages: boolean
  onNewChat: () => void
  agents: TurAIAgent[]
  activeSessionId: string | null
}

export function ChatHeader({ activeTab, onTabChange, llmInstances, selectedLlmId, onModelChange, onToggleSidebar, hasMessages, onNewChat, agents, activeSessionId }: ChatHeaderProps) {
  const { t } = useTranslation()
  // Agent tabs are keyed `agent:<id>`; extract the id so the session-info
  // sheet can stream that agent's per-conversation workspace artifacts (T113).
  const activeAgentId = activeTab.startsWith("agent:") ? activeTab.slice(6) : null

  return (
    <div className="border-b shrink-0">
      {/* Row 1: Controls */}
      <div className="flex items-center gap-2 px-3 py-2">
        <button type="button" onClick={onToggleSidebar} title={t("chat.sessions")} className="text-muted-foreground hover:text-foreground transition-colors p-1.5 rounded-md hover:bg-muted shrink-0">
          <IconHistory className="size-5" />
        </button>
        <ChatSessionInfoSheet conversationId={activeSessionId} agentId={activeAgentId} />
        <Select value={selectedLlmId} onValueChange={onModelChange}>
          <SelectTrigger className="flex-1 min-w-0">
            <SelectValue placeholder={t("chat.selectModel")} />
          </SelectTrigger>
          <SelectContent>
            {llmInstances.map((instance) => (
              <SelectItem key={instance.id} value={instance.id}>{instance.title}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        {hasMessages && (
          <GradientButton variant="outline" size="sm" onClick={onNewChat} className="shrink-0">
            <IconPlus className="size-4 md:mr-1" />
            <span className="hidden md:inline">{t("chat.newChat")}</span>
          </GradientButton>
        )}
        <div className="hidden md:block shrink-0">
          <ModeToggle />
        </div>
      </div>

      {/* Row 2: Tabs — agent-only. The first agent is the global default. */}
      <div className="flex items-center justify-center px-3 pb-2 overflow-x-auto gap-1">
        {agents.map((agent) => (
          <button
            key={agent.id}
            type="button"
            onClick={() => onTabChange(`agent:${agent.id}`)}
            className={`flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm font-medium transition-all whitespace-nowrap shrink-0 ${activeTab === `agent:${agent.id}` ? "bg-muted text-foreground" : "text-muted-foreground hover:text-foreground hover:bg-muted/50"}`}
          >
            {agent.icon ? (
              <Icon icon={agent.icon} className="size-4" />
            ) : (
              <IconRobot className="size-4" />
            )}
            {agent.title}
          </button>
        ))}
      </div>
    </div>
  )
}
