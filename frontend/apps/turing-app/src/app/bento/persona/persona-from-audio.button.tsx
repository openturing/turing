import { ROUTES } from "@/app/routes.const";
import { Button } from "@/components/ui/button";
import { TurPersonaAudioService } from "@/services/persona/persona-audio.service";
import { IconLoader2, IconMicrophone } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

const service = new TurPersonaAudioService();

/**
 * Bento "Derive from audio" header action — Block AG follow-up (T619). Re-adds
 * T470's persona-from-audio authoring, dropped in the console retirement (T570),
 * as a persona-list header button (matching the "Persona dialogue" affordance)
 * instead of the old floating FAB. Uploads a recording, has the backend
 * transcribe + draft a persona, then opens the bento new-persona form pre-filled
 * with the (unsaved) draft for human review — the loader already reads the draft
 * from router state (see {@link BentoPersonaPage}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function BentoPersonaFromAudioButton() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function onFile(file: File) {
    setBusy(true);
    try {
      const result = await service.deriveFromAudio(file);
      if (result.success && result.draft) {
        toast.success(t("persona.fromAudio.drafted"));
        // Hand the unsaved draft to the new-persona form via router state.
        navigate(`${ROUTES.BENTO_PERSONA_INSTANCE}/new`, {
          state: { draft: result.draft, transcript: result.transcript },
        });
      } else {
        toast.error(result.error ?? t("persona.fromAudio.failed"));
      }
    } catch {
      toast.error(t("persona.fromAudio.failed"));
    } finally {
      setBusy(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  return (
    <>
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={busy}
        onClick={() => fileInputRef.current?.click()}
        className="gap-2"
      >
        {busy ? (
          <IconLoader2 size={16} className="animate-spin" />
        ) : (
          <IconMicrophone size={16} />
        )}
        {busy ? t("persona.fromAudio.working") : t("persona.fromAudio.trigger")}
      </Button>
      <input
        ref={fileInputRef}
        type="file"
        accept="audio/*,.mp3,.wav,.m4a,.webm,.ogg,.flac"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) onFile(file);
        }}
      />
    </>
  );
}
