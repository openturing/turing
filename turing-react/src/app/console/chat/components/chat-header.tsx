import { ModeToggle } from "@/components/mode-toggle"
import { GradientButton } from "@/components/ui/gradient-button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { TurLLMInstance } from "@/models/llm/llm-instance.model"
import { IconCompass, IconHistory, IconMessageCircle } from "@tabler/icons-react"

interface ChatHeaderProps {
  activeTab: "chat" | "semantic"
  onTabChange: (tab: "chat" | "semantic") => void
  llmInstances: TurLLMInstance[]
  selectedLlmId: string
  onModelChange: (id: string) => void
  onToggleSidebar: () => void
  hasMessages: boolean
  onNewChat: () => void
}

export function ChatHeader({ activeTab, onTabChange, llmInstances, selectedLlmId, onModelChange, onToggleSidebar, hasMessages, onNewChat }: ChatHeaderProps) {
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

      <div className="flex items-center rounded-full bg-muted p-1 gap-0.5">
        <button
          type="button"
          onClick={() => onTabChange("chat")}
          className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-medium transition-all ${activeTab === "chat" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}
        >
          <IconMessageCircle className="size-4" />
          Chat
        </button>
        <button
          type="button"
          onClick={() => onTabChange("semantic")}
          className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-medium transition-all ${activeTab === "semantic" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}
        >
          <IconCompass className="size-4" />
          Semantic Navigation
        </button>
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
