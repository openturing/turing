import { IconCompass, IconMessageCircle, IconRobot, IconTrash, IconX } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import type { ChatSession } from "@/services/chat/chat-session.service"
import { useDateLocale } from "@/hooks/use-date-locale"
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
  const { t } = useTranslation()
  const dateLocale = useDateLocale()
  return (
    <div className={`shrink-0 bg-muted/30 flex flex-col transition-all duration-200 overflow-hidden ${open ? "w-72 border-r" : "w-0"}`}>
      <div className="flex items-center justify-between px-4 py-3 border-b">
        <span className="text-sm font-medium">{t("chat.sessions")}</span>
        <button type="button" onClick={onClose} className="text-muted-foreground hover:text-foreground transition-colors">
          <IconX className="size-4" />
        </button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {sessions.length === 0 ? (
          <div className="px-4 py-8 text-center text-xs text-muted-foreground">{t("chat.noSavedSessions")}</div>
        ) : (
          <div className="py-1">
            {sessions.map((session) => (
              <div
                key={session.id}
                role="button"
                tabIndex={0}
                onClick={() => onRestore(session)}
                onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") onRestore(session) }}
                title={session.title}
                className={`w-full text-left px-4 py-2.5 text-sm hover:bg-muted/60 transition-colors flex items-start gap-2 group/session cursor-pointer ${activeSessionId === session.id ? "bg-muted/80" : ""}`}
              >
                <div className="shrink-0 pt-0.5">
                  {session.tab === "chat"
                    ? <IconMessageCircle className="size-3.5 text-muted-foreground" />
                    : session.tab === "semantic"
                      ? <IconCompass className="size-3.5 text-emerald-500" />
                      : <IconRobot className="size-3.5 text-violet-500" />}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="truncate">{session.title}</div>
                  <div className="text-xs text-muted-foreground mt-0.5">
                    {formatRelativeTime(session.updatedAt ?? session.createdAt, dateLocale)} &middot; {session.messages.length} {t("chat.msgs")}
                  </div>
                  {session.llmTitle && (
                    <div className="text-xs text-muted-foreground/70 mt-0.5 truncate">
                      {session.llmTitle}{session.modelName ? ` (${session.modelName})` : ""}
                    </div>
                  )}
                </div>
                <button
                  type="button"
                  onClick={(e) => { e.stopPropagation(); onDelete(e, session.id) }}
                  title={t("chat.deleteSession")}
                  className="shrink-0 opacity-0 group-hover/session:opacity-100 text-muted-foreground hover:text-destructive transition-all p-0.5"
                >
                  <IconTrash className="size-3.5" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
