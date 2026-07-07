import { useCallback, useEffect, useRef, useState } from "react";

import { postVoiceSession } from "../core/api";
import type { PostVoiceSessionOptions, TurVoiceSession } from "../core/api";

/**
 * T148 / §X.6.b — full-duplex real-time voice for an AI agent (OpenAI Realtime
 * API). Unlike {@link useTuringVoice} (a browser Web Speech wrapper: separate
 * STT then TTS, one utterance at a time), this hook holds a live bidirectional
 * audio session: the visitor speaks, the model listens with server-side VAD,
 * and the agent's synthesized speech streams back token-by-token — barge-in and
 * all.
 *
 * <p>Transport: the hook calls Turing's {@code POST …/voice/session} to mint a
 * short-lived <b>ephemeral</b> token (the account API key never reaches the
 * browser — see T147), then opens a WebSocket straight to the vendor's realtime
 * endpoint using the browser subprotocol-auth scheme. Mic audio is captured,
 * downsampled to 24 kHz PCM16 and appended to the input buffer; the model's
 * audio deltas are scheduled back through Web Audio.
 *
 * <p>Feature-detected: when the browser lacks {@code getUserMedia},
 * {@code AudioContext} or {@code WebSocket}, {@link UseTuringRealtimeVoiceReturn.supported}
 * is false and {@link UseTuringRealtimeVoiceReturn.start} is a no-op so the
 * caller can hide the voice button.
 *
 * @since 2026.3.4
 */
export type RealtimeVoiceStatus =
  | "idle"
  | "connecting"
  | "connected"
  | "error"
  | "closed";

export interface RealtimeToolCall {
  /** Vendor call id (echo back with the result if you implement tool execution). */
  readonly callId: string;
  /** The tool/function name the model asked to call. */
  readonly name: string;
  /** Raw JSON arguments string emitted by the model. */
  readonly argumentsJson: string;
}

export interface UseTuringRealtimeVoiceOptions {
  /** The AI agent to converse with. */
  readonly agentId: string;
  /** Optional explicit LLM instance (defaults to the agent's default). */
  readonly llmInstanceId?: string;
  /** Optional realtime model override (defaults to the capability/server default). */
  readonly model?: string;
  /** Optional synthesized voice name override. */
  readonly voice?: string;
  /** BCP-47 locale hint for transcription (e.g. "pt-BR"). */
  readonly locale?: string;
  /** Connect immediately on mount. Defaults to false (call {@link start} yourself). */
  readonly autoStart?: boolean;
  /** Called when the model requests a tool/function call. */
  readonly onToolCall?: (call: RealtimeToolCall) => void;
  /** Called on any fatal session error. */
  readonly onError?: (error: Error) => void;
  /**
   * T150 — called once per <b>completed</b> visitor utterance (not per delta).
   * The whole transcribed segment, ready to translate for a spectator.
   */
  readonly onUserUtterance?: (text: string) => void;
  /** T150 — called once per completed assistant turn (the full spoken text). */
  readonly onAssistantUtterance?: (text: string) => void;
}

export interface UseTuringRealtimeVoiceReturn {
  /** False when the browser can't do realtime audio — hide the voice UI. */
  readonly supported: boolean;
  readonly status: RealtimeVoiceStatus;
  readonly isConnecting: boolean;
  readonly isConnected: boolean;
  /** True while the mic is capturing (and not muted). */
  readonly isListening: boolean;
  /** True while the agent's audio is playing back. */
  readonly isAssistantSpeaking: boolean;
  readonly isMuted: boolean;
  /** Running transcript of what the visitor said this session. */
  readonly userTranscript: string;
  /** Running transcript of what the agent said this session. */
  readonly assistantTranscript: string;
  readonly error: Error | null;
  /** Mint a session, open the socket, start capturing. Idempotent. */
  readonly start: () => Promise<void>;
  /** Tear the session down. Idempotent. */
  readonly stop: () => void;
  /** Stop sending mic audio without closing the session. */
  readonly mute: () => void;
  /** Resume sending mic audio. */
  readonly unmute: () => void;
}

/** Realtime audio is 24 kHz mono PCM16 in both directions. */
const SAMPLE_RATE = 24000;

function isRealtimeSupported(): boolean {
  if (typeof globalThis === "undefined") return false;
  const g = globalThis as unknown as {
    WebSocket?: unknown;
    AudioContext?: unknown;
    webkitAudioContext?: unknown;
    navigator?: { mediaDevices?: { getUserMedia?: unknown } };
  };
  const hasAudioCtx = Boolean(g.AudioContext ?? g.webkitAudioContext);
  const hasMic = Boolean(g.navigator?.mediaDevices?.getUserMedia);
  return Boolean(g.WebSocket) && hasAudioCtx && hasMic;
}

function getAudioContextCtor(): typeof AudioContext | null {
  const g = globalThis as unknown as {
    AudioContext?: typeof AudioContext;
    webkitAudioContext?: typeof AudioContext;
  };
  return g.AudioContext ?? g.webkitAudioContext ?? null;
}

/** Linear-interpolation downsample of a Float32 frame to {@link SAMPLE_RATE}. */
function downsampleTo24k(input: Float32Array, inputRate: number): Float32Array {
  if (inputRate === SAMPLE_RATE) return input;
  const ratio = inputRate / SAMPLE_RATE;
  const outLength = Math.round(input.length / ratio);
  const out = new Float32Array(outLength);
  for (let i = 0; i < outLength; i++) {
    const idx = i * ratio;
    const low = Math.floor(idx);
    const high = Math.min(low + 1, input.length - 1);
    const frac = idx - low;
    out[i] = input[low] * (1 - frac) + input[high] * frac;
  }
  return out;
}

/** Float32 [-1,1] → little-endian PCM16 → base64 (for input_audio_buffer.append). */
function float32ToPcm16Base64(float32: Float32Array): string {
  const buffer = new ArrayBuffer(float32.length * 2);
  const view = new DataView(buffer);
  for (let i = 0; i < float32.length; i++) {
    const s = Math.max(-1, Math.min(1, float32[i]));
    view.setInt16(i * 2, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  let binary = "";
  const bytes = new Uint8Array(buffer);
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return globalThis.btoa(binary);
}

/** base64 PCM16 (from response.audio.delta) → Float32 [-1,1] for playback. */
function pcm16Base64ToFloat32(b64: string): Float32Array {
  const binary = globalThis.atob(b64);
  const len = binary.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) bytes[i] = binary.charCodeAt(i);
  const view = new DataView(bytes.buffer);
  const samples = len / 2;
  const out = new Float32Array(samples);
  for (let i = 0; i < samples; i++) {
    out[i] = view.getInt16(i * 2, true) / 0x8000;
  }
  return out;
}

export function useTuringRealtimeVoice(
  options: UseTuringRealtimeVoiceOptions,
): UseTuringRealtimeVoiceReturn {
  const {
    agentId,
    llmInstanceId,
    model,
    voice,
    locale,
    autoStart,
    onToolCall,
    onError,
    onUserUtterance,
    onAssistantUtterance,
  } = options;

  const [supported] = useState(isRealtimeSupported);
  const [status, setStatus] = useState<RealtimeVoiceStatus>("idle");
  const [isMuted, setIsMuted] = useState(false);
  const [isAssistantSpeaking, setIsAssistantSpeaking] = useState(false);
  const [userTranscript, setUserTranscript] = useState("");
  const [assistantTranscript, setAssistantTranscript] = useState("");
  const [error, setError] = useState<Error | null>(null);

  // Transport + audio handles. Refs (not state) because they're mutable
  // imperative resources the render path never reads.
  const wsRef = useRef<WebSocket | null>(null);
  const micStreamRef = useRef<MediaStream | null>(null);
  const inputCtxRef = useRef<AudioContext | null>(null);
  const outputCtxRef = useRef<AudioContext | null>(null);
  const processorRef = useRef<ScriptProcessorNode | null>(null);
  const sourceNodeRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const mutedRef = useRef(false);
  const playCursorRef = useRef(0);
  const pendingSourcesRef = useRef(0);
  const startingRef = useRef(false);
  // Accumulates the current assistant turn's transcript so a completed-utterance
  // callback (T150) can fire the full text on the turn's `.done` boundary.
  const assistantTurnRef = useRef("");

  // Keep callbacks in refs so the long-lived socket handlers always see the
  // latest closures without re-subscribing.
  const onToolCallRef = useRef(onToolCall);
  const onErrorRef = useRef(onError);
  const onUserUtteranceRef = useRef(onUserUtterance);
  const onAssistantUtteranceRef = useRef(onAssistantUtterance);
  useEffect(() => {
    onToolCallRef.current = onToolCall;
    onErrorRef.current = onError;
    onUserUtteranceRef.current = onUserUtterance;
    onAssistantUtteranceRef.current = onAssistantUtterance;
  }, [onToolCall, onError, onUserUtterance, onAssistantUtterance]);

  const teardown = useCallback((nextStatus: RealtimeVoiceStatus) => {
    try {
      processorRef.current?.disconnect();
    } catch {
      /* ignore */
    }
    try {
      sourceNodeRef.current?.disconnect();
    } catch {
      /* ignore */
    }
    processorRef.current = null;
    sourceNodeRef.current = null;
    micStreamRef.current?.getTracks().forEach((t) => t.stop());
    micStreamRef.current = null;
    if (inputCtxRef.current && inputCtxRef.current.state !== "closed") {
      void inputCtxRef.current.close();
    }
    inputCtxRef.current = null;
    if (outputCtxRef.current && outputCtxRef.current.state !== "closed") {
      void outputCtxRef.current.close();
    }
    outputCtxRef.current = null;
    const ws = wsRef.current;
    wsRef.current = null;
    if (ws && ws.readyState <= WebSocket.OPEN) {
      try {
        ws.close();
      } catch {
        /* ignore */
      }
    }
    playCursorRef.current = 0;
    pendingSourcesRef.current = 0;
    setIsAssistantSpeaking(false);
    setStatus(nextStatus);
  }, []);

  const fail = useCallback(
    (err: Error) => {
      setError(err);
      onErrorRef.current?.(err);
      teardown("error");
    },
    [teardown],
  );

  const enqueuePlayback = useCallback((float32: Float32Array) => {
    const ctx = outputCtxRef.current;
    if (!ctx || float32.length === 0) return;
    const buffer = ctx.createBuffer(1, float32.length, SAMPLE_RATE);
    // .set() (vs copyToChannel) sidesteps the TS5.7 TypedArray buffer-generic
    // mismatch and copies into the AudioBuffer's own channel data.
    buffer.getChannelData(0).set(float32);
    const node = ctx.createBufferSource();
    node.buffer = buffer;
    node.connect(ctx.destination);
    const now = ctx.currentTime;
    const startAt = Math.max(now, playCursorRef.current);
    node.start(startAt);
    playCursorRef.current = startAt + buffer.duration;
    pendingSourcesRef.current += 1;
    setIsAssistantSpeaking(true);
    node.onended = () => {
      pendingSourcesRef.current = Math.max(0, pendingSourcesRef.current - 1);
      if (pendingSourcesRef.current === 0) {
        setIsAssistantSpeaking(false);
      }
    };
  }, []);

  const handleServerEvent = useCallback(
    (evt: Record<string, unknown>) => {
      const type = typeof evt.type === "string" ? evt.type : "";
      switch (type) {
        case "response.audio.delta":
        case "response.output_audio.delta": {
          const delta = typeof evt.delta === "string" ? evt.delta : "";
          if (delta) enqueuePlayback(pcm16Base64ToFloat32(delta));
          break;
        }
        case "response.audio_transcript.delta":
        case "response.output_audio_transcript.delta": {
          const delta = typeof evt.delta === "string" ? evt.delta : "";
          if (delta) {
            assistantTurnRef.current += delta;
            setAssistantTranscript((prev) => prev + delta);
          }
          break;
        }
        case "response.audio_transcript.done":
        case "response.output_audio_transcript.done": {
          // Prefer the event's full transcript; fall back to the accumulated turn.
          const full = typeof evt.transcript === "string" && evt.transcript
            ? evt.transcript
            : assistantTurnRef.current;
          assistantTurnRef.current = "";
          if (full) onAssistantUtteranceRef.current?.(full);
          break;
        }
        case "conversation.item.input_audio_transcription.completed": {
          const text = typeof evt.transcript === "string" ? evt.transcript : "";
          if (text) {
            setUserTranscript((prev) => (prev ? `${prev} ${text}` : text));
            onUserUtteranceRef.current?.(text);
          }
          break;
        }
        case "response.function_call_arguments.done": {
          const call: RealtimeToolCall = {
            callId: typeof evt.call_id === "string" ? evt.call_id : "",
            name: typeof evt.name === "string" ? evt.name : "",
            argumentsJson: typeof evt.arguments === "string" ? evt.arguments : "{}",
          };
          onToolCallRef.current?.(call);
          break;
        }
        case "error": {
          const errObj = evt.error as { message?: string } | undefined;
          fail(new Error(errObj?.message ?? "Realtime voice error"));
          break;
        }
        default:
          break;
      }
    },
    [enqueuePlayback, fail],
  );

  const startMicCapture = useCallback(async () => {
    const AudioCtor = getAudioContextCtor();
    if (!AudioCtor) throw new Error("AudioContext unavailable");
    const stream = await globalThis.navigator.mediaDevices.getUserMedia({ audio: true });
    micStreamRef.current = stream;
    const ctx = new AudioCtor();
    inputCtxRef.current = ctx;
    const source = ctx.createMediaStreamSource(stream);
    sourceNodeRef.current = source;
    // ScriptProcessorNode is deprecated but is the most broadly supported way
    // to pull raw PCM frames without shipping a separate AudioWorklet module.
    const processor = ctx.createScriptProcessor(4096, 1, 1);
    processorRef.current = processor;
    processor.onaudioprocess = (event) => {
      const ws = wsRef.current;
      if (!ws || ws.readyState !== WebSocket.OPEN || mutedRef.current) return;
      const input = event.inputBuffer.getChannelData(0);
      const downsampled = downsampleTo24k(input, ctx.sampleRate);
      ws.send(
        JSON.stringify({
          type: "input_audio_buffer.append",
          audio: float32ToPcm16Base64(downsampled),
        }),
      );
    };
    source.connect(processor);
    processor.connect(ctx.destination);
  }, []);

  const start = useCallback(async () => {
    if (!supported || startingRef.current) return;
    if (wsRef.current) return;
    startingRef.current = true;
    setError(null);
    setUserTranscript("");
    setAssistantTranscript("");
    setStatus("connecting");
    try {
      const sessionOptions: PostVoiceSessionOptions = { llmInstanceId, model, voice, locale };
      const session: TurVoiceSession = await postVoiceSession(agentId, sessionOptions);

      const AudioCtor = getAudioContextCtor();
      if (!AudioCtor) throw new Error("AudioContext unavailable");
      outputCtxRef.current = new AudioCtor({ sampleRate: SAMPLE_RATE });

      // Browser subprotocol auth: a browser WebSocket can't set an
      // Authorization header, so the ephemeral token rides in the
      // Sec-WebSocket-Protocol list (the vendor's documented scheme).
      const ws = new WebSocket(session.wsUrl, [
        "realtime",
        `openai-insecure-api-key.${session.clientSecret}`,
        "openai-beta.realtime-v1",
      ]);
      wsRef.current = ws;

      ws.onopen = () => {
        setStatus("connected");
        // Enable input transcription + server-side turn detection so the
        // visitor can just talk; the model decides when a turn ends.
        ws.send(
          JSON.stringify({
            type: "session.update",
            session: {
              input_audio_transcription: { model: "whisper-1" },
              turn_detection: { type: "server_vad" },
            },
          }),
        );
        startMicCapture().catch((e) =>
          fail(e instanceof Error ? e : new Error(String(e))),
        );
      };
      ws.onmessage = (event) => {
        try {
          handleServerEvent(JSON.parse(event.data as string) as Record<string, unknown>);
        } catch {
          /* ignore malformed frames */
        }
      };
      ws.onerror = () => fail(new Error("Realtime voice transport error"));
      ws.onclose = () => {
        if (wsRef.current === ws) {
          teardown("closed");
        }
      };
    } catch (e) {
      fail(e instanceof Error ? e : new Error(String(e)));
    } finally {
      startingRef.current = false;
    }
  }, [
    supported,
    agentId,
    llmInstanceId,
    model,
    voice,
    locale,
    startMicCapture,
    handleServerEvent,
    fail,
    teardown,
  ]);

  const stop = useCallback(() => {
    if (!wsRef.current && status === "idle") return;
    teardown("idle");
  }, [status, teardown]);

  const mute = useCallback(() => {
    mutedRef.current = true;
    setIsMuted(true);
  }, []);

  const unmute = useCallback(() => {
    mutedRef.current = false;
    setIsMuted(false);
  }, []);

  // autoStart + unmount cleanup.
  useEffect(() => {
    if (autoStart) void start();
    return () => teardown("idle");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const isConnected = status === "connected";
  return {
    supported,
    status,
    isConnecting: status === "connecting",
    isConnected,
    isListening: isConnected && !isMuted,
    isAssistantSpeaking,
    isMuted,
    userTranscript,
    assistantTranscript,
    error,
    start,
    stop,
    mute,
    unmute,
  };
}
