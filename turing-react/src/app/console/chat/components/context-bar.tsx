import { IconBolt, IconLoader2 } from "@tabler/icons-react"
import { contextBarColor, formatTokenCount } from "../chat.types"

interface ContextBarProps {
  tokens: number
  percentage: number
  contextWindow: number
  compacting: boolean
  canCompact: boolean
  onCompact: () => void
}

export function ContextBar({ tokens, percentage, contextWindow, compacting, canCompact, onCompact }: ContextBarProps) {
  return (
    <div className="flex items-center gap-2 flex-1 min-w-0 justify-end">
      <div className="flex items-center gap-2 max-w-xs w-full">
        <div className="flex-1 h-1.5 rounded-full bg-muted overflow-hidden">
          <div className={`h-full rounded-full transition-all duration-300 ${contextBarColor(percentage)}`} style={{ width: `${percentage}%` }} />
        </div>
        <span className="text-xs text-muted-foreground whitespace-nowrap">{formatTokenCount(tokens)}/{formatTokenCount(contextWindow)}</span>
      </div>
      <button
        type="button"
        onClick={onCompact}
        disabled={!canCompact || compacting}
        title={`${100 - percentage}% of context remaining. Click to compact.`}
        className="flex items-center gap-1 rounded-md border px-2 py-1 text-xs text-muted-foreground hover:text-foreground hover:bg-muted transition-colors disabled:opacity-50 disabled:pointer-events-none shrink-0"
      >
        {compacting ? <IconLoader2 className="size-3 animate-spin" /> : <IconBolt className="size-3" />}
        Compact
      </button>
    </div>
  )
}
