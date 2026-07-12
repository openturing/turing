import { ROUTES } from "@/app/routes.const";
import { Button } from "@/components/ui/button";
import type { PersonaAudioJobStatus } from "@/models/persona/persona-audio.model.ts";
import { TurPersonaAudioService } from "@/services/persona/persona-audio.service";
import { IconLoader2, IconMicrophone } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

const service = new TurPersonaAudioService();

/**
 * Bento "Derive from audio" header action — Block AG follow-up (T619), made
 * async with a live progress bar in T715 (§XLII.7). Uploads a recording, submits
 * a background job, then streams progress over SSE (queued → transcribing X/N →
 * analysing → done) so a long recording shows real movement instead of a bare
 * spinner. On success it opens the bento new-persona form pre-filled with the
 * (unsaved) draft for human review — the loader reads the draft from router
 * state (see {@link BentoPersonaPage}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function BentoPersonaFromAudioButton() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<PersonaAudioJobStatus | null>(null);
  // Percent is kept monotonic: chunk-completion events arrive from a parallel
  // pool and the SSE delivery order isn't guaranteed, so an out-of-order "3/7"
  // after "4/7" must not rewind the bar.
  const [percent, setPercent] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function onFile(file: File) {
    setBusy(true);
    setStatus(null);
    setPercent(0);
    try {
      const { jobId } = await service.submitJob(file);
      // The SSE stream drives the live progress bar; the authoritative result
      // (fully-serialised draft) is fetched from the servlet poll endpoint once
      // the stream ends — see TurPersonaAudioService.getJob.
      await service.streamJob(jobId, {
        onStatus: (s) => {
          setStatus(s);
          setPercent((prev) => Math.max(prev, phasePercent(s)));
        },
      });
      const result = await service.getJob(jobId);
      if (result.state === "SUCCEEDED" && result.draft) {
        toast.success(t("persona.fromAudio.drafted"));
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
      setStatus(null);
      setPercent(0);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  const label = busy ? phaseLabel(status, t) : t("persona.fromAudio.trigger");
  // A single-chunk transcription (a long clip that re-encodes under the limit)
  // has no sub-progress — show an indeterminate animated bar instead of a bar
  // frozen at 0%. Multi-chunk (max-chunk-seconds set) drives a real percentage.
  const indeterminate = !status || (status.totalChunks ?? 0) <= 1;

  return (
    <div className="flex flex-col items-stretch gap-1.5">
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
        {label}
      </Button>
      {busy && (
        <div
          className="relative h-1 w-full overflow-hidden rounded-full bg-muted"
          role="progressbar"
          aria-valuenow={indeterminate ? undefined : percent}
          aria-valuemin={0}
          aria-valuemax={100}
        >
          <style>{"@keyframes tur-indeterminate{0%{transform:translateX(-110%)}100%{transform:translateX(260%)}}"}</style>
          {indeterminate ? (
            // A sliding segment reads as "working" (indeterminate); a full-width
            // pulse would look like a finished bar.
            <div
              className="absolute inset-y-0 left-0 w-2/5 rounded-full bg-gradient-to-r from-blue-600 to-indigo-600 dark:from-blue-500 dark:to-indigo-500"
              style={{ animation: "tur-indeterminate 1.3s ease-in-out infinite" }}
            />
          ) : (
            <div
              className="h-full rounded-full bg-gradient-to-r from-blue-600 to-indigo-600 transition-[width] duration-300 dark:from-blue-500 dark:to-indigo-500"
              style={{ width: `${percent}%` }}
            />
          )}
        </div>
      )}
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
    </div>
  );
}

/** Phase-aware label for the busy button. */
function phaseLabel(
  status: PersonaAudioJobStatus | null,
  t: (key: string, opts?: Record<string, unknown>) => string,
): string {
  switch (status?.state) {
    case "TRANSCRIBING":
      return status.totalChunks > 1
        ? t("persona.fromAudio.transcribingChunks", {
            done: status.completedChunks,
            total: status.totalChunks,
          })
        : t("persona.fromAudio.transcribing");
    case "ANALYZING":
      return t("persona.fromAudio.analyzing");
    case "QUEUED":
      return t("persona.fromAudio.queued");
    default:
      return t("persona.fromAudio.working");
  }
}

/**
 * Coarse percent: transcription is the bulk (0–90% across chunks), analysing
 * lands near the end. Indeterminate phases nudge forward so the bar never sits
 * dead at 0.
 */
function phasePercent(status: PersonaAudioJobStatus | null): number {
  switch (status?.state) {
    case "TRANSCRIBING":
      return status.totalChunks > 0
        ? Math.round((status.completedChunks / status.totalChunks) * 90)
        : 10;
    case "ANALYZING":
      return 95;
    case "SUCCEEDED":
      return 100;
    case "QUEUED":
    default:
      return 5;
  }
}
