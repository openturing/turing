import { useCallback, useEffect, useRef, useState } from "react";
import { fetchSimilar, type TurSimilarMode, type TurSimilarResult } from "../core/api";
import { useTuringContext } from "../core/use-turing-context";

export interface UseTuringSimilarOptions {
  /**
   * Seed document id. When set, the hook auto-loads its neighbours on mount
   * and whenever the id changes. Omit to drive loading imperatively via
   * {@link UseTuringSimilarReturn.load}.
   */
  readonly documentId?: string;
  /** Max results (server caps at 50; default 10). */
  readonly rows?: number;
  /** Locale override; omit to use the provider's configured locale. */
  readonly locale?: string;
  /** Force {@code VECTOR} or {@code MLT}; omit for server auto-selection. */
  readonly mode?: TurSimilarMode;
  /** Disables the auto-load when {@code false}. Defaults to {@code true}. */
  readonly enabled?: boolean;
}

export interface UseTuringSimilarReturn {
  /** Related documents from the most recent load. */
  readonly results: TurSimilarResult[];
  /** Whether a lookup is in flight. */
  readonly loading: boolean;
  /** Error message from the last failed lookup, or {@code null}. */
  readonly error: string | null;
  /** Imperatively load neighbours for a given document id. */
  readonly load: (documentId: string) => Promise<void>;
  /** Clear the current results and error. */
  readonly reset: () => void;
}

/**
 * T400 — loads documents similar to a seed document (T384's
 * {@code GET /sn/{site}/search/similar}). Renders a "related" / "you may also
 * like" rail with a single hook.
 *
 * @example
 * ```tsx
 * const { results } = useTuringSimilar({ documentId: course.id, mode: "VECTOR", rows: 6 });
 * return results.map((r) => <a key={r.id} href={r.url}>{r.title}</a>);
 * ```
 *
 * @since 2026.3.4
 */
export function useTuringSimilar(
  options: UseTuringSimilarOptions = {},
): UseTuringSimilarReturn {
  const { config } = useTuringContext();
  const { documentId, rows, locale, mode, enabled = true } = options;

  const [results, setResults] = useState<TurSimilarResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Monotonic request id — a superseded response is dropped.
  const requestIdRef = useRef(0);

  const load = useCallback(
    async (id: string): Promise<void> => {
      const requestId = ++requestIdRef.current;
      setLoading(true);
      setError(null);
      try {
        const res = await fetchSimilar(config.site, {
          id,
          rows,
          locale: locale ?? config.locale,
          mode,
        });
        if (requestId !== requestIdRef.current) return;
        setResults(Array.isArray(res) ? res : []);
        setLoading(false);
      } catch (err) {
        if (requestId !== requestIdRef.current) return;
        setError(err instanceof Error ? err.message : "Similar lookup failed");
        setResults([]);
        setLoading(false);
      }
    },
    [config.site, config.locale, rows, locale, mode],
  );

  const reset = useCallback(() => {
    requestIdRef.current++;
    setResults([]);
    setError(null);
    setLoading(false);
  }, []);

  useEffect(() => {
    if (!enabled || !documentId) return;
    void load(documentId);
  }, [enabled, documentId, load]);

  return { results, loading, error, load, reset };
}
