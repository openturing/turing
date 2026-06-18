import type { TuringClient } from "../client";
import {
  fetchSimilar,
  type FetchSimilarParams,
  type TurSimilarMode,
  type TurSimilarResult,
} from "../api";
import { createStore, type Store } from "../store";

/**
 * Vanilla "similar documents" controller (T400) — the framework-agnostic
 * equivalent of the React SDK's `useTuringSimilar`. Wraps {@link fetchSimilar}
 * (T384's `GET /sn/{site}/search/similar`) over an observable {@link Store} so a
 * catalog can render a "related courses" / "you may also like" rail with a
 * single subscription.
 *
 * @example
 * ```js
 * const similar = createSimilarController(client, "courses");
 * similar.subscribe((s) => render(s.results));
 * await similar.load("course-123", { mode: "VECTOR", rows: 6 });
 * ```
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

export type SimilarStatus = "idle" | "loading" | "success" | "error";

export interface SimilarControllerState {
  status: SimilarStatus;
  /** Id of the seed document for the current/last load, or null. */
  seedId: string | null;
  results: TurSimilarResult[];
  error: string | null;
}

export interface SimilarControllerOptions {
  /** Default result count for {@link SimilarController#load}. */
  readonly rows?: number;
  /** Default locale override. */
  readonly locale?: string;
  /** Default strategy; omit for server auto-selection. */
  readonly mode?: TurSimilarMode;
}

export interface SimilarController extends Store<SimilarControllerState> {
  /** Load documents similar to {@code documentId}, merging controller defaults. */
  load(documentId: string, overrides?: Partial<Omit<FetchSimilarParams, "id">>): Promise<void>;
  /** Reset to the idle/empty state and drop any in-flight result. */
  clear(): void;
}

export function createSimilarController(
  client: TuringClient,
  site: string,
  options: SimilarControllerOptions = {},
): SimilarController {
  const store = createStore<SimilarControllerState>({
    status: "idle",
    seedId: null,
    results: [],
    error: null,
  });

  // Monotonic request id — a late response from a superseded load is dropped.
  let latestRequestId = 0;

  async function load(
    documentId: string,
    overrides?: Partial<Omit<FetchSimilarParams, "id">>,
  ): Promise<void> {
    const requestId = ++latestRequestId;
    store.setState({ status: "loading", seedId: documentId, error: null });
    try {
      const results = await fetchSimilar(client, site, {
        id: documentId,
        rows: overrides?.rows ?? options.rows,
        locale: overrides?.locale ?? options.locale,
        mode: overrides?.mode ?? options.mode,
      });
      if (requestId !== latestRequestId) return;
      store.setState({ results, status: "success" });
    } catch (err) {
      if (requestId !== latestRequestId) return;
      store.setState({
        error: err instanceof Error ? err.message : "Similar lookup failed",
        status: "error",
      });
    }
  }

  function clear(): void {
    latestRequestId++;
    store.setState({ status: "idle", seedId: null, results: [], error: null });
  }

  return {
    getState: store.getState,
    setState: store.setState,
    subscribe: store.subscribe,
    load,
    clear,
  };
}
