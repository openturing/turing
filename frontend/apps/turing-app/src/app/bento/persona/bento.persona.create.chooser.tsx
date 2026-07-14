import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import type { PersonaAudioJobStatus } from "@/models/persona/persona-audio.model.ts";
import type { TurPersona } from "@/models/persona/persona.model.ts";
import { TurPersonaAudioService } from "@/services/persona/persona-audio.service";
import { IconForms, IconLoader2, IconMicrophone, IconUserCircle } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

const service = new TurPersonaAudioService();

/**
 * New-persona creation chooser — the first step when creating a persona. The
 * user picks HOW to start: fill the form manually, or upload an audio recording
 * and let AI draft the persona (transcribe → analyse) for review. This unifies
 * the former standalone "Derive from audio" header action into the create flow
 * (T619/T715 logic reused here). It renders as the `isNew` landing inside
 * {@link BentoPersonaPage}'s Outlet, so the audio draft is applied via the
 * shared loader context (`setPersona`) before opening the editor — no router
 * state / re-seed dance needed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function BentoPersonaCreateChooser({ setPersona }: Readonly<{ setPersona: (persona: TurPersona) => void }>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<PersonaAudioJobStatus | null>(null);
  // Percent is kept monotonic: chunk-completion events arrive from a parallel
  // pool and SSE delivery order isn't guaranteed, so a late "3/7" after "4/7"
  // must not rewind the bar.
  const [percent, setPercent] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function onAudioFile(file: File) {
    setBusy(true);
    setStatus(null);
    setPercent(0);
    try {
      const { jobId } = await service.submitJob(file);
      await service.streamJob(jobId, {
        onStatus: (s) => {
          setStatus(s);
          setPercent((prev) => Math.max(prev, phasePercent(s)));
        },
      });
      const result = await service.getJob(jobId);
      if (result.state === "SUCCEEDED" && result.draft) {
        toast.success(t("persona.fromAudio.drafted"));
        // Seed the shared loader's (unsaved) persona with the AI draft, then open
        // the editor pre-filled for human review. Drop any id so it stays a new
        // (uncreated) persona until the user saves.
        const seeded = { ...result.draft };
        delete (seeded as Partial<TurPersona>).id;
        setPersona(seeded as TurPersona);
        navigate("general");
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

  // A single-chunk transcription (a long clip that re-encodes under the limit)
  // has no sub-progress — show an indeterminate animated bar instead of one
  // frozen at 0%. Multi-chunk (max-chunk-seconds set) drives a real percentage.
  const indeterminate = !status || (status.totalChunks ?? 0) <= 1;

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_PERSONA_INSTANCE}
        backLabel={t("persona.title")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-rose-500 to-pink-600 text-white shadow-md">
            <IconUserCircle size={24} />
          </span>
        }
        title={t("persona.newInstance")}
        subtitle={t("persona.create.chooseMethod", {
          defaultValue: "How do you want to create this persona?",
        })}
      />

      <div className="grid gap-4 sm:grid-cols-2">
        {/* Manual — go straight to the editor form. */}
        <button
          type="button"
          disabled={busy}
          onClick={() => navigate("general")}
          className="bento-tile bento-tile-clickable bento-glass flex flex-col items-start gap-3 rounded-3xl p-6 text-left disabled:opacity-50"
        >
          <span className="grid h-11 w-11 place-items-center rounded-2xl bg-linear-to-br from-blue-500 to-indigo-600 text-white shadow-md">
            <IconForms size={22} />
          </span>
          <span className="text-base font-semibold tracking-tight md:text-lg">
            {t("persona.create.manualTitle", { defaultValue: "Enter details manually" })}
          </span>
          <p className="text-sm text-muted-foreground">
            {t("persona.create.manualDesc", {
              defaultValue: "Fill in the persona's identity, style and guidelines yourself.",
            })}
          </p>
        </button>

        {/* From audio — upload a recording; AI drafts the persona. */}
        <button
          type="button"
          disabled={busy}
          onClick={() => fileInputRef.current?.click()}
          className="bento-tile bento-tile-clickable bento-glass flex flex-col items-start gap-3 rounded-3xl p-6 text-left disabled:cursor-progress disabled:opacity-90"
        >
          <span className="grid h-11 w-11 place-items-center rounded-2xl bg-linear-to-br from-fuchsia-500 to-violet-600 text-white shadow-md">
            {busy ? <IconLoader2 size={22} className="animate-spin" /> : <IconMicrophone size={22} />}
          </span>
          <span className="text-base font-semibold tracking-tight md:text-lg">
            {t("persona.create.audioTitle", { defaultValue: "From an audio recording" })}
          </span>
          <p className="text-sm text-muted-foreground">
            {busy
              ? phaseLabel(status, t)
              : t("persona.create.audioDesc", {
                  defaultValue:
                    "Upload a recording and let AI draft the persona for you to review.",
                })}
          </p>
          {busy && (
            <div
              className="relative mt-1 h-1 w-full overflow-hidden rounded-full bg-muted"
              role="progressbar"
              aria-valuenow={indeterminate ? undefined : percent}
              aria-valuemin={0}
              aria-valuemax={100}
            >
              <style>{"@keyframes tur-indeterminate{0%{transform:translateX(-110%)}100%{transform:translateX(260%)}}"}</style>
              {indeterminate ? (
                <div
                  className="absolute inset-y-0 left-0 w-2/5 rounded-full bg-gradient-to-r from-fuchsia-500 to-violet-600"
                  style={{ animation: "tur-indeterminate 1.3s ease-in-out infinite" }}
                />
              ) : (
                <div
                  className="h-full rounded-full bg-gradient-to-r from-fuchsia-500 to-violet-600 transition-[width] duration-300"
                  style={{ width: `${percent}%` }}
                />
              )}
            </div>
          )}
        </button>
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept="audio/*,.mp3,.wav,.m4a,.webm,.ogg,.flac"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) onAudioFile(file);
        }}
      />
    </>
  );
}

/** Phase-aware label for the busy audio card. */
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
