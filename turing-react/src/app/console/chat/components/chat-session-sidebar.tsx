import { IconCompass, IconMessageCircle, IconTrash, IconX } from "@tabler/icons-react"
import type { ChatSession } from "@/services/chat/chat-session.service"
import { formatRelativeTime } from "../chat.types"

interface ChatSessionSidebarProps {
  open: boolean
  onClose: () => void
  sessions: ChatSession[]
  activeSessionId: string | null
  onRestore: (session: ChatSession) => void
  onDelete: (e: React.MouseEvent, id: string) => void
}

export function ChatSessionSidebar({ open, onClose, sessions, activeSessionId, onRestore, onDelete }: ChatSessionSidebarProps) {
  return (
    <div className={`shrink-0 border-r bg-muted/30 flex flex-col transition-all duration-200 ${open ? "w-72" : "w-0"} overflow-hidden`}>
      <div className="flex items-center justify-between px-4 py-3 border-b">
        <span className="text-sm font-medium">Sessions</span>
        <button type="button" onClick={onClose} className="text-muted-foreground hover:text-foreground transition-colors">
          <IconX className="size-4" />
        </button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {sessions.length === 0 ? (
          <div className="px-4 py-8 text-center text-xs text-muted-foreground">No saved sessions</div>
        ) : (
          <div className="py-1">
            {sessions.map((session) => (
              <button
                key={session.id}
                type="button"
                onClick={() => onRestore(session)}
                title={session.title}
                className={`w-full text-left px-4 py-2.5 text-sm hover:bg-muted/60 transition-colors flex items-start gap-2 group/session ${activeSessionId === session.id ? "bg-muted/80" : ""}`}
              >
                <div className="shrink-0 pt-0.5">
                  {session.tab === "chat" ? <IconMessageCircle className="size-3.5 text-muted-foreground" /> : <IconCompass className="size-3.5 text-emerald-500" />}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="truncate">{session.title}</div>
                  <div className="text-xs text-muted-foreground mt-0.5">
                    {formatRelativeTime(session.updatedAt ?? session.createdAt)} &middot; {session.messages.length} msgs
                  </div>
                  {session.llmTitle && (
                    <div className="text-xs text-muted-foreground/70 mt-0.5 truncate">
                      {session.llmTitle}{session.modelName ? ` (${session.modelName})` : ""}
                    </div>
                  )}
                </div>
                <button
                  type="button"
                  onClick={(e) => onDelete(e, session.id)}
                  title="Delete session"
                  className="shrink-0 opacity-0 group-hover/session:opacity-100 text-muted-foreground hover:text-destructive transition-all p-0.5"
                >
                  <IconTrash className="size-3.5" />
                </button>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
