import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Minimal Web Speech API wrapper. Two surfaces:
 *
 * 1. **STT (Speech-to-Text)** via {@link SpeechRecognition}: caller calls
 *    {@link UseTuringVoiceReturn.startListening} → the hook drives the browser
 *    recognizer and exposes the running transcript via
 *    {@link UseTuringVoiceReturn.transcript} + {@code isListening} flag. Caller
 *    decides what to do on every transcript update (typically: pump into
 *    the chat composer's input field and auto-send on final).
 * 2. **TTS (Text-to-Speech)** via {@link SpeechSynthesis}: caller calls
 *    {@link UseTuringVoiceReturn.speak} with text → the hook queues an utterance
 *    on the browser's TTS engine. {@code isSpeaking} reflects whether the
 *    engine is currently outputting audio.
 *
 * Feature-detected: if the browser doesn't support SpeechRecognition or
 * SpeechSynthesis, the matching {@code *Supported} flag is false and the
 * caller can hide the corresponding UI button.
 *
 * Locale defaults to {@code pt-BR} — the AI chat-flow expects the Marina /
 * Lucas personas to converse in Brazilian Portuguese, so both STT and TTS
 * share that locale.
 *
 * @since 2026.2.7
 */
export interface UseTuringVoiceOptions {
  /** BCP-47 locale tag for both STT recognition and TTS playback. Defaults to "pt-BR". */
  readonly lang?: string;
  /**
   * When true, the recognizer surfaces interim (non-final) results in
   * {@link UseTuringVoiceReturn.transcript} as the user keeps speaking — useful
   * for showing the live typing effect. When false, transcript only
   * updates on final results (snappier final state, no flicker).
   * Defaults to true.
   */
  readonly interimResults?: boolean;
}

export interface UseTuringVoiceReturn {
  readonly sttSupported: boolean;
  readonly ttsSupported: boolean;
  readonly isListening: boolean;
  readonly isSpeaking: boolean;
  /** Most recent transcript text from STT. Resets to "" on each new listen. */
  readonly transcript: string;
  /**
   * Whether the most recent transcript update is the final result for the
   * current utterance (caller may auto-submit when this flips true).
   */
  readonly isFinal: boolean;
  /** Start STT capture. No-op when unsupported or already listening. */
  readonly startListening: () => void;
  /** Stop STT capture explicitly. Idempotent. */
  readonly stopListening: () => void;
  /**
   * Speak {@code text} via TTS. Cancels any in-flight utterance first so
   * the user always hears the latest message (relevant in the chat: if
   * the user sends a follow-up before the assistant's previous reply
   * finishes speaking, the new reply takes over).
   */
  readonly speak: (text: string) => void;
  /** Stop TTS playback immediately. */
  readonly stopSpeaking: () => void;
}

// Browser type declarations — the Web Speech API isn't part of the
// standard TS lib yet (it's a draft), and Chrome/Safari ship it under the
// `webkit` prefix. We declare just the bits we use.
interface SpeechRecognitionEventLike {
  readonly resultIndex: number;
  readonly results: ArrayLike<{
    readonly isFinal: boolean;
    readonly 0: { readonly transcript: string };
  }>;
}
interface SpeechRecognitionLike {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
}
type SpeechRecognitionCtor = new () => SpeechRecognitionLike;

function getSpeechRecognitionCtor(): SpeechRecognitionCtor | null {
  if (typeof globalThis === "undefined") return null;
  // Chrome / Edge ship under `webkitSpeechRecognition`; Firefox doesn't
  // ship a recognizer at all (as of 2026); Safari ships the unprefixed
  // identifier. Returning null disables the mic button silently.
  const w = globalThis as unknown as {
    SpeechRecognition?: SpeechRecognitionCtor;
    webkitSpeechRecognition?: SpeechRecognitionCtor;
  };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

export function useTuringVoice(options: UseTuringVoiceOptions = {}): UseTuringVoiceReturn {
  const { lang = "pt-BR", interimResults = true } = options;

  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const [sttSupported, setSttSupported] = useState(false);
  const [ttsSupported, setTtsSupported] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [transcript, setTranscript] = useState("");
  const [isFinal, setIsFinal] = useState(false);

  // Detect support once on mount. We build a fresh recognizer per
  // start/stop cycle to avoid stuck "no-speech" errors on Chrome that
  // cling to a stale recognizer instance after a network hiccup.
  useEffect(() => {
    setSttSupported(getSpeechRecognitionCtor() !== null);
    setTtsSupported(
      typeof globalThis !== "undefined" && "speechSynthesis" in globalThis,
    );
  }, []);

  const startListening = useCallback(() => {
    const Ctor = getSpeechRecognitionCtor();
    if (!Ctor || isListening) return;
    const recognition = new Ctor();
    recognition.lang = lang;
    recognition.continuous = false;
    recognition.interimResults = interimResults;
    setTranscript("");
    setIsFinal(false);
    recognition.onresult = (event) => {
      // Browsers stream chunks: indices < resultIndex are already final
      // and locked; the suffix starting at resultIndex is the in-flight
      // best-guess (may still revise). Concatenate the whole thing for
      // the visible transcript and flag final when the last chunk is.
      let text = "";
      let final = false;
      for (let i = 0; i < event.results.length; i++) {
        text += event.results[i][0].transcript;
        if (event.results[i].isFinal) final = true;
      }
      setTranscript(text.trim());
      setIsFinal(final);
    };
    recognition.onerror = (event) => {
      // "no-speech" + "aborted" are common when the user releases the
      // button without saying anything; not worth surfacing. Other
      // errors are logged at the console for the operator. Either way
      // we restore listening=false so the UI button re-enables.
      if (event.error !== "no-speech" && event.error !== "aborted") {
        // eslint-disable-next-line no-console
        console.warn("[useVoice] STT error:", event.error);
      }
      setIsListening(false);
    };
    recognition.onend = () => setIsListening(false);
    recognitionRef.current = recognition;
    try {
      recognition.start();
      setIsListening(true);
    } catch {
      // start() throws InvalidStateError when called twice in quick
      // succession — recover silently.
      setIsListening(false);
    }
  }, [isListening, lang, interimResults]);

  const stopListening = useCallback(() => {
    const recognition = recognitionRef.current;
    if (!recognition) return;
    try {
      recognition.stop();
    } catch {
      // ignore — already stopped
    }
    setIsListening(false);
  }, []);

  const speak = useCallback(
    (text: string) => {
      if (!ttsSupported || !text.trim()) return;
      const synth = globalThis.speechSynthesis;
      // Cancel any in-flight utterance so a new assistant message
      // interrupts the previous one cleanly (otherwise queued utterances
      // would play in sequence and "lag" the conversation).
      synth.cancel();
      const utter = new SpeechSynthesisUtterance(text);
      utter.lang = lang;
      utter.rate = 1.0;
      utter.pitch = 1.0;
      utter.onstart = () => setIsSpeaking(true);
      utter.onend = () => setIsSpeaking(false);
      utter.onerror = () => setIsSpeaking(false);
      synth.speak(utter);
    },
    [ttsSupported, lang],
  );

  const stopSpeaking = useCallback(() => {
    if (!ttsSupported) return;
    globalThis.speechSynthesis.cancel();
    setIsSpeaking(false);
  }, [ttsSupported]);

  // Cleanup on unmount: stop both engines so a route change doesn't
  // leave a recognizer holding the mic or a synth speaking into the void.
  useEffect(() => {
    return () => {
      try {
        recognitionRef.current?.abort();
      } catch {
        // ignore
      }
      if (typeof globalThis !== "undefined" && "speechSynthesis" in globalThis) {
        globalThis.speechSynthesis.cancel();
      }
    };
  }, []);

  return {
    sttSupported,
    ttsSupported,
    isListening,
    isSpeaking,
    transcript,
    isFinal,
    startListening,
    stopListening,
    speak,
    stopSpeaking,
  };
}
