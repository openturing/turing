import type { TuringClient } from "../client";
import { postAnswer, postAnswerFreshness, type AnswerFilters } from "../api";
import { createStore, type Store } from "../store";
import type {
  TurAnswerCitation,
  TurAnswerTrace,
  TurChatSource,
  TuringConfig,
} from "../types";

/**
 * T837 / §LXII.6 — the vanilla, framework-agnostic answer controller: the
 * observable equivalent of the React `useTuringAnswer` hook. It drives the
 * streaming `GET /sn/{site}/answer` endpoint (T833), accumulating the answer text
 * and its sources / citations / follow-ups / trace into an observable
 * {@link Store} a plain-JS or EDS surface can subscribe to.
 *
 * <p>Fail-open by construction: a mid-stream `error` event lands in
 * {@link AnswerControllerState.error} without throwing, and a superseded or
 * aborted request never clobbers a newer one.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

export type AnswerStatus = "idle" | "loading" | "success" | "error";

export interface AnswerControllerState {
  status: AnswerStatus;
  /** The accumulated answer text (grows as `token` events arrive). */
  answer: string;
  /** Source chips — arrive before the first token. */
  sources: TurChatSource[];
  /** Footnote citations (agree with the source ranks and the answer's `[n]`). */
  citations: TurAnswerCitation[];
  /** Suggested next questions. */
  followups: string[];
  /** Minimal run summary. */
  trace: TurAnswerTrace | null;
  error: string | null;
  /** Derived convenience mirror of `status === "loading"`. */
  isStreaming: boolean;
  /** T840 — ISO instant the answer completed (drift is measured from here). */
  answeredAt: string | null;
  /** T840 — cited source ids a `checkFreshness()` re-check flagged as drifted. */
  staleSources: string[];
}

/** Per-ask overrides: the response locale and the T838 steering filter set. */
export interface AnswerAskOptions {
  locale?: string;
  /** T838 — synthesize the answer within this active facet filter set. */
  filters?: AnswerFilters;
}

export interface AnswerController extends Store<AnswerControllerState> {
  /** Stream a grounded answer for {@code query} (optionally steered by filters/locale). */
  ask(query: string, options?: AnswerAskOptions): Promise<void>;
  /**
   * T840 — re-check whether the current answer's cited sources have drifted since
   * it was produced, populating {@link AnswerControllerState.staleSources}. No-op
   * until an answer with citations has completed.
   */
  checkFreshness(): Promise<void>;
  /** Abort the in-flight answer, leaving whatever has streamed so far. */
  stop(): void;
  /** Abort and clear back to the idle state. */
  reset(): void;
  /** Abort and release resources (the controller is unusable afterwards). */
  destroy(): void;
}

const INITIAL: AnswerControllerState = {
  status: "idle",
  answer: "",
  sources: [],
  citations: [],
  followups: [],
  trace: null,
  error: null,
  isStreaming: false,
  answeredAt: null,
  staleSources: [],
};

function isAbortError(err: unknown): boolean {
  if (typeof DOMException !== "undefined" && err instanceof DOMException && err.name === "AbortError") {
    return true;
  }
  return err instanceof Error && err.name === "AbortError";
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

export function createAnswerController(
  client: TuringClient,
  config: TuringConfig,
): AnswerController {
  const store = createStore<AnswerControllerState>({ ...INITIAL });

  // Closure mirrors of the React refs — the monotonic request id drops a late
  // response from a superseded ask; `aborted` short-circuits a stopped stream.
  let latestRequestId = 0;
  let aborted = false;
  let abortController: AbortController | null = null;
  // The last question asked — so checkFreshness can re-retrieve it (T840).
  let lastQuery = "";

  function isStale(requestId: number): boolean {
    return requestId !== latestRequestId || aborted;
  }

  async function ask(query: string, options?: AnswerAskOptions): Promise<void> {
    const trimmed = query?.trim();
    if (!trimmed || trimmed === "*") return;

    const requestId = ++latestRequestId;
    aborted = false;
    abortController?.abort();
    abortController = new AbortController();
    lastQuery = trimmed;

    store.setState({
      status: "loading",
      isStreaming: true,
      answer: "",
      sources: [],
      citations: [],
      followups: [],
      trace: null,
      error: null,
      answeredAt: null,
      staleSources: [],
    });

    try {
      const result = await postAnswer(client, config.site, trimmed, options?.locale ?? config.locale, {
        signal: abortController.signal,
        filters: options?.filters,
        onSources: (sources) => {
          if (!isStale(requestId)) store.setState({ sources });
        },
        onToken: (token) => {
          if (!isStale(requestId)) store.setState((prev) => ({ answer: prev.answer + token }));
        },
        onCitations: (citations) => {
          if (!isStale(requestId)) store.setState({ citations });
        },
        onFollowups: (followups) => {
          if (!isStale(requestId)) store.setState({ followups });
        },
        onTrace: (trace) => {
          if (!isStale(requestId)) store.setState({ trace });
        },
        onError: (message) => {
          if (!isStale(requestId)) store.setState({ error: message });
        },
      });
      if (isStale(requestId)) return;
      store.setState({
        status: result.error ? "error" : "success",
        isStreaming: false,
        error: result.error ?? null,
        answeredAt: result.error ? null : new Date().toISOString(),
      });
    } catch (err) {
      if (isStale(requestId) || isAbortError(err)) return;
      store.setState({ status: "error", isStreaming: false, error: errorMessage(err) });
    }
  }

  async function checkFreshness(): Promise<void> {
    const state = store.getState();
    if (!state.answeredAt || state.citations.length === 0 || !lastQuery) return;
    const sources = state.citations
      .filter((c) => c.sourceId)
      .map((c) => ({ sourceId: c.sourceId as string, citedText: c.citedText ?? undefined }));
    if (sources.length === 0) return;
    try {
      const verdicts = await postAnswerFreshness(client, config.site, lastQuery, state.answeredAt, sources);
      store.setState({
        staleSources: verdicts.filter((v) => v.status === "STALE").map((v) => v.sourceId),
      });
    } catch {
      // Fail-open: a freshness-check failure shows no badge, never an error.
    }
  }

  function stop(): void {
    aborted = true;
    latestRequestId++;
    abortController?.abort();
    abortController = null;
    if (store.getState().status === "loading") {
      store.setState({ status: "idle", isStreaming: false });
    }
  }

  function reset(): void {
    aborted = true;
    latestRequestId++;
    abortController?.abort();
    abortController = null;
    store.setState({ ...INITIAL });
  }

  function destroy(): void {
    reset();
  }

  return {
    getState: store.getState,
    setState: store.setState,
    subscribe: store.subscribe,
    ask,
    checkFreshness,
    stop,
    reset,
    destroy,
  };
}
