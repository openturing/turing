import { useCallback, useRef, useState } from "react";
import { fetchAnswer, fetchAnswerFreshness, type AnswerFilters } from "../core/api";
import type {
  TurAnswerArtifact,
  TurAnswerCitation,
  TurAnswerTrace,
  TurChatSource,
  TurChatToolCall,
} from "../core/types";
import { useOptionalTuringContext } from "../core/use-turing-context";
import { isAbortError } from "./streaming-chat-core";

/**
 * T837 / §LXII.6 — headless hook for the answer-first surface. Consumes the T833
 * streaming `GET /api/sn/{siteName}/answer` endpoint and exposes the answer text
 * (streaming), its sources / footnote citations / follow-ups / trace, and status —
 * so an integrator gets the fused answer surface in one import. The admin search
 * page, the showcase and the public-site hero all consume this one implementation.
 *
 * <p>The site falls back to the enclosing `<TuringProvider>` config when
 * {@link UseTuringAnswerOptions.siteName} is omitted. Fail-open: a mid-stream
 * `error` event lands in {@link UseTuringAnswerReturn.error}; a superseded or
 * aborted `ask` never clobbers a newer one.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

export type TuringAnswerStatus = "idle" | "loading" | "success" | "error";

export interface UseTuringAnswerOptions {
  /** SN site to answer over. Falls back to the `<TuringProvider>` site. */
  readonly siteName?: string;
  /** Response/retrieval locale. Falls back to the provider locale. */
  readonly locale?: string;
}

/** Per-ask overrides: the response locale and the T838 steering filter set. */
export interface TuringAnswerAskOptions {
  locale?: string;
  /** T838 — synthesize the answer within this active facet filter set. */
  filters?: AnswerFilters;
}

export interface UseTuringAnswerReturn {
  answer: string;
  sources: TurChatSource[];
  citations: TurAnswerCitation[];
  followups: string[];
  /** T841 — structured answer artifacts (answer-as-app generative-UI specs). */
  artifacts: TurAnswerArtifact[];
  /** T843 — the live research-trace steps (tool-call start/end events). */
  traceSteps: TurChatToolCall[];
  trace: TurAnswerTrace | null;
  status: TuringAnswerStatus;
  error: string | null;
  isStreaming: boolean;
  /** T840 — ISO instant the answer completed (drift is measured from here). */
  answeredAt: string | null;
  /** T840 — cited source ids a `checkFreshness()` re-check flagged as drifted. */
  staleSources: string[];
  ask: (query: string, options?: TuringAnswerAskOptions) => Promise<void>;
  /** T840 — re-check the current answer's cited sources for drift. */
  checkFreshness: () => Promise<void>;
  stop: () => void;
  reset: () => void;
}

export function useTuringAnswer(options: UseTuringAnswerOptions = {}): UseTuringAnswerReturn {
  const ctx = useOptionalTuringContext();
  const siteName = options.siteName ?? ctx?.config.site ?? "";
  const defaultLocale = options.locale ?? ctx?.config.locale;

  const [answer, setAnswer] = useState("");
  const [sources, setSources] = useState<TurChatSource[]>([]);
  const [citations, setCitations] = useState<TurAnswerCitation[]>([]);
  const [followups, setFollowups] = useState<string[]>([]);
  const [artifacts, setArtifacts] = useState<TurAnswerArtifact[]>([]);
  const [traceSteps, setTraceSteps] = useState<TurChatToolCall[]>([]);
  const [trace, setTrace] = useState<TurAnswerTrace | null>(null);
  const [status, setStatus] = useState<TuringAnswerStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [answeredAt, setAnsweredAt] = useState<string | null>(null);
  const [staleSources, setStaleSources] = useState<string[]>([]);

  // Superseded-request guard trio (the hook mirror of the vanilla controller).
  const requestRef = useRef(0);
  const abortedRef = useRef(false);
  const abortControllerRef = useRef<AbortController | null>(null);
  // The last question + its citations — so checkFreshness can re-retrieve (T840).
  const lastQueryRef = useRef("");
  const citationsRef = useRef<TurAnswerCitation[]>([]);
  citationsRef.current = citations;
  const answeredAtRef = useRef<string | null>(null);
  answeredAtRef.current = answeredAt;

  const ask = useCallback(
    async (query: string, askOptions?: TuringAnswerAskOptions) => {
      const trimmed = query?.trim();
      if (!trimmed || trimmed === "*" || !siteName) return;

      const requestId = ++requestRef.current;
      abortedRef.current = false;
      abortControllerRef.current?.abort();
      const controller = new AbortController();
      abortControllerRef.current = controller;
      lastQueryRef.current = trimmed;

      setStatus("loading");
      setAnswer("");
      setSources([]);
      setCitations([]);
      setFollowups([]);
      setArtifacts([]);
      setTraceSteps([]);
      setTrace(null);
      setError(null);
      setAnsweredAt(null);
      setStaleSources([]);

      const stale = () => requestId !== requestRef.current || abortedRef.current;
      try {
        const result = await fetchAnswer(siteName, trimmed, askOptions?.locale ?? defaultLocale, {
          signal: controller.signal,
          filters: askOptions?.filters,
          onSources: (s) => { if (!stale()) setSources(s); },
          onToken: (t) => { if (!stale()) setAnswer((prev) => prev + t); },
          onCitations: (c) => { if (!stale()) setCitations(c); },
          onFollowups: (f) => { if (!stale()) setFollowups(f); },
          onArtifact: (a) => { if (!stale()) setArtifacts((prev) => [...prev, a]); },
          onTraceStep: (s) => { if (!stale()) setTraceSteps((prev) => [...prev, s]); },
          onTrace: (tr) => { if (!stale()) setTrace(tr); },
          onError: (m) => { if (!stale()) setError(m); },
        });
        if (stale()) return;
        setStatus(result.error ? "error" : "success");
        if (result.error) setError(result.error);
        else setAnsweredAt(new Date().toISOString());
      } catch (err) {
        if (stale() || isAbortError(err)) return;
        setError(err instanceof Error ? err.message : String(err));
        setStatus("error");
      }
    },
    [siteName, defaultLocale],
  );

  const checkFreshness = useCallback(async () => {
    const at = answeredAtRef.current;
    const cites = citationsRef.current;
    if (!at || cites.length === 0 || !siteName || !lastQueryRef.current) return;
    const refs = cites
      .filter((c) => c.sourceId)
      .map((c) => ({ sourceId: c.sourceId as string, citedText: c.citedText ?? undefined }));
    if (refs.length === 0) return;
    try {
      const verdicts = await fetchAnswerFreshness(siteName, lastQueryRef.current, at, refs);
      setStaleSources(verdicts.filter((v) => v.status === "STALE").map((v) => v.sourceId));
    } catch {
      // Fail-open: a freshness-check failure shows no badge, never an error.
    }
  }, [siteName]);

  const stop = useCallback(() => {
    abortedRef.current = true;
    requestRef.current++;
    abortControllerRef.current?.abort();
    setStatus((s) => (s === "loading" ? "idle" : s));
  }, []);

  const reset = useCallback(() => {
    abortedRef.current = true;
    requestRef.current++;
    abortControllerRef.current?.abort();
    setAnswer("");
    setSources([]);
    setCitations([]);
    setFollowups([]);
    setArtifacts([]);
    setTraceSteps([]);
    setTrace(null);
    setError(null);
    setStatus("idle");
    setAnsweredAt(null);
    setStaleSources([]);
  }, []);

  return {
    answer,
    sources,
    citations,
    followups,
    artifacts,
    traceSteps,
    trace,
    status,
    error,
    isStreaming: status === "loading",
    answeredAt,
    staleSources,
    ask,
    checkFreshness,
    stop,
    reset,
  };
}
