"use client"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { ROUTES } from "@/app/routes.const"
import { TurPersonaAudioService } from "@/services/persona/persona-audio.service"
import { IconLoader2, IconMicrophone } from "@tabler/icons-react"
import { useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"

const service = new TurPersonaAudioService()

/**
 * Block AA / §XXVI.7 — floating "Derive from audio" entry. Uploads a recording,
 * has the backend transcribe + draft a persona, then opens the persona form
 * pre-filled with the (unsaved) draft for human review. Stacked just above the
 * "Create with AI" FAB on the persona list page.
 */
export function PersonaFromAudioTrigger({ className }: { className?: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  async function onFile(file: File) {
    setBusy(true)
    try {
      const result = await service.deriveFromAudio(file)
      if (result.success && result.draft) {
        toast.success(t("persona.fromAudio.drafted"))
        // Hand the unsaved draft to the new-persona form via router state.
        navigate(`${ROUTES.PERSONA_INSTANCE}/new`, {
          state: { draft: result.draft, transcript: result.transcript },
        })
      } else {
        toast.error(result.error ?? t("persona.fromAudio.failed"))
      }
    } catch {
      toast.error(t("persona.fromAudio.failed"))
    } finally {
      setBusy(false)
      if (fileInputRef.current) fileInputRef.current.value = ""
    }
  }

  return (
    <>
      <Button
        type="button"
        size="lg"
        disabled={busy}
        onClick={() => fileInputRef.current?.click()}
        className={cn(
          "fixed bottom-24 right-6 z-30 gap-2 rounded-full shadow-lg",
          "bg-gradient-to-r from-sky-600 to-cyan-600 text-white",
          "hover:from-sky-500 hover:to-cyan-500",
          className
        )}
      >
        {busy ? (
          <IconLoader2 className="size-5 animate-spin" />
        ) : (
          <IconMicrophone className="size-5" />
        )}
        {busy ? t("persona.fromAudio.working") : t("persona.fromAudio.trigger")}
      </Button>
      <input
        ref={fileInputRef}
        type="file"
        accept="audio/*,.mp3,.wav,.m4a,.webm,.ogg,.flac"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) onFile(file)
        }}
      />
    </>
  )
}
