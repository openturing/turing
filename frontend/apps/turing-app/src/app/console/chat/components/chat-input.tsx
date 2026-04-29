import { useCallback, useEffect, useRef, useState } from "react"
import { IconArrowUp, IconFile, IconLoader2, IconPaperclip, IconX } from "@tabler/icons-react"
import { Textarea } from "@/components/ui/textarea"
import { GradientButton } from "@/components/ui/gradient-button"
import { formatFileSize } from "../chat.types"
import { useTranslation } from "react-i18next"

interface ChatInputProps {
  value: string
  onChange: (value: string) => void
  onSend: () => void
  loading: boolean
  disabled: boolean
  placeholder: string
  accentColor?: "blue" | "emerald" | "violet"
  attachments?: {
    files: File[]
    onAdd: (files: FileList | File[]) => void
    onRemove: (index: number) => void
  }
}

export function ChatInput({ value, onChange, onSend, loading, disabled, placeholder, accentColor = "blue", attachments }: ChatInputProps) {
  const { t } = useTranslation()
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [isDragOver, setIsDragOver] = useState(false)

  const adjustHeight = useCallback(() => {
    const textarea = textareaRef.current
    if (textarea) {
      textarea.style.height = "auto"
      textarea.style.height = `${Math.min(textarea.scrollHeight, 200)}px`
    }
  }, [])

  useEffect(() => { adjustHeight() }, [value, adjustHeight])

  // Restore focus to the textarea when streaming finishes. The textarea is
  // disabled while `loading` is true (browser drops focus), so without this
  // the user has to click back into the input before typing the next turn.
  const wasLoadingRef = useRef(loading)
  useEffect(() => {
    if (wasLoadingRef.current && !loading && !disabled) {
      textareaRef.current?.focus()
    }
    wasLoadingRef.current = loading
  }, [loading, disabled])

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); onSend() }
  }

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0 && attachments) {
      attachments.onAdd(e.target.files)
      e.target.value = ""
    }
  }

  const handleDragOver = (e: React.DragEvent) => { e.preventDefault(); setIsDragOver(true) }
  const handleDragLeave = (e: React.DragEvent) => { e.preventDefault(); setIsDragOver(false) }
  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault(); setIsDragOver(false)
    if (e.dataTransfer.files.length > 0 && attachments) attachments.onAdd(e.dataTransfer.files)
  }

  const ringColor = accentColor === "emerald"
    ? "focus-within:ring-emerald-500/50 focus-within:border-emerald-500/50"
    : accentColor === "violet"
      ? "focus-within:ring-violet-500/50 focus-within:border-violet-500/50"
      : "focus-within:ring-blue-500/50 focus-within:border-blue-500/50"
  const hasContent = value.trim() || (attachments && attachments.files.length > 0)

  return (
    <div
      onDragOver={attachments ? handleDragOver : undefined}
      onDragLeave={attachments ? handleDragLeave : undefined}
      onDrop={attachments ? handleDrop : undefined}
      className="relative"
    >
      {isDragOver && attachments && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm border-2 border-dashed border-blue-500 rounded-lg">
          <div className="flex flex-col items-center gap-2 text-blue-500">
            <IconFile className="size-10" />
            <span className="text-lg font-medium">{t("chat.dropFilesHere")}</span>
          </div>
        </div>
      )}

      {attachments && attachments.files.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-2">
          {attachments.files.map((file, idx) => (
            <div key={idx} className="flex items-center gap-1.5 rounded-lg border bg-muted/50 px-2.5 py-1.5 text-xs">
              <IconFile className="size-3.5 shrink-0 text-muted-foreground" />
              <span className="truncate max-w-[150px]">{file.name}</span>
              <span className="text-muted-foreground/60">{formatFileSize(file.size)}</span>
              <button type="button" onClick={() => attachments.onRemove(idx)} title={`Remove ${file.name}`} className="ml-0.5 rounded-full p-0.5 hover:bg-muted-foreground/20 transition-colors">
                <IconX className="size-3" />
              </button>
            </div>
          ))}
        </div>
      )}

      <div className={`relative flex items-center rounded-xl border bg-muted/50 focus-within:ring-2 ${ringColor} transition-all`}>
        {attachments && (
          <>
            <input ref={fileInputRef} type="file" multiple className="hidden" title={t("chat.attachFiles")} onChange={handleFileInputChange} />
            <button type="button" onClick={() => fileInputRef.current?.click()} disabled={disabled || loading} className="p-3 text-muted-foreground hover:text-foreground transition-colors disabled:opacity-50 disabled:pointer-events-none" title={t("chat.attachFiles")}>
              <IconPaperclip className="size-5" />
            </button>
          </>
        )}
        <Textarea
          ref={textareaRef}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          disabled={disabled || loading}
          className={`flex-1 resize-none border-0 bg-transparent ${attachments ? "px-2" : "px-4"} py-3 text-sm focus-visible:ring-0 focus-visible:ring-offset-0 min-h-11! max-h-50 field-sizing-fixed`}
          rows={1}
        />
        <div className="p-2">
          <GradientButton size="icon-sm" disabled={!hasContent || loading || disabled} onClick={onSend} className="rounded-lg">
            {loading ? <IconLoader2 className="size-4 animate-spin" /> : <IconArrowUp className="size-4" />}
          </GradientButton>
        </div>
      </div>
    </div>
  )
}
