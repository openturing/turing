import { useCallback, useEffect, useRef, useState } from "react";
import {
  fetchCopilotAvailable,
  postCopilot,
  type TurCatalogCitation,
} from "../core/api";
import type { TurChatConversationMessage } from "../core/types";
import { useOptionalTuringContext } from "../core/use-turing-context";

/** Loading state of the most recent copilot turn. */
export type CopilotStatus = "idle" | "loading" | "error";

/**
 * One turn in a vectorless-copilot conversation. Assistant turns carry the
 * {@link TurCatalogCitation}[] the answer was grounded on, so a UI can render
 * the cited sources next to the reply.
 */
export interface CopilotMessage {
  readonly id: string;
  readonly role: "user" | "assistant";
  readonly content: string;
  readonly citations?: TurCatalogCitation[];
  readonly timestamp: number;
}

export interface UseTuringCopilotOptions {
  /**
   * SN site to talk to. Optional — falls back to the {@code <TuringProvider>}
   * site when omitted (the common embedded case).
   */
  readonly siteName?: string;
  /** Locale code passed to the copilot (drives the NL→facet parse language). */
  readonly locale?: string;
  /** Max turns kept in memory. Default {@code 50}. */
  readonly maxMessages?: number;
  /**
   * Probe {@code GET /copilot/available} on mount to populate {@link available}.
   * Default {@code true}.
   */
  readonly probeAvailability?: boolean;
}

export interface UseTuringCopilotReturn {
  /** Conversation so far (oldest first). */
  messages: CopilotMessage[];
  /** Citations of the most recent assistant answer (empty until one lands). */
  citations: TurCatalogCitation[];
  /** Human-readable summary of the last grounded query's filters, if any. */
  groundedQuerySummary: string | null;
  /** Total index hits behind the last answer. */
  totalHits: number;
  /** Loading state of the most recent send. */
  status: CopilotStatus;
  /** Whether a turn is in flight. */
  isLoading: boolean;
  /** Error message from the last failed send, or a copilot-unavailable reason. */
  error: string | null;
  /**
   * Whether the copilot can answer (a default LLM is configured). {@code null}
   * while unknown / not probed.
   */
  available: boolean | null;
  /**
   * Send a user message: appends it, POSTs the full history to
   * {@code /api/sn/{site}/copilot}, and appends the grounded, cited answer.
   */
  send: (content: string) => Promise<void>;
  /** Clear the conversation and reset error state. */
  reset: () => void;
}

const DEFAULT_MAX_MESSAGES = 50;

function newId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * T792 / §LIV.3 (Block BF) — hook for the <strong>Vectorless (Structured-Data)
 * RAG</strong> copilot ({@code POST /api/sn/{siteName}/copilot}). Unlike
 * {@link useTuringChat} (vector RAG, SSE-streamed) this binds the non-streaming
 * catalog-copilot path: the site's declared field schema is searched with no
 * embeddings and the LLM answers strictly from the cited rows, so every
 * assistant turn carries a {@link TurCatalogCitation}[].
 *
 * <p>Multi-turn: the full conversation is posted and the latest user turn drives
 * retrieval, mirroring {@code TurCatalogCopilotResult}. Fail-soft — an
 * unavailable copilot or a failed turn surfaces via {@link UseTuringCopilotReturn#error}
 * without throwing.
 *
 * @example
 * ```tsx
 * const { messages, citations, send, isLoading } = useTuringCopilot({ siteName: "model-catalog" });
 * send("cheapest embedding model with >= 1M context?");
 * ```
 *
 * @since 2026.3.4
 */
export function useTuringCopilot(
  options: UseTuringCopilotOptions = {},
): UseTuringCopilotReturn {
  const ctx = useOptionalTuringContext();
  const {
    siteName: siteOverride,
    locale,
    maxMessages = DEFAULT_MAX_MESSAGES,
    probeAvailability = true,
  } = options;
  const siteName = siteOverride ?? ctx?.config.site ?? "";

  const [messages, setMessages] = useState<CopilotMessage[]>([]);
  const [citations, setCitations] = useState<TurCatalogCitation[]>([]);
  const [groundedQuerySummary, setGroundedQuerySummary] = useState<string | null>(null);
  const [totalHits, setTotalHits] = useState<number>(0);
  const [status, setStatus] = useState<CopilotStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [available, setAvailable] = useState<boolean | null>(null);

  const messagesRef = useRef<CopilotMessage[]>(messages);
  messagesRef.current = messages;
  const requestRef = useRef(0);

  useEffect(() => {
    if (!probeAvailability || !siteName) return;
    let cancelled = false;
    fetchCopilotAvailable(siteName)
      .then((ok) => {
        if (!cancelled) setAvailable(ok);
      })
      .catch(() => {
        if (!cancelled) setAvailable(null);
      });
    return () => {
      cancelled = true;
    };
  }, [siteName, probeAvailability]);

  const send = useCallback(
    async (rawContent: string) => {
      const content = rawContent.trim();
      if (!content || !siteName) return;

      const requestId = ++requestRef.current;
      const userMsg: CopilotMessage = {
        id: newId(),
        role: "user",
        content,
        timestamp: Date.now(),
      };
      const all = [...messagesRef.current, userMsg];
      const trimmed = all.length > maxMessages ? all.slice(-maxMessages) : all;
      messagesRef.current = trimmed;
      setMessages(trimmed);
      setError(null);
      setStatus("loading");

      try {
        const wire: TurChatConversationMessage[] = trimmed.map((m) => ({
          role: m.role,
          content: m.content,
        }));
        const result = await postCopilot(siteName, wire, locale);
        if (requestId !== requestRef.current) return; // superseded

        const answerCitations = result.citations ?? [];
        setCitations(answerCitations);
        setGroundedQuerySummary(result.groundedQuerySummary ?? null);
        setTotalHits(result.totalHits ?? 0);

        if (!result.available) {
          setAvailable(false);
          setError(result.error ?? "The catalog copilot is not available.");
          setStatus("error");
          return;
        }
        setAvailable(true);

        // A degraded result (available but no answer text) still yields
        // citations — surface the matched rows with a soft note.
        const answerText =
          result.answer ??
          (answerCitations.length > 0
            ? ""
            : (result.error ?? "No answer could be generated."));

        const assistantMsg: CopilotMessage = {
          id: newId(),
          role: "assistant",
          content: answerText,
          citations: answerCitations,
          timestamp: Date.now(),
        };
        const next = [...messagesRef.current, assistantMsg];
        const nextTrimmed =
          next.length > maxMessages ? next.slice(-maxMessages) : next;
        messagesRef.current = nextTrimmed;
        setMessages(nextTrimmed);
        setStatus(result.answer == null && answerCitations.length === 0 ? "error" : "idle");
        if (result.error) setError(result.error);
      } catch (err) {
        if (requestId !== requestRef.current) return;
        setError(err instanceof Error ? err.message : "Copilot request failed");
        setStatus("error");
      }
    },
    [siteName, locale, maxMessages],
  );

  const reset = useCallback(() => {
    requestRef.current++;
    messagesRef.current = [];
    setMessages([]);
    setCitations([]);
    setGroundedQuerySummary(null);
    setTotalHits(0);
    setError(null);
    setStatus("idle");
  }, []);

  return {
    messages,
    citations,
    groundedQuerySummary,
    totalHits,
    status,
    isLoading: status === "loading",
    error,
    available,
    send,
    reset,
  };
}
