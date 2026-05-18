import type { Meta, StoryObj } from "@storybook/react-vite";
import { useEffect, useRef, useState } from "react";

/**
 * # useTuringVoice
 *
 * Minimal Web Speech API wrapper for voice-mode chat. Two surfaces:
 *
 * - **STT (Speech-to-Text)** via `SpeechRecognition`: caller invokes
 *   `startListening()` → the hook drives the browser recognizer and
 *   exposes the running `transcript` + `isListening` flag. Caller decides
 *   what to do on every transcript update — typically: pump into the
 *   chat composer's input and auto-send when `isFinal` flips true.
 * - **TTS (Text-to-Speech)** via `SpeechSynthesis`: caller invokes
 *   `speak(text)` → the hook queues an utterance on the browser TTS
 *   engine. `isSpeaking` reflects whether audio is currently playing.
 *
 * Feature-detected: when the browser doesn't support either surface,
 * the matching `*Supported` flag stays false and the caller can hide
 * the corresponding UI button.
 *
 * Locale defaults to **`pt-BR`** — the Executive Education personas converse in
 * Brazilian Portuguese. Override via `lang` option for other markets.
 *
 * ## Key Features
 * - `startListening()` / `stopListening()`: imperative STT control
 * - `transcript` + `isFinal`: streaming + commit signal for the recognizer
 * - `speak(text)` / `stopSpeaking()`: imperative TTS with auto-cancel
 *   of any in-flight utterance (so a new assistant reply interrupts
 *   the previous one cleanly)
 * - `sttSupported` / `ttsSupported`: feature flags for graceful
 *   degradation
 * - `isListening` / `isSpeaking`: UI binding for mic + speaker icons
 *
 * ## Usage
 * ```tsx
 * const voice = useTuringVoice({ lang: "pt-BR", interimResults: true });
 *
 * // Mic button — auto-sends when the user pauses
 * useEffect(() => {
 *   if (voice.transcript && voice.isFinal && !voice.isListening) {
 *     sendChat(voice.transcript);
 *   }
 * }, [voice.transcript, voice.isFinal, voice.isListening]);
 *
 * // Speak the assistant's reply when it arrives
 * useEffect(() => {
 *   if (lastAssistantMessage) voice.speak(lastAssistantMessage);
 * }, [lastAssistantMessage]);
 * ```
 *
 * ## When to use
 *
 * - **Mobile-first chat**: voice input cuts typing on small screens
 *   by ~70%; useful for the Executive Education flow where 60%+ of traffic is
 *   mobile
 * - **Accessibility**: visual impairment, motor limitations — TTS
 *   replay of assistant messages
 * - **Hands-free demos**: trade-show booth, driving scenario, kitchen
 *
 * ## About this story
 *
 * The story simulates STT (no mic permission needed in Storybook) by
 * fake-typing a pre-recorded sentence character-by-character. TTS uses
 * the real `speechSynthesis` API — click "Falar" to hear the demo
 * speak the transcript out loud (browser-dependent voice).
 */

type FauxRecognitionState = "idle" | "listening" | "final";

const SAMPLE_TRANSCRIPTS_PT: ReadonlyArray<string> = [
  "Oi, quero treinar uma equipe de marketing em IA generativa",
  "Sou diretor financeiro de uma fintech, quero virar CFO em três anos",
  "Preciso de um curso curto em liderança para meu time de produto",
];

const SAMPLE_TRANSCRIPTS_EN: ReadonlyArray<string> = [
  "Hi, I'd like to train a marketing team in generative AI",
  "I'm the CFO of a fintech and want to become group CFO in three years",
  "I need a short leadership course for my product team",
];

function VoiceDemo({
  lang,
  interimResults,
  forceUnsupported,
}: {
  lang: string;
  interimResults: boolean;
  forceUnsupported: boolean;
}) {
  const [state, setState] = useState<FauxRecognitionState>("idle");
  const [transcript, setTranscript] = useState("");
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [sampleIdx, setSampleIdx] = useState(0);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Faux feature-detection — in real life this checks
  // `webkitSpeechRecognition`. Storybook always has the browser, but
  // the `forceUnsupported` toggle lets you preview the disabled state.
  const sttSupported = !forceUnsupported;
  const ttsSupported = !forceUnsupported && typeof globalThis !== "undefined" &&
                       "speechSynthesis" in globalThis;

  const samples = lang.startsWith("pt") ? SAMPLE_TRANSCRIPTS_PT : SAMPLE_TRANSCRIPTS_EN;
  const targetText = samples[sampleIdx % samples.length];

  function startListening() {
    if (!sttSupported || state === "listening") return;
    setTranscript("");
    setState("listening");
    let pos = 0;
    intervalRef.current = setInterval(() => {
      pos++;
      const next = targetText.slice(0, pos);
      // When interimResults is false, only flip the visible transcript on
      // the final word boundary — mirrors the snappier "no flicker" mode.
      if (interimResults) {
        setTranscript(next);
      } else if (pos === targetText.length) {
        setTranscript(targetText);
      }
      if (pos >= targetText.length) {
        if (intervalRef.current) clearInterval(intervalRef.current);
        intervalRef.current = null;
        setState("final");
        setSampleIdx((i) => i + 1);
      }
    }, 60);
  }

  function stopListening() {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    setState(transcript.length > 0 ? "final" : "idle");
  }

  function speak() {
    if (!ttsSupported || !transcript.trim()) return;
    const synth = globalThis.speechSynthesis;
    synth.cancel();
    const utter = new SpeechSynthesisUtterance(transcript);
    utter.lang = lang;
    utter.onstart = () => setIsSpeaking(true);
    utter.onend = () => setIsSpeaking(false);
    utter.onerror = () => setIsSpeaking(false);
    synth.speak(utter);
  }

  function stopSpeaking() {
    if (!ttsSupported) return;
    globalThis.speechSynthesis.cancel();
    setIsSpeaking(false);
  }

  // Cleanup on unmount: stop both engines.
  useEffect(() => {
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
      if (typeof globalThis !== "undefined" && "speechSynthesis" in globalThis) {
        globalThis.speechSynthesis.cancel();
      }
    };
  }, []);

  const isListening = state === "listening";
  const isFinal = state === "final";

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", maxWidth: "560px" }}>
      {/* Feature-flag row */}
      <div style={{
        display: "flex", gap: "10px", marginBottom: "12px",
        padding: "8px 14px",
        background: "#f8fafc",
        borderRadius: "8px",
        fontSize: "12px",
      }}>
        <span>
          <strong>sttSupported:</strong>{" "}
          <code style={{
            padding: "1px 6px", borderRadius: "4px",
            background: sttSupported ? "#dcfce7" : "#fee2e2",
            color: sttSupported ? "#166534" : "#991b1b",
          }}>{String(sttSupported)}</code>
        </span>
        <span>
          <strong>ttsSupported:</strong>{" "}
          <code style={{
            padding: "1px 6px", borderRadius: "4px",
            background: ttsSupported ? "#dcfce7" : "#fee2e2",
            color: ttsSupported ? "#166534" : "#991b1b",
          }}>{String(ttsSupported)}</code>
        </span>
        <span><strong>lang:</strong> <code>{lang}</code></span>
      </div>

      {/* STT — Mic button + transcript */}
      <div style={{
        border: "1px solid #e2e8f0",
        borderRadius: "12px",
        overflow: "hidden",
        marginBottom: "14px",
      }}>
        <div style={{
          padding: "10px 14px",
          background: "#f1f5f9",
          fontSize: "12px",
          fontWeight: 600,
          color: "#475569",
          textTransform: "uppercase",
          letterSpacing: "0.05em",
        }}>STT — Speech to Text</div>
        <div style={{ padding: "14px" }}>
          <div style={{ display: "flex", gap: "10px", marginBottom: "12px" }}>
            <button
              type="button"
              onClick={isListening ? stopListening : startListening}
              disabled={!sttSupported}
              style={{
                padding: "10px 18px",
                borderRadius: "999px",
                border: "none",
                background: isListening ? "#dc2626" : (sttSupported ? "#2563eb" : "#cbd5e1"),
                color: "white",
                fontSize: "13px",
                fontWeight: 600,
                cursor: sttSupported ? "pointer" : "not-allowed",
                display: "inline-flex",
                alignItems: "center",
                gap: "8px",
              }}
            >
              {isListening ? (
                <>
                  <span style={{
                    width: 10, height: 10, borderRadius: "50%",
                    background: "white",
                    animation: "pulse 1s ease-in-out infinite",
                  }} />
                  Parar
                </>
              ) : "🎤 Falar"}
            </button>
            <div style={{ fontSize: "12px", color: "#64748b", alignSelf: "center" }}>
              {isListening ? "Capturing..." : isFinal ? "✓ Final transcript" : "Click to start"}
            </div>
          </div>

          <div style={{
            minHeight: "60px",
            padding: "10px 12px",
            background: "white",
            border: "1px solid #cbd5e1",
            borderRadius: "8px",
            fontSize: "14px",
            color: transcript ? "#0f172a" : "#94a3b8",
          }}>
            {transcript || "Transcript will appear here..."}
            {isListening && interimResults && (
              <span style={{ display: "inline-block", width: 6, height: 14,
                              background: "#2563eb", marginLeft: 2,
                              animation: "blink 1s steps(2) infinite",
                              verticalAlign: "middle" }} />
            )}
          </div>

          <div style={{ marginTop: "8px", fontSize: "11px", color: "#64748b" }}>
            <strong>isListening:</strong>{" "}
            <code>{String(isListening)}</code>{" · "}
            <strong>isFinal:</strong> <code>{String(isFinal)}</code>{" · "}
            <strong>interimResults:</strong> <code>{String(interimResults)}</code>
          </div>
        </div>
      </div>

      {/* TTS — Speak button */}
      <div style={{
        border: "1px solid #e2e8f0",
        borderRadius: "12px",
        overflow: "hidden",
      }}>
        <div style={{
          padding: "10px 14px",
          background: "#f1f5f9",
          fontSize: "12px",
          fontWeight: 600,
          color: "#475569",
          textTransform: "uppercase",
          letterSpacing: "0.05em",
        }}>TTS — Text to Speech (real audio in Storybook)</div>
        <div style={{ padding: "14px" }}>
          <div style={{ display: "flex", gap: "10px" }}>
            <button
              type="button"
              onClick={speak}
              disabled={!ttsSupported || !transcript.trim() || isSpeaking}
              style={{
                padding: "10px 18px",
                borderRadius: "8px",
                border: "none",
                background: isSpeaking ? "#94a3b8" : (ttsSupported && transcript ? "#22c55e" : "#cbd5e1"),
                color: "white",
                fontSize: "13px",
                fontWeight: 600,
                cursor: (ttsSupported && transcript && !isSpeaking) ? "pointer" : "not-allowed",
                display: "inline-flex",
                alignItems: "center",
                gap: "8px",
              }}
            >
              {isSpeaking ? <>🔊 Falando...</> : "🔊 Falar transcript"}
            </button>
            {isSpeaking && (
              <button
                type="button"
                onClick={stopSpeaking}
                style={{
                  padding: "10px 14px",
                  borderRadius: "8px",
                  border: "1px solid #cbd5e1",
                  background: "white",
                  color: "#475569",
                  fontSize: "13px",
                  cursor: "pointer",
                }}
              >Parar</button>
            )}
          </div>
          <div style={{ marginTop: "8px", fontSize: "11px", color: "#64748b" }}>
            <strong>isSpeaking:</strong> <code>{String(isSpeaking)}</code>
          </div>
        </div>
      </div>

      <style>{`
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }
        @keyframes blink { 50% { opacity: 0; } }
      `}</style>
    </div>
  );
}

const meta: Meta<typeof VoiceDemo> = {
  title: "Hooks/useTuringVoice",
  component: VoiceDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Web Speech API wrapper for voice-mode chat. STT side is simulated (no mic permission needed in Storybook — the demo fake-types a sample sentence character-by-character). TTS uses the real `speechSynthesis` API — click 'Falar transcript' to hear it. Toggle `forceUnsupported` to preview the disabled state for browsers that don't ship Web Speech.",
      },
    },
  },
  argTypes: {
    lang: {
      description: "BCP-47 locale tag (pt-BR / en-US / es-ES).",
      control: { type: "select" },
      options: ["pt-BR", "en-US", "es-ES"],
    },
    interimResults: {
      description: "Stream interim (non-final) results to the transcript as the user speaks.",
      control: { type: "boolean" },
    },
    forceUnsupported: {
      description: "Override feature-detect to render the unsupported-browser UI.",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof VoiceDemo>;

export const PtBRInterim: Story = {
  name: "pt-BR with interim results (live typing)",
  args: { lang: "pt-BR", interimResults: true, forceUnsupported: false },
};

export const PtBRFinalOnly: Story = {
  name: "pt-BR final-only (snappier, no flicker)",
  args: { lang: "pt-BR", interimResults: false, forceUnsupported: false },
};

export const EnUS: Story = {
  name: "en-US with interim results",
  args: { lang: "en-US", interimResults: true, forceUnsupported: false },
};

export const Unsupported: Story = {
  name: "Browser without Web Speech (mic + speaker disabled)",
  args: { lang: "pt-BR", interimResults: true, forceUnsupported: true },
};
