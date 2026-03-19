import { ModeToggle } from "@/components/mode-toggle"
import { GradientAvatar, GradientAvatarFallback, GradientAvatarImage } from "@/components/ui/gradient-avatar"
import { GradientButton } from "@/components/ui/gradient-button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { TurAIAgent } from "@/models/agent/ai-agent.model"
import type { TurLLMInstance } from "@/models/llm/llm-instance.model"
import { IconCompass, IconHistory, IconMessageCircle, IconRobot } from "@tabler/icons-react"

interface ChatHeaderProps {
  activeTab: string
  onTabChange: (tab: string) => void
  llmInstances: TurLLMInstance[]
  selectedLlmId: string
  onModelChange: (id: string) => void
  onToggleSidebar: () => void
  hasMessages: boolean
  onNewChat: () => void
  agents: TurAIAgent[]
}

export function ChatHeader({ activeTab, onTabChange, llmInstances, selectedLlmId, onModelChange, onToggleSidebar, hasMessages, onNewChat, agents }: ChatHeaderProps) {
  return (
    <div className="flex items-center justify-between border-b px-4 py-3 shrink-0 gap-3">
      <div className="flex items-center gap-2 flex-1 min-w-0">
        <button type="button" onClick={onToggleSidebar} title="Sessions" className="text-muted-foreground hover:text-foreground transition-colors p-1.5 rounded-md hover:bg-muted">
          <IconHistory className="size-5" />
        </button>
        <Select value={selectedLlmId} onValueChange={onModelChange}>
          <SelectTrigger className="w-56">
            <SelectValue placeholder="Select a model..." />
          </SelectTrigger>
          <SelectContent>
            {llmInstances.map((instance) => (
              <SelectItem key={instance.id} value={instance.id}>{instance.title}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="flex items-center rounded-full bg-muted p-1 gap-0.5 overflow-x-auto max-w-[60%]">
        <button
          type="button"
          onClick={() => onTabChange("chat")}
          className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-medium transition-all whitespace-nowrap ${activeTab === "chat" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}
        >
          <IconMessageCircle className="size-4" />
          Chat
        </button>
        <button
          type="button"
          onClick={() => onTabChange("semantic")}
          className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-medium transition-all whitespace-nowrap ${activeTab === "semantic" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}
        >
          <IconCompass className="size-4" />
          Semantic Navigation
        </button>
        {agents.map((agent) => (
          <button
            key={agent.id}
            type="button"
            onClick={() => onTabChange(`agent:${agent.id}`)}
            className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-medium transition-all whitespace-nowrap ${activeTab === `agent:${agent.id}` ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}
          >
            {agent.avatarUrl ? (
              <GradientAvatar className="size-5">
                <GradientAvatarImage src={agent.avatarUrl} alt={agent.title} />
                <GradientAvatarFallback className="text-[8px]">
                  <IconRobot className="size-3" />
                </GradientAvatarFallback>
              </GradientAvatar>
            ) : (
              <IconRobot className="size-4" />
            )}
            {agent.title}
          </button>
        ))}
      </div>

      <div className="flex items-center gap-3 flex-1 min-w-0 justify-end">
        {hasMessages && (
          <GradientButton variant="outline" size="sm" onClick={onNewChat}>
            New Chat
          </GradientButton>
        )}
        <ModeToggle />
      </div>
    </div>
  )
}
