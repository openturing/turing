import { useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { IconCopy, IconDownload, IconInfoCircle } from "@tabler/icons-react"

import { useChatSessionSlots, useChatSlotAudit } from "@/api/queries/chat-session.queries"
import type { TurChatSlotAuditEntry } from "@/services/chat/chat-slot-audit.service"
import { TurChatSessionExportService } from "@/services/chat/chat-session-export.service"
import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"

interface ChatSessionInfoSheetProps {
  conversationId: string | null
}

/**
 * Slide-in panel exposing identifiers and metadata about the *current* chat
 * session: conversation id (same value the backend keys on for
 * `/api/chat/sessions/{conversationId}/slots` and `/messages`) plus the
 * live slot values captured by every flow that ran (or is still running)
 * in this conversation. Slots are merged at the JSON root by the backend,
 * so the UI just renders a flat map. While the sheet is open the slots
 * endpoint is polled so newly-filled slots show up without a manual refresh.
 *
 * The trigger is a subtle info icon in the chat header — visible even when
 * no session is active so admins can confirm the empty state.
 *
 * @since 2026.2.7
 */
const exportService = new TurChatSessionExportService()

export function ChatSessionInfoSheet({ conversationId }: ChatSessionInfoSheetProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [exporting, setExporting] = useState(false)

  // Poll only while the sheet is visible AND a session exists; otherwise the
  // hook is disabled and no network traffic is generated.
  const { data, isError } = useChatSessionSlots(conversationId, {
    enabled: open,
    refetchInterval: 3000,
  })
  const { data: auditData, isError: auditError } = useChatSlotAudit(conversationId, {
    enabled: open,
    refetchInterval: 5000,
  })

  const copyId = async () => {
    if (!conversationId) return
    try {
      await navigator.clipboard.writeText(conversationId)
      toast.success(t("chat.sessionInfo.copied"))
    } catch {
      // Clipboard may be unavailable on insecure contexts; fail silently.
    }
  }

  const exportConversation = async () => {
    if (!conversationId || exporting) return
    setExporting(true)
    try {
      await exportService.download(conversationId)
      toast.success(t("chat.sessionInfo.exportSuccess"))
    } catch {
      toast.error(t("chat.sessionInfo.exportError"))
    } finally {
      setExporting(false)
    }
  }

  const slotEntries = Object.entries(data?.slots ?? {})

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        title={t("chat.sessionInfo.button")}
        aria-label={t("chat.sessionInfo.button")}
        className="text-muted-foreground hover:text-foreground transition-colors p-1.5 rounded-md hover:bg-muted shrink-0"
      >
        <IconInfoCircle className="size-5" />
      </button>
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>{t("chat.sessionInfo.title")}</SheetTitle>
            <SheetDescription>{t("chat.sessionInfo.description")}</SheetDescription>
          </SheetHeader>
          <div className="px-4 py-2 space-y-6 overflow-y-auto">
            <section className="space-y-1.5">
              <div className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                {t("chat.sessionInfo.conversationId")}
              </div>
              {conversationId ? (
                <div className="flex items-center gap-2">
                  <code className="flex-1 min-w-0 truncate rounded bg-muted px-2 py-1 text-xs font-mono">
                    {conversationId}
                  </code>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={copyId}
                    title={t("chat.copyToClipboard")}
                  >
                    <IconCopy className="size-4" />
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={exportConversation}
                    disabled={exporting}
                    title={t("chat.sessionInfo.export")}
                    aria-label={t("chat.sessionInfo.export")}
                  >
                    <IconDownload className="size-4" />
                  </Button>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">
                  {t("chat.sessionInfo.noActiveSession")}
                </p>
              )}
            </section>

            {conversationId && (
              <section className="space-y-2">
                <div className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                  {t("chat.sessionInfo.slots")}
                </div>
                {isError ? (
                  <p className="text-sm text-destructive">
                    {t("chat.sessionInfo.slotsError")}
                  </p>
                ) : slotEntries.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    {t("chat.sessionInfo.slotsEmpty")}
                  </p>
                ) : (
                  <div className="rounded-md border bg-muted/20 p-3">
                    <dl className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1.5 text-xs">
                      {slotEntries.map(([name, value]) => (
                        <div key={name} className="contents">
                          <dt className="font-mono text-muted-foreground truncate" title={name}>
                            {name}
                          </dt>
                          <dd className="font-mono wrap-break-word whitespace-pre-wrap">{value}</dd>
                        </div>
                      ))}
                    </dl>
                  </div>
                )}
              </section>
            )}

            {conversationId && (
              <section className="space-y-2">
                <div className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                  {t("chat.sessionInfo.auditTitle")}
                </div>
                {auditError ? (
                  <p className="text-sm text-destructive">
                    {t("chat.sessionInfo.auditError")}
                  </p>
                ) : !auditData || auditData.entries.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    {t("chat.sessionInfo.auditEmpty")}
                  </p>
                ) : (
                  <ol className="space-y-1.5">
                    {auditData.entries.map(entry => (
                      <SlotAuditRow key={entry.id} entry={entry} />
                    ))}
                  </ol>
                )}
              </section>
            )}
          </div>
        </SheetContent>
      </Sheet>
    </>
  )
}

/**
 * One row of the slot audit timeline. Renders the slot name + source badge
 * on top, then the (old → new) delta inline so the operator reads the
 * change at a glance. {@code originDetail} (node id / tool title / site
 * name / agent id) appears as a subtle subtitle.
 */
function SlotAuditRow({ entry }: { readonly entry: TurChatSlotAuditEntry }) {
  const { t } = useTranslation()
  const sourceLabel = t(`chat.sessionInfo.auditSource.${entry.source}`, {
    defaultValue: entry.source,
  })
  const renderValue = (value: string | null, cleared = false) => {
    if (value === null || value === undefined) return <em className="text-muted-foreground/70">{t("chat.sessionInfo.auditEmptyValue")}</em>
    if (value === "" && cleared) return <em className="text-muted-foreground/70">{t("chat.sessionInfo.auditCleared")}</em>
    if (value === "") return <em className="text-muted-foreground/70">{t("chat.sessionInfo.auditEmptyValue")}</em>
    return <span className="break-all">{value}</span>
  }
  const sourceColor: Record<string, string> = {
    NODE: "bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300",
    TOOL: "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
    ENDPOINT: "bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300",
    EXTRACT: "bg-violet-100 text-violet-700 dark:bg-violet-950 dark:text-violet-300",
  }
  const badgeClass = sourceColor[entry.source] ?? "bg-muted text-muted-foreground"
  return (
    <li className="rounded-md border bg-muted/10 px-3 py-2 text-xs space-y-1">
      <div className="flex items-center gap-2 flex-wrap">
        <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium uppercase ${badgeClass}`}>
          {sourceLabel}
        </span>
        <span className="font-mono font-medium truncate" title={entry.slotName}>
          {entry.slotName}
        </span>
        <span className="text-muted-foreground/60 ml-auto shrink-0">
          {new Date(entry.ts).toLocaleString()}
        </span>
      </div>
      <div className="font-mono leading-relaxed">
        {renderValue(entry.oldValue)}
        <span className="mx-1.5 text-muted-foreground/60">→</span>
        {renderValue(entry.newValue, true)}
      </div>
      {entry.originDetail && (
        <div className="text-muted-foreground/70 truncate" title={entry.originDetail}>
          {entry.originDetail}
        </div>
      )}
    </li>
  )
}
