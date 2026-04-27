import { useEffect, useState } from "react"
import { Icon } from "@iconify/react"
import { IconArrowRight, IconBulb, IconLoader2 } from "@tabler/icons-react"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import type { TurIntent } from "@/models/intent/intent.model"
import { TurIntentService } from "@/services/intent/intent.service"

const turIntentService = new TurIntentService()

interface ChatSuggestionsProps {
  onSelectPrompt: (prompt: string) => void
  /**
   * Agent whose intents drive the suggestion chips. When omitted (e.g. a chat
   * UI not yet bound to an agent), no suggestions are rendered.
   */
  agentId?: string
}

export function ChatSuggestions({ onSelectPrompt, agentId }: ChatSuggestionsProps) {
  const [intents, setIntents] = useState<TurIntent[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedIntent, setSelectedIntent] = useState<TurIntent | null>(null)

  useEffect(() => {
    if (!agentId) {
      setIntents([])
      setLoading(false)
      return
    }
    setLoading(true)
    turIntentService.queryEnabled(agentId)
      .then(setIntents)
      .catch(() => setIntents([]))
      .finally(() => setLoading(false))
  }, [agentId])

  if (loading) {
    return (
      <div className="flex justify-center mt-4">
        <IconLoader2 className="size-5 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (intents.length === 0) return null

  return (
    <>
      <div className="flex flex-wrap justify-center gap-2 mt-4 max-w-2xl">
        {intents.map((intent) => (
          <button
            key={intent.id}
            type="button"
            onClick={() => setSelectedIntent(intent)}
            className="flex items-center gap-2 rounded-full border bg-background px-4 py-2 text-sm font-medium text-foreground shadow-sm hover:bg-muted/80 hover:border-blue-500/40 transition-all cursor-pointer"
          >
            {intent.icon ? (
              <Icon icon={intent.icon} className="size-4 shrink-0" />
            ) : (
              <IconBulb className="size-4 shrink-0 text-muted-foreground" />
            )}
            {intent.title}
            <IconArrowRight className="size-3.5 text-muted-foreground" />
          </button>
        ))}
      </div>

      <Dialog open={!!selectedIntent} onOpenChange={(open) => !open && setSelectedIntent(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              {selectedIntent?.icon && (
                <Icon icon={selectedIntent.icon} className="size-5 shrink-0" />
              )}
              {selectedIntent?.title}
            </DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-2 mt-2">
            {selectedIntent?.actions.map((action) => (
              <button
                key={action.id}
                type="button"
                onClick={() => {
                  onSelectPrompt(action.prompt)
                  setSelectedIntent(null)
                }}
                className="flex items-center justify-between gap-3 rounded-lg border bg-background p-3 text-left hover:bg-muted/60 hover:border-blue-500/40 transition-all cursor-pointer group"
              >
                <div>
                  <div className="text-sm font-medium">{action.label}</div>
                  <div className="text-xs text-muted-foreground mt-0.5 line-clamp-2">{action.prompt}</div>
                </div>
                <IconArrowRight className="size-4 shrink-0 text-muted-foreground group-hover:text-blue-500 transition-colors" />
              </button>
            ))}
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
