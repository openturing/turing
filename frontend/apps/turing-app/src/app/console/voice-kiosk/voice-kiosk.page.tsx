import {
  IconAlertTriangle,
  IconMicrophone,
  IconMicrophoneOff,
  IconPlayerStopFilled,
} from "@tabler/icons-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";

import { useTuringRealtimeVoice } from "@viglet/turing-react-sdk";

import { cn } from "@/lib/utils";

/**
 * T149 / §X.6.c — voice-first kiosk mode. A full-screen, chrome-free voice UI
 * for trade-show booths, lobby kiosks, and accessibility deployments: one big
 * mic orb, live transcripts, nothing else. Drives the agent named in
 * {@code ?agent=<id>} through the full-duplex {@link useTuringRealtimeVoice}
 * hook (T148).
 *
 * Standalone route (rendered outside the console shell — no sidebar).
 *
 * @since 2026.3.4
 */
export default function VoiceKioskPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const agentId = searchParams.get("agent") ?? "";
  const llmInstanceId = searchParams.get("llm") ?? undefined;
  const locale = searchParams.get("locale") ?? undefined;

  const {
    supported,
    status,
    isConnecting,
    isConnected,
    isListening,
    isAssistantSpeaking,
    isMuted,
    userTranscript,
    assistantTranscript,
    error,
    start,
    stop,
    mute,
    unmute,
  } = useTuringRealtimeVoice({ agentId, llmInstanceId, locale });

  const statusLabel = useMemo(() => {
    if (isConnecting) return t("voiceKiosk.connecting");
    if (isAssistantSpeaking) return t("voiceKiosk.speaking");
    if (isListening) return t("voiceKiosk.listening");
    if (status === "error") return t("voiceKiosk.error");
    return t("voiceKiosk.tapToStart");
  }, [isConnecting, isAssistantSpeaking, isListening, status, t]);

  const blocker =
    !supported
      ? t("voiceKiosk.unsupported")
      : !agentId
        ? t("voiceKiosk.missingAgent")
        : null;

  const onOrbClick = () => {
    if (blocker) return;
    if (isConnected || isConnecting) return;
    void start();
  };

  return (
    <div className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-10 overflow-hidden bg-gradient-to-br from-slate-950 via-slate-900 to-indigo-950 text-white">
      <h1 className="text-2xl font-semibold tracking-tight text-white/90">
        {t("voiceKiosk.title")}
      </h1>

      {blocker ? (
        <div className="flex max-w-md flex-col items-center gap-3 px-6 text-center">
          <IconAlertTriangle className="size-10 text-amber-400" />
          <p className="text-lg text-white/80">{blocker}</p>
        </div>
      ) : (
        <>
          <button
            type="button"
            onClick={onOrbClick}
            aria-label={statusLabel}
            disabled={isConnecting}
            className={cn(
              "relative flex size-56 items-center justify-center rounded-full",
              "bg-gradient-to-br from-blue-600 to-indigo-600 shadow-2xl shadow-indigo-900/50",
              "transition-transform duration-300 focus:outline-none focus-visible:ring-4 focus-visible:ring-indigo-400/60",
              isConnected ? "cursor-default" : "cursor-pointer hover:scale-105",
              isAssistantSpeaking && "animate-pulse",
            )}
          >
            {/* Listening halo */}
            {isListening && (
              <span className="absolute inset-0 animate-ping rounded-full bg-blue-500/30" />
            )}
            <IconMicrophone className="size-20 text-white" stroke={1.5} />
          </button>

          <p className="text-xl font-medium text-white/80" aria-live="polite">
            {statusLabel}
          </p>

          {/* Transcripts */}
          <div className="flex min-h-24 w-full max-w-2xl flex-col gap-3 px-6">
            {userTranscript && (
              <div className="self-end rounded-2xl bg-white/10 px-4 py-2 text-right text-white/90">
                <span className="mb-0.5 block text-xs uppercase tracking-wide text-white/50">
                  {t("voiceKiosk.you")}
                </span>
                {userTranscript}
              </div>
            )}
            {assistantTranscript && (
              <div className="self-start rounded-2xl bg-indigo-500/20 px-4 py-2 text-left text-white/90">
                <span className="mb-0.5 block text-xs uppercase tracking-wide text-indigo-200/70">
                  {t("voiceKiosk.assistant")}
                </span>
                {assistantTranscript}
              </div>
            )}
          </div>

          {error && (
            <p className="text-sm text-rose-300">{error.message}</p>
          )}

          {/* Controls — only while a session is live */}
          {isConnected && (
            <div className="flex items-center gap-4">
              <button
                type="button"
                onClick={isMuted ? unmute : mute}
                className="flex items-center gap-2 rounded-full bg-white/10 px-5 py-3 text-sm font-medium text-white/90 transition-colors hover:bg-white/20"
              >
                {isMuted ? (
                  <IconMicrophoneOff className="size-5" />
                ) : (
                  <IconMicrophone className="size-5" />
                )}
                {isMuted ? t("voiceKiosk.unmute") : t("voiceKiosk.mute")}
              </button>
              <button
                type="button"
                onClick={stop}
                className="flex items-center gap-2 rounded-full bg-gradient-to-br from-red-600 to-rose-600 px-5 py-3 text-sm font-medium text-white shadow-lg transition-transform hover:scale-105"
              >
                <IconPlayerStopFilled className="size-5" />
                {t("voiceKiosk.end")}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
